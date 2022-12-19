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

package org.oransc.ics.controllers.authorization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import org.oransc.ics.clients.AsyncRestClient;
import org.oransc.ics.clients.AsyncRestClientFactory;
import org.oransc.ics.clients.SecurityContext;
import org.oransc.ics.configuration.ApplicationConfig;
import org.oransc.ics.controllers.authorization.SubscriptionAuthRequest.Input.AccessType;
import org.oransc.ics.exceptions.ServiceException;
import org.oransc.ics.repository.InfoJob;
import org.oransc.ics.repository.InfoType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AuthorizationCheck {

    private final ApplicationConfig applicationConfig;
    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final AsyncRestClient restClient;
    private static Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public AuthorizationCheck(ApplicationConfig applicationConfig, SecurityContext securityContext) {

        this.applicationConfig = applicationConfig;
        AsyncRestClientFactory restClientFactory =
            new AsyncRestClientFactory(applicationConfig.getWebClientConfig(), securityContext);
        this.restClient = restClientFactory.createRestClientUseHttpProxy("");
    }

    public Mono<InfoJob> authorizeDataJob(Map<String, String> receivedHttpHeaders, InfoJob job, AccessType accessType) {
        return authorizeDataJob(receivedHttpHeaders, job.getType(), job.getJobData(), accessType) //
            .map(x -> job);
    }

    public Mono<InfoType> authorizeDataJob(Map<String, String> receivedHttpHeaders, InfoType type, Object jobDefinition,
        AccessType accessType) {
        if (this.applicationConfig.getAuthAgentUrl().isEmpty()) {
            return Mono.just(type);
        }

        String tkn = getAuthToken(receivedHttpHeaders);
        SubscriptionAuthRequest.Input input = SubscriptionAuthRequest.Input.builder() //
            .accessType(accessType) //
            .authToken(tkn) //
            .infoTypeId(type.getId()) //
            .jobDefinition(jobDefinition) //
            .build();

        SubscriptionAuthRequest req = SubscriptionAuthRequest.builder().input(input).build();

        String url = this.applicationConfig.getAuthAgentUrl();
        return this.restClient.post(url, gson.toJson(req)) //
            .doOnError(t -> logger.warn("Error returned from auth server: {}", t.getMessage())) //
            .onErrorResume(t -> Mono.just("")) //
            .flatMap(this::checkAuthResult) //
            .map(rsp -> type);

    }

    private String getAuthToken(Map<String, String> httpHeaders) {
        String tkn = httpHeaders.get("authorization");
        if (tkn == null) {
            logger.debug("No authorization token received in {}", httpHeaders);
            return "";
        }
        tkn = tkn.substring("Bearer ".length());
        return tkn;
    }

    private Mono<String> checkAuthResult(String response) {
        logger.debug("Auth result: {}", response);
        try {
            AuthorizationResult res = gson.fromJson(response, AuthorizationResult.class);
            return res != null && res.isResult() ? Mono.just(response)
                : Mono.error(new ServiceException("Not authorized", HttpStatus.UNAUTHORIZED));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

}
