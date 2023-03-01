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

import org.junit.jupiter.api.Test;
import org.oransc.ics.repository.InfoJobs;
import org.oransc.ics.repository.InfoTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@TestPropertySource(
    properties = { //
        "server.ssl.key-store=./config/keystore.jks", //
        "app.webclient.trust-store=./config/truststore.jks", //
        "app.vardata-directory=./target" //
    })
@SuppressWarnings("squid:S3577") // Not containing any tests since it is a mock.
class MockInformationService {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationTest.class);

    @LocalServerPort
    private int port;

    @Autowired
    InfoTypes infoTypes;

    @Autowired
    InfoJobs infoJobs;

    @Test
    @SuppressWarnings("squid:S2699")
    void runMock() throws Exception {
        logger.warn("**************** Keeping server alive! " + this.port);
        synchronized (this) {
            while (true) {
                System.out.println("**** Types *** ");
                this.infoTypes.getAllInfoTypes().forEach(type -> System.out.println("  " + type.getId()));
                System.out.println("**** Jobs *** ");
                this.infoJobs.getJobs()
                    .forEach(job -> System.out.println("  id: " + job.getId() + ", type:" + job.getType().getId()));
                Thread.sleep(1000 * 60);

            }
        }
    }
}
