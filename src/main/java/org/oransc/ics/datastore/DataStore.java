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

import org.oransc.ics.configuration.ApplicationConfig;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DataStore {

    public Flux<String> listObjects(String prefix);

    public Mono<byte[]> readObject(String fileName);

    public Mono<byte[]> writeObject(String fileName, byte[] fileData);

    public Mono<Boolean> deleteObject(String name);

    public Mono<String> createDataStore();

    public Mono<String> deleteAllData();

    public Mono<String> deleteBucket();

    static DataStore create(ApplicationConfig config, String location) {
        return config.isS3Enabled() ? new S3ObjectStore(config, location) : new FileStore(config, location);
    }

}
