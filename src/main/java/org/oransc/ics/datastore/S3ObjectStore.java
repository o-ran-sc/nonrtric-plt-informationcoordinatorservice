/*-
 * ========================LICENSE_START=================================
 * O-RAN-SC
 * %%
 * Copyright (C) 2022 Nordix Foundation
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================LICENSE_END===================================
 */

package org.oransc.ics.datastore;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import org.oransc.ics.configuration.ApplicationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

class S3ObjectStore implements DataStore {
    private static final Logger logger = LoggerFactory.getLogger(S3ObjectStore.class);
    private final ApplicationConfig applicationConfig;

    private static S3AsyncClient s3AsynchClient;
    private final String location;

    public S3ObjectStore(ApplicationConfig applicationConfig, String location) {
        this.applicationConfig = applicationConfig;
        this.location = location;

        getS3AsynchClient(applicationConfig);
    }

    private static synchronized S3AsyncClient getS3AsynchClient(ApplicationConfig applicationConfig) {
        if (applicationConfig.isS3Enabled() && s3AsynchClient == null) {
            s3AsynchClient = getS3AsyncClientBuilder(applicationConfig).build();
        }
        return s3AsynchClient;
    }

    private static S3AsyncClientBuilder getS3AsyncClientBuilder(ApplicationConfig applicationConfig) {
        URI uri = URI.create(applicationConfig.getS3EndpointOverride());
        return S3AsyncClient.builder() //
            .region(Region.US_EAST_1) //
            .endpointOverride(uri) //
            .credentialsProvider(StaticCredentialsProvider.create( //
                AwsBasicCredentials.create(applicationConfig.getS3AccessKeyId(), //
                    applicationConfig.getS3SecretAccessKey())));
    }

    @Override
    public Flux<String> listObjects(String prefix) {
        return listObjectsInBucket(bucket(), location + "/" + prefix).map(S3Object::key) //
            .map(this::externalName);
    }

    @Override
    public Mono<Boolean> deleteObject(String name) {
        DeleteObjectRequest request = DeleteObjectRequest.builder() //
            .bucket(bucket()) //
            .key(key(name)) //
            .build();

        CompletableFuture<DeleteObjectResponse> future = s3AsynchClient.deleteObject(request);

        return Mono.fromFuture(future).map(resp -> true);
    }

    @Override
    public Mono<byte[]> readObject(String fileName) {
        return getDataFromS3Object(bucket(), fileName);
    }

    @Override
    public Mono<byte[]> writeObject(String fileName, byte[] fileData) {

        PutObjectRequest request = PutObjectRequest.builder() //
            .bucket(bucket()) //
            .key(key(fileName)) //
            .build();

        AsyncRequestBody body = AsyncRequestBody.fromBytes(fileData);

        CompletableFuture<PutObjectResponse> future = s3AsynchClient.putObject(request, body);

        return Mono.fromFuture(future) //
            .map(putObjectResponse -> fileData) //
            .doOnError(t -> logger.error("Failed to store file in S3 {}", t.getMessage()));
    }

    @Override
    public Mono<String> createDataStore() {
        return createS3Bucket(bucket());
    }

    private Mono<String> createS3Bucket(String s3Bucket) {

        CreateBucketRequest request = CreateBucketRequest.builder() //
            .bucket(s3Bucket) //
            .build();

        CompletableFuture<CreateBucketResponse> future = s3AsynchClient.createBucket(request);

        return Mono.fromFuture(future) //
            .map(f -> s3Bucket) //
            .doOnError(t -> logger.debug("Could not create S3 bucket: {}", t.getMessage()))
            .onErrorResume(t -> Mono.just(s3Bucket));
    }

    @Override
    public Mono<String> deleteAllData() {
        return listObjects("") //
            .flatMap(key -> deleteObject(key)) //
            .collectList() //
            .map(resp -> "OK")
            .doOnError(t -> logger.warn("Could not delete bucket: {}, reason: {}", bucket(), t.getMessage()))
            .onErrorResume(t -> Mono.just("NOK"));
    }

    @Override
    public Mono<String> deleteBucket() {
        return deleteBucketFromS3Storage()
            .doOnError(t -> logger.warn("Could not delete: {}, reason: {}", bucket(), t.getMessage()))
            .map(x -> bucket()).onErrorResume(t -> Mono.just(bucket()));
    }

    private Mono<DeleteBucketResponse> deleteBucketFromS3Storage() {
        DeleteBucketRequest request = DeleteBucketRequest.builder() //
            .bucket(bucket()) //
            .build();

        CompletableFuture<DeleteBucketResponse> future = s3AsynchClient.deleteBucket(request);

        return Mono.fromFuture(future);
    }

    private String bucket() {
        return applicationConfig.getS3Bucket();
    }

    private Mono<ListObjectsResponse> listObjectsRequest(String bucket, String prefix,
        ListObjectsResponse prevResponse) {
        ListObjectsRequest.Builder builder = ListObjectsRequest.builder() //
            .bucket(bucket) //
            .maxKeys(1000) //
            .prefix(prefix);

        if (prevResponse != null) {
            if (Boolean.TRUE.equals(prevResponse.isTruncated())) {
                builder.marker(prevResponse.nextMarker());
            } else {
                return Mono.empty();
            }
        }

        ListObjectsRequest listObjectsRequest = builder.build();
        CompletableFuture<ListObjectsResponse> future = s3AsynchClient.listObjects(listObjectsRequest);
        return Mono.fromFuture(future);
    }

    private Flux<S3Object> listObjectsInBucket(String bucket, String prefix) {

        return listObjectsRequest(bucket, prefix, null) //
            .expand(response -> listObjectsRequest(bucket, prefix, response)).map(ListObjectsResponse::contents) //
            .doOnNext(f -> logger.debug("Found objects in {}: {}", bucket, f.size())) //
            .doOnError(t -> logger.warn("Error fromlist objects: {}", t.getMessage())) //
            .flatMap(Flux::fromIterable) //
            .doOnNext(obj -> logger.debug("Found object: {}", obj.key()));
    }

    private Mono<byte[]> getDataFromS3Object(String bucket, String fileName) {

        GetObjectRequest request = GetObjectRequest.builder() //
            .bucket(bucket) //
            .key(key(fileName)) //
            .build();

        CompletableFuture<ResponseBytes<GetObjectResponse>> future =
            s3AsynchClient.getObject(request, AsyncResponseTransformer.toBytes());

        return Mono.fromFuture(future) //
            .map(b -> b.asByteArray()) //
            .doOnError(t -> logger.error("Failed to get file from S3, key:{}, bucket: {}, {}", key(fileName), bucket,
                t.getMessage())) //
            .doOnEach(n -> logger.debug("Read file from S3: {} {}", bucket, key(fileName))) //
            .onErrorResume(t -> Mono.empty());
    }

    private String key(String fileName) {
        return location + "/" + fileName;
    }

    private String externalName(String internalName) {
        return internalName.substring(key("").length());
    }

}
