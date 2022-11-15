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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Schema(name = "subscription_authorization", description = "Authorization request for subscription requests")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
public class SubscriptionAuthRequest {

    @Schema(name = "input", description = "input")
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @ToString
    public static class Input {

        @Schema(name = "acces_type", description = "Access type")
        public enum AccessType {
            READ, WRITE
        }

        @Schema(name = "access_type", description = "Access type", required = true)
        @JsonProperty(value = "access_type", required = true)
        @SerializedName("access_type")
        private AccessType accessType;

        @Schema(name = "info_type_id", description = "Information type identifier", required = true)
        @SerializedName("info_type_id")
        @JsonProperty(value = "info_type_id", required = true)
        private String infoTypeId;

        @Schema(name = "job_definition", description = "Information type specific job data", required = true)
        @SerializedName("job_definition")
        @JsonProperty(value = "job_definition", required = true)
        private Object jobDefinition;

        @Schema(name = "auth_token", description = "Authorization token", required = true)
        @SerializedName("auth_token")
        @JsonProperty(value = "auth_token", required = true)
        private String authToken;

    }

    @Schema(name = "input", description = "Input", required = true)
    @JsonProperty(value = "input", required = true)
    @SerializedName("input")
    private Input input;

}
