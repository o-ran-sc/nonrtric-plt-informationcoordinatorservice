/*-
 * ========================LICENSE_START=================================
 * O-RAN-SC
 * %%
 * Copyright (C) 2020 Nordix Foundation
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

package org.oransc.ics.controllers.a1e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.oransc.ics.configuration.ApplicationConfig;
import org.oransc.ics.controllers.ErrorResponse;
import org.oransc.ics.controllers.VoidResponse;
import org.oransc.ics.controllers.authorization.AuthorizationCheck;
import org.oransc.ics.controllers.authorization.SubscriptionAuthRequest.Input.AccessType;
import org.oransc.ics.controllers.r1producer.ProducerCallbacks;
import org.oransc.ics.exceptions.ServiceException;
import org.oransc.ics.repository.InfoJob;
import org.oransc.ics.repository.InfoJobs;
import org.oransc.ics.repository.InfoProducers;
import org.oransc.ics.repository.InfoType;
import org.oransc.ics.repository.InfoTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@SuppressWarnings("java:S3457") // No need to call "toString()" method as formatting and string ..
@RestController("A1-EI")
@Tag(name = A1eConsts.CONSUMER_API_NAME, description = A1eConsts.CONSUMER_API_DESCRIPTION)
@RequestMapping(path = A1eConsts.API_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
public class A1eController {

    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    ApplicationConfig applicationConfig;

    @Autowired
    private InfoJobs infoJobs;

    @Autowired
    private InfoTypes infoTypes;

    @Autowired
    private InfoProducers infoProducers;

    @Autowired
    ProducerCallbacks producerCallbacks;

    @Autowired
    private AuthorizationCheck authorization;

    private static Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    @GetMapping(path = "/eitypes", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "EI type identifiers", description = "")
    @ApiResponses(
        value = { //
            @ApiResponse(
                responseCode = "200",
                description = "EI type identifiers", //
                content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))), //
        })
    public ResponseEntity<Object> getEiTypeIdentifiers( //
    ) {
        List<String> result = new ArrayList<>();
        for (InfoType eiType : this.infoTypes.getAllInfoTypes()) {
            result.add(eiType.getId());
        }

        return new ResponseEntity<>(gson.toJson(result), HttpStatus.OK);
    }

    @GetMapping(path = "/eitypes/{eiTypeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Individual EI type", description = "")
    @ApiResponses(
        value = { //
            @ApiResponse(
                responseCode = "200",
                description = "EI type", //
                content = @Content(schema = @Schema(implementation = A1eEiTypeInfo.class))), //
            @ApiResponse(
                responseCode = "404",
                description = "Enrichment Information type is not found", //
                content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
        })
    public ResponseEntity<Object> getEiType( //
        @PathVariable("eiTypeId") String eiTypeId) {
        try {
            this.infoTypes.getType(eiTypeId); // Make sure that the type exists
            A1eEiTypeInfo info = toEiTypeInfo();
            return new ResponseEntity<>(gson.toJson(info), HttpStatus.OK);
        } catch (Exception e) {
            return ErrorResponse.create(e, HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(path = "/eijobs", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "EI job identifiers", description = "query for EI job identifiers")
    @ApiResponses(
        value = { //
            @ApiResponse(
                responseCode = "200",
                description = "EI job identifiers", //
                content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))),
            @ApiResponse(
                responseCode = "404",
                description = "Enrichment Information type is not found", //
                content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
        })
    public ResponseEntity<Object> getEiJobIds( //
        @Parameter(
            name = A1eConsts.EI_TYPE_ID_PARAM,
            required = false, //
            description = A1eConsts.EI_TYPE_ID_PARAM_DESCRIPTION) //
        @RequestParam(name = A1eConsts.EI_TYPE_ID_PARAM, required = false) String eiTypeId,
        @Parameter(
            name = A1eConsts.OWNER_PARAM,
            required = false, //
            description = A1eConsts.OWNER_PARAM_DESCRIPTION) //
        @RequestParam(name = A1eConsts.OWNER_PARAM, required = false) String owner) {
        try {
            List<String> result = new ArrayList<>();
            if (owner != null) {
                for (InfoJob job : this.infoJobs.getJobsForOwner(owner)) {
                    if (eiTypeId == null || job.getType().getId().equals(eiTypeId)) {
                        result.add(job.getId());
                    }
                }
            } else if (eiTypeId != null) {
                this.infoJobs.getJobsForType(eiTypeId).forEach(job -> result.add(job.getId()));
            } else {
                this.infoJobs.getJobs().forEach(job -> result.add(job.getId()));
            }
            return new ResponseEntity<>(gson.toJson(result), HttpStatus.OK);
        } catch (Exception e) {
            return ErrorResponse.create(e, HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(path = "/eijobs/{eiJobId}", produces = MediaType.APPLICATION_JSON_VALUE) //
    @Operation(summary = "Individual EI job", description = "") //
    @ApiResponses(
        value = { //
            @ApiResponse(
                responseCode = "200",
                description = "EI job", //
                content = @Content(schema = @Schema(implementation = A1eEiJobInfo.class))), //
            @ApiResponse(
                responseCode = "404",
                description = "Enrichment Information job is not found", //
                content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
        })
    public Mono<ResponseEntity<Object>> getIndividualEiJob( //
        @PathVariable("eiJobId") String eiJobId, //
        @RequestHeader Map<String, String> headers) {

        return this.infoJobs.getJobMono(eiJobId)
            .flatMap(job -> authorization.authorizeDataJob(headers, job, AccessType.READ)) //
            .map(job -> new ResponseEntity<Object>(gson.toJson(toEiJobInfo(job)), HttpStatus.OK))
            .onErrorResume(ErrorResponse::createMono);
    }

    @GetMapping(path = "/eijobs/{eiJobId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "EI job status", description = "")
    @ApiResponses(
        value = { //
            @ApiResponse(
                responseCode = "200",
                description = "EI job status", //
                content = @Content(schema = @Schema(implementation = A1eEiJobStatus.class))), //
            @ApiResponse(
                responseCode = "404",
                description = "Enrichment Information job is not found", //
                content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
        })
    public ResponseEntity<Object> getEiJobStatus( //
        @PathVariable("eiJobId") String eiJobId) {
        try {
            InfoJob job = this.infoJobs.getJob(eiJobId);
            return new ResponseEntity<>(gson.toJson(toEiJobStatus(job)), HttpStatus.OK);
        } catch (Exception e) {
            return ErrorResponse.create(e, HttpStatus.NOT_FOUND);
        }
    }

    private A1eEiJobStatus toEiJobStatus(InfoJob job) {
        return this.infoProducers.isJobEnabled(job) ? new A1eEiJobStatus(A1eEiJobStatus.EiJobStatusValues.ENABLED)
            : new A1eEiJobStatus(A1eEiJobStatus.EiJobStatusValues.DISABLED);

    }

    @DeleteMapping(path = "/eijobs/{eiJobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Individual EI job", description = "")
    @ApiResponses(
        value = { //
            @ApiResponse(
                responseCode = "200",
                description = "Not used", //
                content = @Content(schema = @Schema(implementation = VoidResponse.class))),
            @ApiResponse(
                responseCode = "204",
                description = "Job deleted", //
                content = @Content(schema = @Schema(implementation = VoidResponse.class))), //
            @ApiResponse(
                responseCode = "404",
                description = "Enrichment Information job is not found", //
                content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))) //
        })
    public Mono<ResponseEntity<Object>> deleteIndividualEiJob( //
        @PathVariable("eiJobId") String eiJobId, //
        @RequestHeader Map<String, String> headers) {

        return this.infoJobs.getJobMono(eiJobId)
            .flatMap(job -> authorization.authorizeDataJob(headers, job, AccessType.WRITE)) //
            .doOnNext(job -> this.infoJobs.remove(job, this.infoProducers))
            .map(x -> new ResponseEntity<>(HttpStatus.NO_CONTENT)).onErrorResume(ErrorResponse::createMono);
    }

    @PutMapping(
        path = "/eijobs/{eiJobId}", //
        produces = MediaType.APPLICATION_JSON_VALUE, //
        consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Individual EI job", description = A1eConsts.PUT_INDIVIDUAL_JOB_DESCRIPTION)
    @ApiResponses(
        value = { //
            @ApiResponse(
                responseCode = "201",
                description = "Job created", //
                content = @Content(schema = @Schema(implementation = VoidResponse.class))), //
            @ApiResponse(
                responseCode = "200",
                description = "Job updated", //
                content = @Content(schema = @Schema(implementation = VoidResponse.class))), //
            @ApiResponse(
                responseCode = "404",
                description = "Enrichment Information type is not found", //
                content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))),
            @ApiResponse(
                responseCode = "400",
                description = "Input validation failed", //
                content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class))), //
            @ApiResponse(
                responseCode = "409",
                description = "Cannot modify job type", //
                content = @Content(schema = @Schema(implementation = ErrorResponse.ErrorInfo.class)))})
    public Mono<ResponseEntity<Object>> putIndividualEiJob( //
        @PathVariable("eiJobId") String eiJobId, //
        @RequestBody A1eEiJobInfo eiJobObject, //
        @RequestHeader Map<String, String> headers) throws ServiceException {

        final boolean isNewJob = this.infoJobs.get(eiJobId) == null;
        try {
            InfoType eiType = this.infoTypes.getCompatibleType(eiJobObject.eiTypeId);

            return authorization.authorizeDataJob(headers, eiType, eiJobObject.jobDefinition, AccessType.WRITE) //
                .flatMap(x -> validatePutEiJob(eiJobId, eiType, eiJobObject)) //
                .flatMap(job -> startEiJob(job, eiType)) //
                .doOnNext(newEiJob -> this.infoJobs.put(newEiJob)) //
                .map(newEiJob -> new ResponseEntity<>(isNewJob ? HttpStatus.CREATED : HttpStatus.OK)) //
                .onErrorResume(
                    throwable -> Mono.just(ErrorResponse.create(throwable, HttpStatus.INTERNAL_SERVER_ERROR)));
        } catch (Exception e) {
            return Mono.just(ErrorResponse.create(e, HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private Mono<InfoJob> startEiJob(InfoJob newEiJob, InfoType type) {
        return this.producerCallbacks.startInfoSubscriptionJob(newEiJob, type, infoProducers) //
            .doOnNext(noOfAcceptingProducers -> this.logger.debug(
                "Started EI job {}, number of activated producers: {}", newEiJob.getId(), noOfAcceptingProducers)) //
            .map(noOfAcceptingProducers -> newEiJob);
    }

    private Mono<InfoJob> validatePutEiJob(String eiJobId, InfoType eiType, A1eEiJobInfo eiJobInfo) {
        try {
            validateJsonObjectAgainstSchema(eiType.getJobDataSchema(), eiJobInfo.jobDefinition);
            validateUri(eiJobInfo.jobResultUri);
            validateUri(eiJobInfo.statusNotificationUri);

            InfoJob existingEiJob = this.infoJobs.get(eiJobId);
            if (existingEiJob != null) {
                InfoType.TypeId typeId = InfoType.TypeId.ofString(eiJobInfo.eiTypeId);
                if (!existingEiJob.getType().getId().contains(typeId.getName())) {
                    throw new ServiceException("Not allowed to change type for existing EI job", HttpStatus.CONFLICT);
                }
            }
            return Mono.just(toEiJob(eiJobInfo, eiJobId, eiType));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private void validateUri(String url) throws URISyntaxException, ServiceException {
        if (url != null && !url.isEmpty()) {
            URI uri = new URI(url);
            if (!uri.isAbsolute()) {
                throw new ServiceException("URI: " + url + " is not absolute", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private void validateJsonObjectAgainstSchema(Object schemaObj, Object object) throws ServiceException {
        try {
            ObjectMapper mapper = new ObjectMapper();

            String schemaAsString = mapper.writeValueAsString(schemaObj);
            JSONObject schemaJSON = new JSONObject(schemaAsString);
            var schema = org.everit.json.schema.loader.SchemaLoader.load(schemaJSON);

            String objectAsString = mapper.writeValueAsString(object);
            JSONObject json = new JSONObject(objectAsString);
            schema.validate(json);
        } catch (Exception e) {
            throw new ServiceException("Json validation failure " + e.toString(), HttpStatus.BAD_REQUEST);
        }
    }

    private InfoJob toEiJob(A1eEiJobInfo info, String id, InfoType type) {
        return InfoJob.builder() //
            .id(id) //
            .type(type) //
            .owner(info.owner) //
            .jobData(info.jobDefinition) //
            .targetUrl(info.jobResultUri) //
            .jobStatusUrl(info.statusNotificationUri == null ? "" : info.statusNotificationUri) //
            .build();
    }

    private A1eEiTypeInfo toEiTypeInfo() {
        return new A1eEiTypeInfo();
    }

    private A1eEiJobInfo toEiJobInfo(InfoJob s) {
        return new A1eEiJobInfo(s.getType().getId(), s.getJobData(), s.getOwner(), s.getTargetUrl(),
            s.getJobStatusUrl());
    }
}
