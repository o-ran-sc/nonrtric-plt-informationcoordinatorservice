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

package org.oransc.ics.controllers.r1producer;

import org.oransc.ics.controllers.r1consumer.ConsumerConsts;

public class ProducerConsts {
    public static final String PRODUCER_API_NAME = "Data producer (registration)";
    public static final String API_ROOT = "/data-producer/v1";
    public static final String PRODUCER_API_DESCRIPTION = "API for data producers";

    public static final String PRODUCER_API_CALLBACKS_NAME = "Data producer (callbacks)";
    public static final String PRODUCER_API_CALLBACKS_DESCRIPTION = "API implemented by data producers";

    public static final String INFO_TYPE_ID_PARAM = ConsumerConsts.INFO_TYPE_ID_PARAM;
    public static final String INFO_TYPE_ID_PATH = ConsumerConsts.INFO_TYPE_ID_PATH;

    public static final String INFO_PRODUCER_ID_PATH = "infoProducerId";

    public static final String DELETE_INFO_TYPE_DESCRPTION = "Existing jobs of the type will be automatically deleted.";

    private ProducerConsts() {
    }

}
