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

package org.oransc.ics.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Getter;

import org.oransc.ics.controllers.VoidResponse;
import org.oransc.ics.controllers.r1consumer.ConsumerConsts;
import org.oransc.ics.controllers.r1consumer.ConsumerTypeRegistrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("ConsumerSimulatorController")
@Tag(name = ConsumerConsts.CONSUMER_API_CALLBACKS_NAME, description = ConsumerConsts.CONSUMER_API_DESCRIPTION)
public class ConsumerSimulatorController {

    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static class TestResults {

        public List<ConsumerTypeRegistrationInfo> typeRegistrationInfoCallbacks =
            Collections.synchronizedList(new ArrayList<ConsumerTypeRegistrationInfo>());

        public void reset() {
            typeRegistrationInfoCallbacks.clear();
        }
    }

    @Getter
    private TestResults testResults = new TestResults();

    private static final String TYPE_STATUS_CALLBACK_URL = "/example-dataconsumer/info-type-status";

    public static String getTypeStatusCallbackUrl() {
        return TYPE_STATUS_CALLBACK_URL;
    }

    @PostMapping(path = TYPE_STATUS_CALLBACK_URL, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Callback for changed Information type registration status",
        description = "The primitive is implemented by the data consumer and is invoked when a Information type status has been changed. <br/>"
            + "Subscription are managed by primitives in '" + ConsumerConsts.CONSUMER_API_NAME + "'")
    @ApiResponses(
        value = { //
            @ApiResponse(
                responseCode = "200",
                description = "OK", //
                content = @Content(schema = @Schema(implementation = VoidResponse.class))) //
        })
    public ResponseEntity<Object> typeStatusCallback( //
        @RequestBody ConsumerTypeRegistrationInfo status) {
        logger.info("Job type registration status callback status: {}", status);
        this.testResults.typeRegistrationInfoCallbacks.add(status);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
