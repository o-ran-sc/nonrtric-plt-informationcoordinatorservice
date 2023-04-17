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

package org.oransc.ics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.oransc.ics.clients.AsyncRestClient;
import org.oransc.ics.clients.AsyncRestClientFactory;
import org.oransc.ics.clients.SecurityContext;
import org.oransc.ics.configuration.ApplicationConfig;
import org.oransc.ics.configuration.WebClientConfig;
import org.oransc.ics.configuration.WebClientConfig.HttpProxyConfig;
import org.oransc.ics.controller.A1eCallbacksSimulatorController;
import org.oransc.ics.controller.ConsumerSimulatorController;
import org.oransc.ics.controller.OpenPolicyAgentSimulatorController;
import org.oransc.ics.controller.ProducerSimulatorController;
import org.oransc.ics.controllers.a1e.A1eConsts;
import org.oransc.ics.controllers.a1e.A1eEiJobInfo;
import org.oransc.ics.controllers.a1e.A1eEiJobStatus;
import org.oransc.ics.controllers.a1e.A1eEiTypeInfo;
import org.oransc.ics.controllers.authorization.SubscriptionAuthRequest;
import org.oransc.ics.controllers.authorization.SubscriptionAuthRequest.Input.AccessType;
import org.oransc.ics.controllers.r1consumer.ConsumerConsts;
import org.oransc.ics.controllers.r1consumer.ConsumerInfoTypeInfo;
import org.oransc.ics.controllers.r1consumer.ConsumerJobInfo;
import org.oransc.ics.controllers.r1consumer.ConsumerJobStatus;
import org.oransc.ics.controllers.r1consumer.ConsumerTypeRegistrationInfo;
import org.oransc.ics.controllers.r1consumer.ConsumerTypeSubscriptionInfo;
import org.oransc.ics.controllers.r1producer.ProducerCallbacks;
import org.oransc.ics.controllers.r1producer.ProducerConsts;
import org.oransc.ics.controllers.r1producer.ProducerInfoTypeInfo;
import org.oransc.ics.controllers.r1producer.ProducerJobInfo;
import org.oransc.ics.controllers.r1producer.ProducerRegistrationInfo;
import org.oransc.ics.controllers.r1producer.ProducerStatusInfo;
import org.oransc.ics.datastore.DataStore;
import org.oransc.ics.exceptions.ServiceException;
import org.oransc.ics.repository.InfoJob;
import org.oransc.ics.repository.InfoJobs;
import org.oransc.ics.repository.InfoProducer;
import org.oransc.ics.repository.InfoProducers;
import org.oransc.ics.repository.InfoType;
import org.oransc.ics.repository.InfoTypeSubscriptions;
import org.oransc.ics.repository.InfoTypes;
import org.oransc.ics.tasks.ProducerSupervision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = { //
        "server.ssl.key-store=./config/keystore.jks", //
        "app.webclient.trust-store=./config/truststore.jks", //
        "app.webclient.trust-store-used=true", //
        "app.vardata-directory=/tmp/ics", //
        "app.s3.bucket=" // If this is set, S3 will be used to store data.
    })
class ApplicationTest {
    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final String TYPE_ID = "typeId_1.9.9";
    private final String PRODUCER_ID = "producerId";
    private final String EI_JOB_PROPERTY = "\"property1\"";
    private final String JOB_ID = "jobId";

    @Autowired
    ApplicationContext context;

    @Autowired
    InfoJobs infoJobs;

    @Autowired
    InfoTypes infoTypes;

    @Autowired
    InfoProducers infoProducers;

    @Autowired
    ApplicationConfig applicationConfig;

    @Autowired
    ProducerSimulatorController producerSimulator;

    @Autowired
    ConsumerSimulatorController consumerSimulator;

    @Autowired
    A1eCallbacksSimulatorController a1eCallbacksSimulator;

    @Autowired
    ProducerSupervision producerSupervision;

    @Autowired
    ProducerCallbacks producerCallbacks;

    @Autowired
    InfoTypeSubscriptions infoTypeSubscriptions;

    @Autowired
    SecurityContext securityContext;

    @Autowired
    OpenPolicyAgentSimulatorController openPolicyAgentSimulatorController;

    private static Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    /**
     * Overrides the BeanFactory.
     */
    @TestConfiguration
    static class TestBeanFactory {
        @Bean
        public ServletWebServerFactory servletContainer() {
            return new TomcatServletWebServerFactory();
        }
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void reset() {
        this.infoJobs.clear();
        this.infoTypes.clear();
        this.infoProducers.clear();
        this.infoTypeSubscriptions.clear();
        this.producerSimulator.getTestResults().reset();
        this.consumerSimulator.getTestResults().reset();
        this.a1eCallbacksSimulator.getTestResults().reset();
        this.securityContext.setAuthTokenFilePath(null);
        this.applicationConfig.setAuthAgentUrl("");
        this.openPolicyAgentSimulatorController.getTestResults().reset();
        this.applicationConfig.setAuthAgentUrl(baseUrl() + OpenPolicyAgentSimulatorController.SUBSCRIPTION_AUTH_URL);
    }

    @AfterEach
    void check() {
        assertThat(this.producerSimulator.getTestResults().errorFound).isFalse();
    }

    @Test
    void generateApiDoc() throws FileNotFoundException {
        String url = "/v3/api-docs";
        ResponseEntity<String> resp = restClient().getForEntity(url).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        JSONObject jsonObj = new JSONObject(resp.getBody());
        assertThat(jsonObj.remove("servers")).isNotNull();

        String indented = jsonObj.toString(4);
        try (PrintStream out = new PrintStream(new FileOutputStream("api/ics-api.json"))) {
            out.print(indented);
        }
    }

    @Test
    void a1eGetEiTypes() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, "test");
        String url = A1eConsts.API_ROOT + "/eitypes";
        String rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo("[\"test\"]");
    }

    @Test
    void testTrustValidation() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, "test");
        String url = A1eConsts.API_ROOT + "/eitypes";
        String rsp = restClient(true).get(url).block();
        assertThat(rsp).isEqualTo("[\"test\"]");
    }

    @Test
    void consumerGetInfoTypes() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, "test");
        String url = ConsumerConsts.API_ROOT + "/info-types";
        String rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo("[\"test\"]");
    }

    @Test
    void consumerDeleteJobsForOneOwner() throws Exception {
        putInfoProducerWithOneType("producer1", TYPE_ID);
        putInfoJob(TYPE_ID, "jobId1");
        putInfoJob(TYPE_ID, "jobId2");
        putEiJob(TYPE_ID, "jobId3", "otherOwner");
        assertThat(this.infoJobs.size()).isEqualTo(3);
        String url = ConsumerConsts.API_ROOT + "/info-jobs?owner=owner";
        restClient().delete(url).block();
        assertThat(this.infoJobs.size()).isEqualTo(1);

        assertThat(this.producerSimulator.getTestResults().jobsStarted).hasSize(3);

        await().untilAsserted(() -> assertThat(this.producerSimulator.getTestResults().jobsStopped).hasSize(2));
    }

    @Test
    void a1eGetEiTypesEmpty() throws Exception {
        String url = A1eConsts.API_ROOT + "/eitypes";
        String rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo("[]");
    }

    @Test
    void consumerGetEiTypesEmpty() throws Exception {
        String url = ConsumerConsts.API_ROOT + "/info-types";
        String rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo("[]");
    }

    @Test
    void a1eGetEiType() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, "test");
        String url = A1eConsts.API_ROOT + "/eitypes/test";
        String rsp = restClient().get(url).block();
        A1eEiTypeInfo info = gson.fromJson(rsp, A1eEiTypeInfo.class);
        assertThat(info).isNotNull();
    }

    @Test
    void consumerGetEiType() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, "test");
        String url = ConsumerConsts.API_ROOT + "/info-types/test";
        String rsp = restClient().get(url).block();
        ConsumerInfoTypeInfo info = gson.fromJson(rsp, ConsumerInfoTypeInfo.class);
        assertThat(info).isNotNull();
        assertThat(info.jobDataSchema).isNotNull();
        assertThat(info.state).isEqualTo(ConsumerInfoTypeInfo.ConsumerTypeStatusValues.ENABLED);
        assertThat(info.noOfProducers).isEqualTo(1);
    }

    @Test
    void a1eGetEiTypeNotFound() throws Exception {
        String url = A1eConsts.API_ROOT + "/eitypes/junk";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND, "Information type not found: junk");
    }

    @Test
    void consumerGetEiTypeNotFound() throws Exception {
        String url = ConsumerConsts.API_ROOT + "/info-types/junk";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND, "Information type not found: junk");
    }

    @Test
    void a1eGetEiJobsIds() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, "jobId");
        final String JOB_ID_JSON = "[\"jobId\"]";
        String url = A1eConsts.API_ROOT + "/eijobs?infoTypeId=" + TYPE_ID;
        String rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo(JOB_ID_JSON);

        url = A1eConsts.API_ROOT + "/eijobs?owner=owner";
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo(JOB_ID_JSON);

        url = A1eConsts.API_ROOT + "/eijobs?owner=JUNK";
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo("[]");

        url = A1eConsts.API_ROOT + "/eijobs";
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo(JOB_ID_JSON);

        url = A1eConsts.API_ROOT + "/eijobs?eiTypeId=" + TYPE_ID + "&&owner=owner";
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo(JOB_ID_JSON);

        url = A1eConsts.API_ROOT + "/eijobs?eiTypeId=JUNK";
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo("[]");
    }

    @Test
    void consumerGetInformationJobsIds() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, "jobId");
        final String JOB_ID_JSON = "[\"jobId\"]";
        String url = ConsumerConsts.API_ROOT + "/info-jobs?infoTypeId=" + TYPE_ID;
        String rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo(JOB_ID_JSON);

        url = ConsumerConsts.API_ROOT + "/info-jobs?owner=owner";
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo(JOB_ID_JSON);

        url = ConsumerConsts.API_ROOT + "/info-jobs?owner=JUNK";
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo("[]");

        url = ConsumerConsts.API_ROOT + "/info-jobs";
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo(JOB_ID_JSON);

        url = ConsumerConsts.API_ROOT + "/info-jobs?infoTypeId=" + TYPE_ID + "&&owner=owner";
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo(JOB_ID_JSON);

        url = ConsumerConsts.API_ROOT + "/info-jobs?infoTypeId=JUNK";
        rsp = restClient().get(url).block();
        assertThat(rsp).isEqualTo("[]");
    }

    @Test
    void a1eGetEiJob() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, "jobId");
        String url = A1eConsts.API_ROOT + "/eijobs/jobId";
        String rsp = restClient().get(url).block();
        A1eEiJobInfo info = gson.fromJson(rsp, A1eEiJobInfo.class);
        assertThat(info.owner).isEqualTo("owner");
        assertThat(info.eiTypeId).isEqualTo(TYPE_ID);
    }

    @Test
    void consumerGetEiJob() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, "jobId");
        String url = ConsumerConsts.API_ROOT + "/info-jobs/jobId";
        String rsp = restClient().get(url).block();
        ConsumerJobInfo info = gson.fromJson(rsp, ConsumerJobInfo.class);
        assertThat(info.owner).isEqualTo("owner");
        assertThat(info.infoTypeId).isEqualTo(TYPE_ID);
    }

    @Test
    void a1eGetEiJobNotFound() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        String url = A1eConsts.API_ROOT + "/eijobs/junk";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND, "Could not find Information job: junk");
    }

    @Test
    void consumerGetInfoJobNotFound() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        String url = ConsumerConsts.API_ROOT + "/info-jobs/junk";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND, "Could not find Information job: junk");
    }

    @Test
    void a1eGetEiJobStatus() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, "jobId");

        verifyJobStatus("jobId", "ENABLED");
    }

    @Test
    void consumerGetInfoJobStatus() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, "jobId");

        String url = ConsumerConsts.API_ROOT + "/info-jobs/jobId/status";
        String rsp = restClient().get(url).block();
        assertThat(rsp) //
            .contains("ENABLED") //
            .contains(PRODUCER_ID);

        ConsumerJobStatus status = gson.fromJson(rsp, ConsumerJobStatus.class);
        assertThat(status.producers).contains(PRODUCER_ID);
    }

    @Test
    void a1eDeleteEiJob() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, "jobId");
        assertThat(this.infoJobs.size()).isEqualTo(1);
        String url = A1eConsts.API_ROOT + "/eijobs/jobId";
        restClient().delete(url).block();
        assertThat(this.infoJobs.size()).isZero();

        ProducerSimulatorController.TestResults simulatorResults = this.producerSimulator.getTestResults();
        await().untilAsserted(() -> assertThat(simulatorResults.jobsStopped).hasSize(1));
        assertThat(simulatorResults.jobsStopped.get(0)).isEqualTo("jobId");

    }

    @Test
    void consumerDeleteInfoJob() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, "jobId");
        assertThat(this.infoJobs.size()).isEqualTo(1);
        String url = ConsumerConsts.API_ROOT + "/info-jobs/jobId";
        restClient().delete(url).block();
        assertThat(this.infoJobs.size()).isZero();

        ProducerSimulatorController.TestResults simulatorResults = this.producerSimulator.getTestResults();
        await().untilAsserted(() -> assertThat(simulatorResults.jobsStopped).hasSize(1));
        assertThat(simulatorResults.jobsStopped.get(0)).isEqualTo("jobId");

        testErrorCode(restClient().delete(url), HttpStatus.NOT_FOUND, "Could not find Information job: jobId");
    }

    @Test
    void a1eDeleteEiJobNotFound() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        String url = A1eConsts.API_ROOT + "/eijobs/junk";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND, "Could not find Information job: junk");
    }

    @Test
    void consumerDeleteEiJobNotFound() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        String url = ConsumerConsts.API_ROOT + "/info-jobs/junk";
        testErrorCode(restClient().get(url), HttpStatus.NOT_FOUND, "Could not find Information job: junk");
    }

    @Test
    void a1ePutEiJob() throws Exception {

        // Test that one producer accepting a job is enough
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoProducerWithOneTypeRejecting("simulateProducerError", TYPE_ID);

        String url = A1eConsts.API_ROOT + "/eijobs/jobId";
        String body = gson.toJson(eiJobInfo());
        ResponseEntity<String> resp = restClient().putForEntity(url, body).block();
        assertThat(this.infoJobs.size()).isEqualTo(1);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ProducerSimulatorController.TestResults simulatorResults = this.producerSimulator.getTestResults();
        await().untilAsserted(() -> assertThat(simulatorResults.jobsStarted).hasSize(1));
        ProducerJobInfo request = simulatorResults.jobsStarted.get(0);
        assertThat(request.id).isEqualTo("jobId");

        // One retry --> two calls
        await().untilAsserted(() -> assertThat(simulatorResults.noOfRejectedCreate).isEqualTo(2));
        assertThat(simulatorResults.noOfRejectedCreate).isEqualTo(2);

        resp = restClient().putForEntity(url, body).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        InfoJob job = this.infoJobs.getJob("jobId");
        assertThat(job.getOwner()).isEqualTo("owner");

        verifyJobStatus(JOB_ID, "ENABLED");
    }

    @Test
    void a1ePutEiJobVersionHandling() throws Exception {
        final String REG_TYPE_ID1 = "type_1.5.0"; // Compatible
        final String REG_TYPE_ID2 = "type_1.6.0"; // Compatible
        final String REG_TYPE_ID3 = "type_2.0.0";
        final String REG_TYPE_ID4 = "type_1.0.0";
        final String REG_TYPE_ID5 = "xxxx_1.3.0"; // Other type

        final String PUT_TYPE_ID = "type_1.1.0";
        // Test that one producer accepting a job is enough
        putInfoProducerWithOneType(REG_TYPE_ID1, REG_TYPE_ID1);
        putInfoProducerWithOneType(REG_TYPE_ID2, REG_TYPE_ID2);
        putInfoProducerWithOneType(REG_TYPE_ID3, REG_TYPE_ID3);
        putInfoProducerWithOneType(REG_TYPE_ID4, REG_TYPE_ID4);
        putInfoProducerWithOneType(REG_TYPE_ID5, REG_TYPE_ID5);

        String url = A1eConsts.API_ROOT + "/eijobs/" + JOB_ID;
        String body = gson.toJson(eiJobInfo(PUT_TYPE_ID, JOB_ID));
        ResponseEntity<String> resp = restClient().putForEntity(url, body).block();
        assertThat(this.infoJobs.size()).isEqualTo(1);
        assertThat(this.infoJobs.getJobs().iterator().next().getType().getId()).isEqualTo(REG_TYPE_ID1);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        resp = restClient().putForEntity(url, body).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        restClient().delete(url).block();

    }

    @Test
    void testA1EPutJob_unknownType() throws Exception {
        final String REG_TYPE_ID1 = "type_1.5.0"; // Compatible
        putInfoProducerWithOneType(REG_TYPE_ID1, REG_TYPE_ID1);

        String body = gson.toJson(eiJobInfo("junkTypeId", JOB_ID));

        String url = A1eConsts.API_ROOT + "/eijobs/jobId";
        testErrorCode(restClient().put(url, body), HttpStatus.NOT_FOUND, "not found");

        url = A1eConsts.API_ROOT + "/eijobs/" + JOB_ID;
        final String PUT_TYPE_ERROR_ID = "type_1.1";
        body = gson.toJson(eiJobInfo(PUT_TYPE_ERROR_ID, JOB_ID));
        testErrorCode(restClient().put(url, body), HttpStatus.NOT_FOUND, "not found");
    }

    @Test
    void consumerPutInformationJob() throws Exception {
        // Test that one producer accepting a job is enough
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);

        String url = ConsumerConsts.API_ROOT + "/info-jobs/jobId";
        String body = gson.toJson(consumerJobInfo());
        ResponseEntity<String> resp = restClient().putForEntity(url, body).block();
        assertThat(this.infoJobs.size()).isEqualTo(1);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ProducerSimulatorController.TestResults simulatorResults = this.producerSimulator.getTestResults();
        await().untilAsserted(() -> assertThat(simulatorResults.jobsStarted).hasSize(1));
        ProducerJobInfo request = simulatorResults.jobsStarted.get(0);
        assertThat(request.id).isEqualTo("jobId");

        resp = restClient().putForEntity(url, body).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        InfoJob job = this.infoJobs.getJob("jobId");
        assertThat(job.getOwner()).isEqualTo("owner");

        verifyJobStatus(JOB_ID, "ENABLED");

        body = gson.toJson(consumerJobInfo("junkTypeId", "jobId", ""));
        testErrorCode(restClient().put(url, body), HttpStatus.NOT_FOUND, "not found");
    }

    @Test
    void testVersioning() throws Exception {
        final String REG_TYPE_ID1 = "type_1.5.0"; // Compatible
        final String REG_TYPE_ID2 = "type_1.6.0"; // Compatible
        final String REG_TYPE_ID3 = "type_2.0.0";
        final String REG_TYPE_ID4 = "type_1.0.0";
        final String REG_TYPE_ID5 = "xxxx_1.3.0"; // Other type

        final String PUT_TYPE_ID = "type_1.1.0";
        // Test that one producer accepting a job is enough
        putInfoProducerWithOneType(REG_TYPE_ID1, REG_TYPE_ID1);
        putInfoProducerWithOneType(REG_TYPE_ID2, REG_TYPE_ID2);
        putInfoProducerWithOneType(REG_TYPE_ID3, REG_TYPE_ID3);
        putInfoProducerWithOneType(REG_TYPE_ID4, REG_TYPE_ID4);
        putInfoProducerWithOneType(REG_TYPE_ID5, REG_TYPE_ID5);

        String url = ConsumerConsts.API_ROOT + "/info-jobs/jobId";
        String body = gson.toJson(consumerJobInfo(PUT_TYPE_ID, "jobId", "owner"));
        ResponseEntity<String> resp = restClient().putForEntity(url, body).block();
        assertThat(this.infoJobs.size()).isEqualTo(1);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ConsumerJobInfo getInfo = gson.fromJson(restClient().get(url).block(), ConsumerJobInfo.class);
        assertThat(getInfo.infoTypeId).isEqualTo(REG_TYPE_ID1);

        ProducerSimulatorController.TestResults simulatorResults = this.producerSimulator.getTestResults();
        await().untilAsserted(() -> assertThat(simulatorResults.jobsStarted).hasSize(2));
        ProducerJobInfo request = simulatorResults.jobsStarted.get(0);
        assertThat(request.id).isEqualTo("jobId");

        // TBD the producer gets the registerred type, but could get the requested
        // (PUT_TYPE_ID). This
        // depends on the
        // the principles for backwards compability.
        assertThat(request.typeId.equals(REG_TYPE_ID1) || request.typeId.equals(REG_TYPE_ID2)).isTrue();

        verifyJobStatus(JOB_ID, "ENABLED");

        // Test update job
        resp = restClient().putForEntity(url, body).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        await().untilAsserted(() -> assertThat(simulatorResults.jobsStarted).hasSize(4));

    }

    @Test
    void a1ePutEiJob_jsonSchemavalidationError() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);

        String url = A1eConsts.API_ROOT + "/eijobs/jobId";
        // The element with name "property1" is mandatory in the schema
        A1eEiJobInfo jobInfo = new A1eEiJobInfo(TYPE_ID, toJsonObject("{ \"XXstring\" : \"value\" }"), "owner",
            "targetUri", "jobStatusUrl");
        String body = gson.toJson(jobInfo);

        testErrorCode(restClient().put(url, body), HttpStatus.BAD_REQUEST, "Json validation failure");

        testErrorCode(restClient().put(url, "{jojo}"), HttpStatus.BAD_REQUEST, "", false);

    }

    @Test
    void consumerPutJob_jsonSchemavalidationError() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);

        String url = ConsumerConsts.API_ROOT + "/info-jobs/jobId";
        // The element with name "property1" is mandatory in the schema
        ConsumerJobInfo jobInfo =
            new ConsumerJobInfo(TYPE_ID, toJsonObject("{ \"XXstring\" : \"value\" }"), "owner", "targetUri", null);
        String body = gson.toJson(jobInfo);

        testErrorCode(restClient().put(url, body), HttpStatus.BAD_REQUEST, "Json validation failure");
    }

    @Test
    void consumerPutJob_uriError() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);

        String url = ConsumerConsts.API_ROOT + "/info-jobs/jobId";

        ConsumerJobInfo jobInfo = new ConsumerJobInfo(TYPE_ID, jsonObject(""), "owner", "junk", null);
        String body = gson.toJson(jobInfo);

        testErrorCode(restClient().put(url, body), HttpStatus.BAD_REQUEST, "URI: junk is not absolute");
    }

    @Test
    void a1eChangingEiTypeGetRejected() throws Exception {
        putInfoProducerWithOneType("producer1", "typeId1");
        putInfoProducerWithOneType("producer2", "typeId2");
        putInfoJob("typeId1", "jobId");

        String url = A1eConsts.API_ROOT + "/eijobs/jobId";
        String body = gson.toJson(eiJobInfo("typeId2", "jobId"));
        testErrorCode(restClient().put(url, body), HttpStatus.CONFLICT,
            "Not allowed to change type for existing EI job");
    }

    @Test
    void consumerChangingInfoTypeGetRejected() throws Exception {
        putInfoProducerWithOneType("producer1", "typeId1");
        putInfoProducerWithOneType("producer2", "typeId2");
        putInfoJob("typeId1", "jobId");

        String url = ConsumerConsts.API_ROOT + "/info-jobs/jobId";
        String body = gson.toJson(consumerJobInfo("typeId2", "jobId", "owner"));
        testErrorCode(restClient().put(url, body), HttpStatus.CONFLICT, "Not allowed to change type for existing job");
    }

    @Test
    void producerPutType() throws JsonMappingException, JsonProcessingException, ServiceException {
        assertThat(putInfoType(TYPE_ID)).isEqualTo(HttpStatus.CREATED);
        assertThat(putInfoType(TYPE_ID)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void producerPutType_noSchema() {
        String url = ProducerConsts.API_ROOT + "/info-types/" + TYPE_ID;
        String body = "{}";
        testErrorCode(restClient().put(url, body), HttpStatus.BAD_REQUEST, "No schema provided");

        testErrorCode(restClient().post(url, body), HttpStatus.METHOD_NOT_ALLOWED, "", false);
    }

    @Test
    void producerDeleteType() throws Exception {
        putInfoType(TYPE_ID);
        this.putInfoJob(TYPE_ID, "job1");
        this.putInfoJob(TYPE_ID, "job2");
        deleteInfoType(TYPE_ID);

        assertThat(this.infoTypes.size()).isZero();
        assertThat(this.infoJobs.size()).isZero(); // Test that also the job is deleted

        testErrorCode(restClient().delete(deleteInfoTypeUrl(TYPE_ID)), HttpStatus.NOT_FOUND,
            "Information type not found");
    }

    @Test
    void producerDeleteTypeExistingProducer() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        String url = ProducerConsts.API_ROOT + "/info-types/" + TYPE_ID;
        testErrorCode(restClient().delete(url), HttpStatus.CONFLICT, "The type has active producers: " + PRODUCER_ID);
        assertThat(this.infoTypes.size()).isEqualTo(1);
    }

    @Test
    void producerDeleteTypeExistingJob() throws Exception {
        putInfoType(TYPE_ID);
        String url = ProducerConsts.API_ROOT + "/info-types/" + TYPE_ID;
        putInfoJob(TYPE_ID, JOB_ID);
        restClient().delete(url).block();
        assertThat(this.infoTypes.size()).isZero();

        // The jobs are implicitly deleted
        assertThat(this.infoJobs.size()).isZero();
    }

    @Test
    void producerPutProducerWithOneType_rejecting() throws Exception {
        putInfoProducerWithOneTypeRejecting("simulateProducerError", TYPE_ID);
        String url = A1eConsts.API_ROOT + "/eijobs/" + JOB_ID;
        String body = gson.toJson(eiJobInfo());
        restClient().put(url, body).block();

        ProducerSimulatorController.TestResults simulatorResults = this.producerSimulator.getTestResults();
        // There is one retry -> 2 calls
        await().untilAsserted(() -> assertThat(simulatorResults.noOfRejectedCreate).isEqualTo(2));
        assertThat(simulatorResults.noOfRejectedCreate).isEqualTo(2);

        verifyJobStatus(JOB_ID, "DISABLED");
    }

    @Test
    void producerGetInfoProducerTypes() throws Exception {
        final String EI_TYPE_ID_2 = TYPE_ID + "_2";
        putInfoProducerWithOneType("producer1", TYPE_ID);
        putInfoJob(TYPE_ID, "jobId");
        putInfoProducerWithOneType("producer2", EI_TYPE_ID_2);
        putInfoJob(EI_TYPE_ID_2, "jobId2");
        String url = ProducerConsts.API_ROOT + "/info-types";

        ResponseEntity<String> resp = restClient().getForEntity(url).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains(TYPE_ID);
        assertThat(resp.getBody()).contains(EI_TYPE_ID_2);
    }

    @Test
    void producerPutInfoProducer() throws Exception {
        this.putInfoType(TYPE_ID);
        String url = ProducerConsts.API_ROOT + "/info-producers/infoProducerId";
        String body = gson.toJson(producerInfoRegistratioInfo(TYPE_ID));

        ResponseEntity<String> resp = restClient().putForEntity(url, body).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(this.infoTypes.size()).isEqualTo(1);
        InfoType type = this.infoTypes.getType(TYPE_ID);
        assertThat(this.infoProducers.getProducersSupportingType(type)).hasSize(1);
        assertThat(this.infoProducers.size()).isEqualTo(1);
        assertThat(this.infoProducers.get("infoProducerId").getInfoTypes().iterator().next().getId())
            .isEqualTo(TYPE_ID);

        resp = restClient().putForEntity(url, body).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // GET info producer
        resp = restClient().getForEntity(url).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(body);

        testErrorCode(restClient().get(url + "junk"), HttpStatus.NOT_FOUND, "Could not find Information Producer");
    }

    @Test
    void producerPutInfoProducerExistingJob() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, "jobId");
        String url = ProducerConsts.API_ROOT + "/info-producers/infoProducerId";
        String body = gson.toJson(producerInfoRegistratioInfo(TYPE_ID));
        restClient().putForEntity(url, body).block();

        ProducerSimulatorController.TestResults simulatorResults = this.producerSimulator.getTestResults();
        await().untilAsserted(() -> assertThat(simulatorResults.jobsStarted).hasSize(2));
        ProducerJobInfo request = simulatorResults.jobsStarted.get(0);
        assertThat(request.id).isEqualTo("jobId");
    }

    @Test
    void testPutInfoProducer_noType() throws Exception {
        String url = ProducerConsts.API_ROOT + "/info-producers/infoProducerId";
        String body = gson.toJson(producerInfoRegistratioInfo(TYPE_ID));
        testErrorCode(restClient().put(url, body), HttpStatus.NOT_FOUND, "Information type not found");
    }

    @Test
    void producerPutProducerAndInfoJob() throws Exception {
        this.putInfoType(TYPE_ID);
        String url = ProducerConsts.API_ROOT + "/info-producers/infoProducerId";
        String body = gson.toJson(producerInfoRegistratioInfo(TYPE_ID));
        restClient().putForEntity(url, body).block();
        assertThat(this.infoTypes.size()).isEqualTo(1);
        this.infoTypes.getType(TYPE_ID);

        url = A1eConsts.API_ROOT + "/eijobs/jobId";
        body = gson.toJson(eiJobInfo());
        restClient().putForEntity(url, body).block();

        ProducerSimulatorController.TestResults simulatorResults = this.producerSimulator.getTestResults();
        await().untilAsserted(() -> assertThat(simulatorResults.jobsStarted).hasSize(1));
        ProducerJobInfo request = simulatorResults.jobsStarted.get(0);
        assertThat(request.id).isEqualTo("jobId");
    }

    @Test
    void producerGetInfoJobsForProducer() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, "jobId1");
        putInfoJob(TYPE_ID, "jobId2");

        // PUT a consumerRestApiTestBase.java
        String url = ProducerConsts.API_ROOT + "/info-producers/infoProducerId";
        String body = gson.toJson(producerInfoRegistratioInfo(TYPE_ID));
        restClient().putForEntity(url, body).block();

        url = ProducerConsts.API_ROOT + "/info-producers/infoProducerId/info-jobs";
        ResponseEntity<String> resp = restClient().getForEntity(url).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ProducerJobInfo[] parsedResp = gson.fromJson(resp.getBody(), ProducerJobInfo[].class);
        assertThat(parsedResp[0].typeId).isEqualTo(TYPE_ID);
        assertThat(parsedResp[1].typeId).isEqualTo(TYPE_ID);
    }

    @Test
    void producerDeleteInfoProducer() throws Exception {
        putInfoProducerWithOneType("infoProducerId", TYPE_ID);
        putInfoProducerWithOneType("infoProducerId2", TYPE_ID);

        assertThat(this.infoProducers.size()).isEqualTo(2);
        InfoType type = this.infoTypes.getType(TYPE_ID);
        assertThat(this.infoProducers.getProducerIdsForType(type)).contains("infoProducerId");
        assertThat(this.infoProducers.getProducerIdsForType(type)).contains("infoProducerId2");
        putInfoJob(TYPE_ID, "jobId");
        assertThat(this.infoJobs.size()).isEqualTo(1);

        deleteInfoProducer("infoProducerId");
        assertThat(this.infoProducers.size()).isEqualTo(1);
        assertThat(this.infoProducers.getProducerIdsForType(type)).doesNotContain("infoProducerId");
        verifyJobStatus("jobId", "ENABLED");

        deleteInfoProducer("infoProducerId2");
        assertThat(this.infoProducers.size()).isZero();
        assertThat(this.infoTypes.size()).isEqualTo(1);
        verifyJobStatus("jobId", "DISABLED");

        String url = ProducerConsts.API_ROOT + "/info-producers/" + "junk";
        testErrorCode(restClient().delete(url), HttpStatus.NOT_FOUND, "Could not find Information Producer");
    }

    @Test
    void a1eJobStatusNotifications() throws Exception {
        A1eCallbacksSimulatorController.TestResults consumerCalls = this.a1eCallbacksSimulator.getTestResults();
        ProducerSimulatorController.TestResults producerCalls = this.producerSimulator.getTestResults();

        putInfoProducerWithOneType("infoProducerId", TYPE_ID);
        putInfoJob(TYPE_ID, "jobId");
        putInfoProducerWithOneType("infoProducerId2", TYPE_ID);
        await().untilAsserted(() -> assertThat(producerCalls.jobsStarted).hasSize(2));

        deleteInfoProducer("infoProducerId2");
        assertThat(this.infoTypes.size()).isEqualTo(1); // The type remains, one producer left
        deleteInfoProducer("infoProducerId");
        assertThat(this.infoTypes.size()).isEqualTo(1); // The type remains
        assertThat(this.infoJobs.size()).isEqualTo(1); // The job remains
        await().untilAsserted(() -> assertThat(consumerCalls.eiJobStatusCallbacks).hasSize(1));
        assertThat(consumerCalls.eiJobStatusCallbacks.get(0).state)
            .isEqualTo(A1eEiJobStatus.EiJobStatusValues.DISABLED);

        putInfoProducerWithOneType("infoProducerId", TYPE_ID);
        await().untilAsserted(() -> assertThat(consumerCalls.eiJobStatusCallbacks).hasSize(2));
        assertThat(consumerCalls.eiJobStatusCallbacks.get(1).state).isEqualTo(A1eEiJobStatus.EiJobStatusValues.ENABLED);
    }

    @Test
    void a1eJobStatusNotifications2() throws Exception {
        // Test replacing a producer with new and removed types

        // Create a job
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, JOB_ID);

        // change the type for the producer, the job shall be disabled
        putInfoProducerWithOneType(PRODUCER_ID, "junk");
        verifyJobStatus(JOB_ID, "DISABLED");
        A1eCallbacksSimulatorController.TestResults consumerCalls = this.a1eCallbacksSimulator.getTestResults();
        await().untilAsserted(() -> assertThat(consumerCalls.eiJobStatusCallbacks).hasSize(1));
        assertThat(consumerCalls.eiJobStatusCallbacks.get(0).state)
            .isEqualTo(A1eEiJobStatus.EiJobStatusValues.DISABLED);

        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        verifyJobStatus(JOB_ID, "ENABLED");
        await().untilAsserted(() -> assertThat(consumerCalls.eiJobStatusCallbacks).hasSize(2));
        assertThat(consumerCalls.eiJobStatusCallbacks.get(1).state).isEqualTo(A1eEiJobStatus.EiJobStatusValues.ENABLED);
    }

    @Test
    void producerGetProducerInfoType() throws JsonMappingException, JsonProcessingException, ServiceException {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        String url = ProducerConsts.API_ROOT + "/info-types/" + TYPE_ID;
        ResponseEntity<String> resp = restClient().getForEntity(url).block();
        ProducerInfoTypeInfo info = gson.fromJson(resp.getBody(), ProducerInfoTypeInfo.class);
        assertThat(info.jobDataSchema).isNotNull();
        assertThat(info.typeSpecificInformation).isNotNull();

        testErrorCode(restClient().get(url + "junk"), HttpStatus.NOT_FOUND, "Information type not found");
    }

    @Test
    void producerGetProducerIdentifiers() throws JsonMappingException, JsonProcessingException, ServiceException {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        String url = ProducerConsts.API_ROOT + "/info-producers";
        ResponseEntity<String> resp = restClient().getForEntity(url).block();
        assertThat(resp.getBody()).contains(PRODUCER_ID);

        url = ProducerConsts.API_ROOT + "/info-producers?infoTypeId=" + TYPE_ID;
        resp = restClient().getForEntity(url).block();
        assertThat(resp.getBody()).contains(PRODUCER_ID);

        url = ProducerConsts.API_ROOT + "/info-producers?infoTypeId=junk";
        resp = restClient().getForEntity(url).block();
        assertThat(resp.getBody()).isEqualTo("[]");
    }

    @Test
    void producerSupervision() throws Exception {

        A1eCallbacksSimulatorController.TestResults consumerResults = this.a1eCallbacksSimulator.getTestResults();
        putInfoProducerWithOneTypeRejecting("simulateProducerError", TYPE_ID);

        {
            // Create a job
            putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
            putInfoJob(TYPE_ID, JOB_ID);
            verifyJobStatus(JOB_ID, "ENABLED");
            deleteInfoProducer(PRODUCER_ID);
            // A Job disabled status notification shall now be received
            await().untilAsserted(() -> assertThat(consumerResults.eiJobStatusCallbacks).hasSize(1));
            assertThat(consumerResults.eiJobStatusCallbacks.get(0).state)
                .isEqualTo(A1eEiJobStatus.EiJobStatusValues.DISABLED);
            verifyJobStatus(JOB_ID, "DISABLED");
        }

        assertThat(this.infoProducers.size()).isEqualTo(1);
        assertThat(this.infoTypes.size()).isEqualTo(1);
        assertProducerOpState("simulateProducerError", ProducerStatusInfo.OperationalState.ENABLED);

        this.producerSupervision.createTask().blockLast();
        this.producerSupervision.createTask().blockLast();

        // Now we have one producer that is disabled
        assertThat(this.infoProducers.size()).isEqualTo(1);
        assertProducerOpState("simulateProducerError", ProducerStatusInfo.OperationalState.DISABLED);

        // After 3 failed checks, the producer shall be deregistered
        this.producerSupervision.createTask().blockLast();
        assertThat(this.infoProducers.size()).isZero(); // The producer is removed
        assertThat(this.infoTypes.size()).isEqualTo(1); // The type remains

        // Now we have one disabled job, and no producer.
        // PUT a producer, then a Job ENABLED status notification shall be received
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        await().untilAsserted(() -> assertThat(consumerResults.eiJobStatusCallbacks).hasSize(2));
        assertThat(consumerResults.eiJobStatusCallbacks.get(1).state)
            .isEqualTo(A1eEiJobStatus.EiJobStatusValues.ENABLED);
        verifyJobStatus(JOB_ID, "ENABLED");
    }

    @Test
    void producerSupervision2() throws Exception {
        // Test that supervision enables not enabled jobs and sends a notification when
        // suceeded

        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, JOB_ID);

        InfoProducer producer = this.infoProducers.getProducer(PRODUCER_ID);
        InfoJob job = this.infoJobs.getJob(JOB_ID);
        // Pretend that the producer did reject the job and the a DISABLED notification
        // is sent for the job
        producer.setJobDisabled(job);
        job.setLastReportedStatus(false);
        verifyJobStatus(JOB_ID, "DISABLED");

        // Run the supervision and wait for the job to get started in the producer
        this.producerSupervision.createTask().blockLast();
        A1eCallbacksSimulatorController.TestResults consumerResults = this.a1eCallbacksSimulator.getTestResults();
        await().untilAsserted(() -> assertThat(consumerResults.eiJobStatusCallbacks).hasSize(1));
        assertThat(consumerResults.eiJobStatusCallbacks.get(0).state)
            .isEqualTo(A1eEiJobStatus.EiJobStatusValues.ENABLED);
        verifyJobStatus(JOB_ID, "ENABLED");
    }

    @Test
    void testGetStatus() throws JsonMappingException, JsonProcessingException, ServiceException {
        putInfoProducerWithOneTypeRejecting("simulateProducerError", TYPE_ID);
        putInfoProducerWithOneTypeRejecting("simulateProducerError2", TYPE_ID);

        String url = "/status";
        ResponseEntity<String> resp = restClient().getForEntity(url).block();
        assertThat(resp.getBody()).contains("hunky dory");
    }

    @Test
    void testDb() {
        DataStore db = DataStore.create(this.applicationConfig, "test");
        db.createDataStore().block();
        final int NO_OF_OBJS = 1200;
        for (int i = 0; i < NO_OF_OBJS; ++i) {
            String data = "data";
            db.writeObject("Obj_" + i, data.getBytes()).block();
        }

        List<?> entries = db.listObjects("").collectList().block();
        assertThat(entries).hasSize(NO_OF_OBJS);

        db.listObjects("").doOnNext(name -> logger.debug("deleted {}", name)).flatMap(name -> db.deleteObject(name))
            .blockLast();

        db.createDataStore().block();

    }

    @Test
    void testJobDatabasePersistence() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, "jobId1");
        putInfoJob(TYPE_ID, "jobId2");
        waitForS3();

        assertThat(this.infoJobs.size()).isEqualTo(2);
        {
            InfoJob savedJob = this.infoJobs.getJob("jobId1");
            // Restore the jobs
            InfoJobs jobs = new InfoJobs(this.applicationConfig, this.infoTypes, this.producerCallbacks);
            jobs.restoreJobsFromDatabase().blockLast();
            assertThat(jobs.size()).isEqualTo(2);
            InfoJob restoredJob = jobs.getJob("jobId1");
            assertThat(restoredJob.getPersistentData()).isEqualTo(savedJob.getPersistentData());
            assertThat(restoredJob.getId()).isEqualTo("jobId1");
            assertThat(restoredJob.getLastUpdated()).isEqualTo(savedJob.getLastUpdated());

            jobs.remove("jobId1", this.infoProducers);
            jobs.remove("jobId2", this.infoProducers);
        }
        {
            // Restore the jobs, no jobs in database
            InfoJobs jobs = new InfoJobs(this.applicationConfig, this.infoTypes, this.producerCallbacks);
            jobs.restoreJobsFromDatabase().blockLast();
            assertThat(jobs.size()).isZero();
        }
        logger.warn("Test removing a job when the db file is gone");
        this.infoJobs.remove("jobId1", this.infoProducers);
        assertThat(this.infoJobs.size()).isEqualTo(1);

        ProducerSimulatorController.TestResults simulatorResults = this.producerSimulator.getTestResults();
        await().untilAsserted(() -> assertThat(simulatorResults.jobsStopped).hasSize(3));
    }

    @Test
    void testTypesDatabasePersistence() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        InfoType savedType = this.infoTypes.getType(TYPE_ID);

        waitForS3();
        assertThat(this.infoTypes.size()).isEqualTo(1);

        {
            // Restore the types
            InfoTypes restoredTypes = new InfoTypes(this.applicationConfig);
            restoredTypes.restoreTypesFromDatabase().blockLast();
            InfoType restoredType = restoredTypes.getType(TYPE_ID);
            assertThat(restoredType.getPersistentInfo()).isEqualTo(savedType.getPersistentInfo());
            assertThat(restoredTypes.size()).isEqualTo(1);
        }
        {
            // Restore the jobs, no jobs in database
            InfoTypes restoredTypes = new InfoTypes(this.applicationConfig);
            restoredTypes.clear();
            restoredTypes.restoreTypesFromDatabase().blockLast();
            assertThat(restoredTypes.size()).isZero();
        }
        logger.warn("Test removing a job when the db file is gone");
        this.infoTypes.remove(this.infoTypes.getType(TYPE_ID));
        assertThat(this.infoJobs.size()).isZero();
    }

    @Test
    void testConsumerTypeSubscriptionDatabase() throws Exception {
        final String callbackUrl = baseUrl() + ConsumerSimulatorController.getTypeStatusCallbackUrl();
        final ConsumerTypeSubscriptionInfo info = new ConsumerTypeSubscriptionInfo(callbackUrl, "owner");

        // PUT a subscription
        String body = gson.toJson(info);
        restClient().putForEntity(typeSubscriptionUrl() + "/subscriptionId", body).block();
        assertThat(this.infoTypeSubscriptions.size()).isEqualTo(1);

        waitForS3();

        InfoTypeSubscriptions restoredSubscriptions = new InfoTypeSubscriptions(this.applicationConfig);
        restoredSubscriptions.restoreFromDatabase().blockLast();
        assertThat(restoredSubscriptions.size()).isEqualTo(1);
        assertThat(restoredSubscriptions.getSubscriptionsForOwner("owner")).hasSize(1);

        // Delete the subscription
        restClient().deleteForEntity(typeSubscriptionUrl() + "/subscriptionId").block();
        restoredSubscriptions = new InfoTypeSubscriptions(this.applicationConfig);
        assertThat(restoredSubscriptions.size()).isZero();
    }

    @SuppressWarnings("java:S2925") // sleep
    private void waitForS3() throws InterruptedException {
        if (this.applicationConfig.isS3Enabled()) {
            Thread.sleep(1000); // Storing in S3 is asynch, so it can take some millis
        }
    }

    @Test
    void testConsumerTypeSubscription() throws Exception {

        final String callbackUrl = baseUrl() + ConsumerSimulatorController.getTypeStatusCallbackUrl();
        final ConsumerTypeSubscriptionInfo info = new ConsumerTypeSubscriptionInfo(callbackUrl, "owner");

        testErrorCode(restClient().get(typeSubscriptionUrl() + "/junk"), HttpStatus.NOT_FOUND,
            "Could not find Information subscription: junk");

        testErrorCode(restClient().delete(typeSubscriptionUrl() + "/junk"), HttpStatus.NOT_FOUND,
            "Could not find Information subscription: junk");

        {
            // PUT a subscription
            String body = gson.toJson(info);
            ResponseEntity<String> resp =
                restClient().putForEntity(typeSubscriptionUrl() + "/subscriptionId", body).block();
            assertThat(this.infoTypeSubscriptions.size()).isEqualTo(1);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            resp = restClient().putForEntity(typeSubscriptionUrl() + "/subscriptionId", body).block();
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        {
            // GET IDs
            ResponseEntity<String> resp = restClient().getForEntity(typeSubscriptionUrl()).block();
            assertThat(resp.getBody()).isEqualTo("[\"subscriptionId\"]");
            resp = restClient().getForEntity(typeSubscriptionUrl() + "?owner=owner").block();
            assertThat(resp.getBody()).isEqualTo("[\"subscriptionId\"]");
            resp = restClient().getForEntity(typeSubscriptionUrl() + "?owner=junk").block();
            assertThat(resp.getBody()).isEqualTo("[]");
        }

        {
            // GET the individual subscription
            ResponseEntity<String> resp = restClient().getForEntity(typeSubscriptionUrl() + "/subscriptionId").block();
            ConsumerTypeSubscriptionInfo respInfo = gson.fromJson(resp.getBody(), ConsumerTypeSubscriptionInfo.class);
            assertThat(respInfo).isEqualTo(info);
        }

        {
            // Test the callbacks
            final ConsumerSimulatorController.TestResults consumerCalls = this.consumerSimulator.getTestResults();

            // Test callback for PUT type
            this.putInfoType(TYPE_ID);
            await().untilAsserted(() -> assertThat(consumerCalls.typeRegistrationInfoCallbacks).hasSize(1));
            assertThat(consumerCalls.typeRegistrationInfoCallbacks.get(0).state)
                .isEqualTo(ConsumerTypeRegistrationInfo.ConsumerTypeStatusValues.REGISTERED);

            // Test callback for DELETE type
            this.deleteInfoType(TYPE_ID);
            await().untilAsserted(() -> assertThat(consumerCalls.typeRegistrationInfoCallbacks).hasSize(2));
            assertThat(consumerCalls.typeRegistrationInfoCallbacks.get(1).state)
                .isEqualTo(ConsumerTypeRegistrationInfo.ConsumerTypeStatusValues.DEREGISTERED);
        }

        {
            // DELETE the subscription
            ResponseEntity<String> resp =
                restClient().deleteForEntity(typeSubscriptionUrl() + "/subscriptionId").block();
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(this.infoTypeSubscriptions.size()).isZero();
            resp = restClient().getForEntity(typeSubscriptionUrl()).block();
            assertThat(resp.getBody()).isEqualTo("[]");
        }
    }

    @Test
    void testRemovingNonWorkingSubscription() throws Exception {
        // Test that subscriptions are removed for a unresponsive consumer

        // PUT a subscription with a junk callback
        final ConsumerTypeSubscriptionInfo info = new ConsumerTypeSubscriptionInfo(baseUrl() + "/JUNK", "owner");
        String body = gson.toJson(info);
        restClient().putForEntity(typeSubscriptionUrl() + "/subscriptionId", body).block();
        assertThat(this.infoTypeSubscriptions.size()).isEqualTo(1);

        this.putInfoType(TYPE_ID);
        // The callback will fail and the subscription will be removed
        await().untilAsserted(() -> assertThat(this.infoTypeSubscriptions.size()).isZero());
    }

    @Test
    void testTypeSubscriptionErrorCodes() throws Exception {

        testErrorCode(restClient().get(typeSubscriptionUrl() + "/junk"), HttpStatus.NOT_FOUND,
            "Could not find Information subscription: junk");

        testErrorCode(restClient().delete(typeSubscriptionUrl() + "/junk"), HttpStatus.NOT_FOUND,
            "Could not find Information subscription: junk");
    }

    @Test
    void testFineGrainedAuthorizationCheck() throws Exception {
        this.applicationConfig.setAuthAgentUrl(baseUrl() + OpenPolicyAgentSimulatorController.SUBSCRIPTION_AUTH_URL);
        final String AUTH_TOKEN = "testToken";
        Path authFile = Files.createTempFile("icsTestAuthToken", ".txt");
        Files.write(authFile, AUTH_TOKEN.getBytes());
        this.securityContext.setAuthTokenFilePath(authFile);
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, "jobId");

        var testResults = openPolicyAgentSimulatorController.getTestResults();

        await().untilAsserted(() -> assertThat(testResults.receivedRequests).hasSize(1));

        // Test OPA check
        SubscriptionAuthRequest authRequest = testResults.receivedRequests.get(0);
        assertThat(authRequest.getInput().getAccessType()).isEqualTo(AccessType.WRITE);
        assertThat(authRequest.getInput().getInfoTypeId()).isEqualTo(TYPE_ID);
        assertThat(authRequest.getInput().getAuthToken()).isEqualTo(AUTH_TOKEN);
    }

    @Test
    void testFineGrainedAuthorizationCheckRejections() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, JOB_ID);

        // Test rejection from OPA
        this.applicationConfig
            .setAuthAgentUrl(baseUrl() + OpenPolicyAgentSimulatorController.SUBSCRIPTION_REJECT_AUTH_URL);
        var testResults = openPolicyAgentSimulatorController.getTestResults();

        // R1
        String url = ConsumerConsts.API_ROOT + "/info-jobs/" + JOB_ID;
        testErrorCode(restClient().delete(url), HttpStatus.UNAUTHORIZED, "Not authorized");
        assertThat(testResults.receivedRequests).hasSize(2);
        SubscriptionAuthRequest authRequest = testResults.receivedRequests.get(1);
        assertThat(authRequest.getInput().getAccessType()).isEqualTo(AccessType.WRITE);

        String body = gson.toJson(consumerJobInfo(TYPE_ID, JOB_ID, "owner"));
        testErrorCode(restClient().put(url, body), HttpStatus.UNAUTHORIZED, "Not authorized");

        testErrorCode(restClient().get(url), HttpStatus.UNAUTHORIZED, "Not authorized");

        // A1-E
        url = A1eConsts.API_ROOT + "/eijobs/" + JOB_ID;
        testErrorCode(restClient().get(url), HttpStatus.UNAUTHORIZED, "Not authorized");

        testErrorCode(restClient().delete(url), HttpStatus.UNAUTHORIZED, "Not authorized");

        body = gson.toJson(eiJobInfo(TYPE_ID, JOB_ID, "owner"));
        testErrorCode(restClient().put(url, body), HttpStatus.UNAUTHORIZED, "Not authorized");
    }

    @Test
    void testFineGrainedAuthorizationCheckRejections_OPA_UNAVALIABLE() throws Exception {
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, JOB_ID);

        // Test rejection from OPA
        this.applicationConfig.setAuthAgentUrl("junk");

        // R1
        String url = ConsumerConsts.API_ROOT + "/info-jobs/" + JOB_ID;
        testErrorCode(restClient().delete(url), HttpStatus.UNAUTHORIZED, "Not authorized");

        String body = gson.toJson(consumerJobInfo(TYPE_ID, JOB_ID, "owner"));
        testErrorCode(restClient().put(url, body), HttpStatus.UNAUTHORIZED, "Not authorized");

        testErrorCode(restClient().get(url), HttpStatus.UNAUTHORIZED, "Not authorized");

        // A1-E
        url = A1eConsts.API_ROOT + "/eijobs/" + JOB_ID;
        testErrorCode(restClient().get(url), HttpStatus.UNAUTHORIZED, "Not authorized");

        testErrorCode(restClient().delete(url), HttpStatus.UNAUTHORIZED, "Not authorized");

        body = gson.toJson(eiJobInfo(TYPE_ID, JOB_ID, "owner"));
        testErrorCode(restClient().put(url, body), HttpStatus.UNAUTHORIZED, "Not authorized");
    }

    @Test
    void testAuthHeader() throws Exception {
        final String AUTH_TOKEN = "testToken";
        Path authFile = Files.createTempFile("icsTestAuthToken", ".txt");
        Files.write(authFile, AUTH_TOKEN.getBytes());
        this.securityContext.setAuthTokenFilePath(authFile);
        putInfoProducerWithOneType(PRODUCER_ID, TYPE_ID);
        putInfoJob(TYPE_ID, "jobId");

        // Test that authorization header is sent to the producer.
        await().untilAsserted(() -> assertThat(this.producerSimulator.getTestResults().receivedHeaders).hasSize(1));
        Map<String, String> headers = this.producerSimulator.getTestResults().receivedHeaders.get(0);
        assertThat(headers).containsEntry("authorization", "Bearer " + AUTH_TOKEN);

        Files.delete(authFile);

        // Test that it works when the file is deleted. The cached header is used
        putInfoJob(TYPE_ID, "jobId2");
        await().untilAsserted(() -> assertThat(this.infoJobs.size()).isEqualByComparingTo(2));
        headers = this.producerSimulator.getTestResults().receivedHeaders.get(1);
        assertThat(headers).containsEntry("authorization", "Bearer " + AUTH_TOKEN);

    }

    @Test
    @SuppressWarnings("squid:S2925") // "Thread.sleep" should not be used in tests.
    void testZZActuator() throws Exception {
        // The test must be run last, hence the "ZZ" in the name. All succeeding tests
        // will fail.
        AsyncRestClient client = restClient();
        client.post("/actuator/loggers/org.oransc.ics", "{\"configuredLevel\":\"trace\"}").block();
        String resp = client.get("/actuator/loggers/org.oransc.ics").block();
        assertThat(resp).contains("TRACE");
        client.post("/actuator/loggers/org.springframework.boot.actuate", "{\"configuredLevel\":\"trace\"}").block();
        // This will stop the web server and all coming tests will fail.
        client.post("/actuator/shutdown", "").block();
        Thread.sleep(1000);

        StepVerifier.create(restClient().get(ConsumerConsts.API_ROOT + "/info-jobs")) // Any call
            .expectSubscription() //
            .expectErrorMatches(t -> t instanceof WebClientRequestException) //
            .verify();
    }

    private String typeSubscriptionUrl() {
        return ConsumerConsts.API_ROOT + "/info-type-subscription";
    }

    private void deleteInfoProducer(String infoProducerId) {
        String url = ProducerConsts.API_ROOT + "/info-producers/" + infoProducerId;
        restClient().deleteForEntity(url).block();
    }

    private void verifyJobStatus(String jobId, String expStatus) {
        String url = A1eConsts.API_ROOT + "/eijobs/" + jobId + "/status";
        String rsp = restClient().get(url).block();
        assertThat(rsp).contains(expStatus);
    }

    private void assertProducerOpState(String producerId,
        ProducerStatusInfo.OperationalState expectedOperationalState) {
        String statusUrl = ProducerConsts.API_ROOT + "/info-producers/" + producerId + "/status";
        ResponseEntity<String> resp = restClient().getForEntity(statusUrl).block();
        ProducerStatusInfo statusInfo = gson.fromJson(resp.getBody(), ProducerStatusInfo.class);
        assertThat(statusInfo.opState).isEqualTo(expectedOperationalState);
    }

    ProducerInfoTypeInfo ProducerInfoTypeRegistrationInfo(String typeId)
        throws JsonMappingException, JsonProcessingException {
        return new ProducerInfoTypeInfo(jsonSchemaObject(), typeSpecifcInfoObject());
    }

    ProducerRegistrationInfo producerEiRegistratioInfoRejecting(String typeId)
        throws JsonMappingException, JsonProcessingException {
        return new ProducerRegistrationInfo(Arrays.asList(typeId), //
            baseUrl() + ProducerSimulatorController.JOB_ERROR_URL,
            baseUrl() + ProducerSimulatorController.SUPERVISION_ERROR_URL);
    }

    ProducerRegistrationInfo producerInfoRegistratioInfo(String typeId)
        throws JsonMappingException, JsonProcessingException {
        return new ProducerRegistrationInfo(Arrays.asList(typeId), //
            baseUrl() + ProducerSimulatorController.JOB_URL, baseUrl() + ProducerSimulatorController.SUPERVISION_URL);
    }

    private ConsumerJobInfo consumerJobInfo() throws JsonMappingException, JsonProcessingException {
        return consumerJobInfo(TYPE_ID, JOB_ID, "owner");
    }

    ConsumerJobInfo consumerJobInfo(String typeId, String infoJobId, String owner)
        throws JsonMappingException, JsonProcessingException {
        return new ConsumerJobInfo(typeId, jsonObject(owner), owner, "https://junk.com",
            baseUrl() + A1eCallbacksSimulatorController.getJobStatusUrl(infoJobId));
    }

    private A1eEiJobInfo eiJobInfo() throws Exception {
        return eiJobInfo(TYPE_ID, JOB_ID);
    }

    A1eEiJobInfo eiJobInfo(String typeId, String infoJobId) throws Exception {
        return eiJobInfo(typeId, infoJobId, "owner");

    }

    A1eEiJobInfo eiJobInfo(String typeId, String infoJobId, String owner) throws Exception {
        return new A1eEiJobInfo(typeId, jsonObject(owner), owner, "https://junk.com",
            baseUrl() + A1eCallbacksSimulatorController.getJobStatusUrl(infoJobId));
    }

    private Object toJsonObject(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new NullPointerException(e.toString());
        }
    }

    private Object jsonObject(String aValue) {
        return toJsonObject("{ " + EI_JOB_PROPERTY + " : \"" + aValue + "\" }");
    }

    private Object typeSpecifcInfoObject() {
        return toJsonObject("{ \"propertyName\" : \"value\" }");
    }

    private Object jsonSchemaObject() {
        // a json schema with one mandatory property named "string"
        String schemaStr = "{" //
            + "\"$schema\": \"http://json-schema.org/draft-04/schema#\"," //
            + "\"type\": \"object\"," //
            + "\"properties\": {" //
            + EI_JOB_PROPERTY + " : {" //
            + "    \"type\": \"string\"" //
            + "  }" //
            + "}," //
            + "\"required\": [" //
            + EI_JOB_PROPERTY //
            + "]" //
            + "}"; //
        return toJsonObject(schemaStr);
    }

    private InfoJob putEiJob(String infoTypeId, String jobId, String owner) throws Exception {
        String url = A1eConsts.API_ROOT + "/eijobs/" + jobId;
        String body = gson.toJson(eiJobInfo(infoTypeId, jobId, owner));
        restClient().putForEntity(url, body).block();
        return this.infoJobs.getJob(jobId);

    }

    private InfoJob putInfoJob(String infoTypeId, String jobId) throws Exception {
        return putInfoJob(infoTypeId, jobId, "owner");
    }

    private InfoJob putInfoJob(String infoTypeId, String jobId, String owner) throws Exception {
        String url = ConsumerConsts.API_ROOT + "/info-jobs/" + jobId;
        String body = gson.toJson(consumerJobInfo(infoTypeId, jobId, owner));
        restClient().putForEntity(url, body).block();

        return this.infoJobs.getJob(jobId);
    }

    private HttpStatusCode putInfoType(String infoTypeId)
        throws JsonMappingException, JsonProcessingException, ServiceException {
        String url = ProducerConsts.API_ROOT + "/info-types/" + infoTypeId;
        String body = gson.toJson(ProducerInfoTypeRegistrationInfo(infoTypeId));

        ResponseEntity<String> resp = restClient().putForEntity(url, body).block();
        this.infoTypes.getType(infoTypeId);
        return resp.getStatusCode();
    }

    private String deleteInfoTypeUrl(String typeId) {
        return ProducerConsts.API_ROOT + "/info-types/" + typeId;
    }

    private void deleteInfoType(String typeId) {
        restClient().delete(deleteInfoTypeUrl(typeId)).block();
    }

    private InfoType putInfoProducerWithOneTypeRejecting(String producerId, String infoTypeId)
        throws JsonMappingException, JsonProcessingException, ServiceException {
        this.putInfoType(infoTypeId);
        String url = ProducerConsts.API_ROOT + "/info-producers/" + producerId;
        String body = gson.toJson(producerEiRegistratioInfoRejecting(infoTypeId));
        restClient().putForEntity(url, body).block();
        return this.infoTypes.getType(infoTypeId);
    }

    private InfoType putInfoProducerWithOneType(String producerId, String infoTypeId)
        throws JsonMappingException, JsonProcessingException, ServiceException {
        if (this.infoTypes.get(infoTypeId) == null) {
            this.putInfoType(infoTypeId);
        }

        String url = ProducerConsts.API_ROOT + "/info-producers/" + producerId;
        String body = gson.toJson(producerInfoRegistratioInfo(infoTypeId));

        restClient().putForEntity(url, body).block();

        return this.infoTypes.getType(infoTypeId);
    }

    private String baseUrl() {
        return "https://localhost:" + this.port;
    }

    private AsyncRestClient restClient(boolean useTrustValidation) {
        WebClientConfig config = this.applicationConfig.getWebClientConfig();
        HttpProxyConfig httpProxyConfig = HttpProxyConfig.builder() //
            .httpProxyHost("") //
            .httpProxyPort(0) //
            .build();
        config = WebClientConfig.builder() //
            .keyStoreType(config.getKeyStoreType()) //
            .keyStorePassword(config.getKeyStorePassword()) //
            .keyStore(config.getKeyStore()) //
            .keyPassword(config.getKeyPassword()) //
            .isTrustStoreUsed(useTrustValidation) //
            .trustStore(config.getTrustStore()) //
            .trustStorePassword(config.getTrustStorePassword()) //
            .httpProxyConfig(httpProxyConfig).build();

        AsyncRestClientFactory restClientFactory = new AsyncRestClientFactory(config, securityContext);
        return restClientFactory.createRestClientNoHttpProxy(baseUrl());
    }

    private AsyncRestClient restClient() {
        return restClient(false);
    }

    private void testErrorCode(Mono<?> request, HttpStatus expStatus, String responseContains) {
        testErrorCode(request, expStatus, responseContains, true);
    }

    private void testErrorCode(Mono<?> request, HttpStatus expStatus, String responseContains,
        boolean expectApplicationProblemJsonMediaType) {
        StepVerifier.create(request) //
            .expectSubscription() //
            .expectErrorMatches(
                t -> checkWebClientError(t, expStatus, responseContains, expectApplicationProblemJsonMediaType)) //
            .verify();
    }

    private boolean checkWebClientError(Throwable throwable, HttpStatus expStatus, String responseContains,
        boolean expectApplicationProblemJsonMediaType) {
        assertTrue(throwable instanceof WebClientResponseException);
        WebClientResponseException responseException = (WebClientResponseException) throwable;
        assertThat(responseException.getStatusCode()).isEqualTo(expStatus);
        assertThat(responseException.getResponseBodyAsString()).contains(responseContains);
        if (expectApplicationProblemJsonMediaType) {
            assertThat(responseException.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        }
        return true;
    }

}
