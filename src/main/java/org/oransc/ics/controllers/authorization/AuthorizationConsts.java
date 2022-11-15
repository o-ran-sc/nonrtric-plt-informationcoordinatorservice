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

public class AuthorizationConsts {

    public static final String AUTH_API_NAME = "Authorization API";
    public static final String AUTH_API_DESCRIPTION =
        "API used for authorization of information job access (this is provided by an authorization producer such as OPA)";

    public static final String GRANT_ACCESS_SUMMARY = "Request for access authorization.";
    public static final String GRANT_ACCESS_DESCRIPTION = "The authorization function decides if access is granted.";

    private AuthorizationConsts() {
    }

}
