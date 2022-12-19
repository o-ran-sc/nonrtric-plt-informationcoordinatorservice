/*-
 * ========================LICENSE_START=================================
 * O-RAN-SC
 * %%
 * Copyright (C) 2019 Nordix Foundation
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

package org.oransc.ics.repository;

import java.lang.invoke.MethodHandles;
import java.time.Instant;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the dynamic information about a information job
 */
@Builder
public class InfoJob {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Getter
    private final String id;

    @Getter
    private final InfoType type;

    @Getter
    private String owner;

    @Getter
    private Object jobData;

    @Getter
    private String targetUrl;

    @Getter
    private String jobStatusUrl;

    @Getter
    @Builder.Default
    private String lastUpdated = Instant.now().toString();

    @Getter
    @Builder.Default
    private boolean isLastStatusReportedEnabled = true;

    @Getter
    @Builder
    @EqualsAndHashCode
    public static class PersistentData {
        private String id;
        private String typeId;
        private String owner;
        private Object jobData;
        private String targetUrl;
        private String jobStatusUrl;
        private String lastUpdated;
    }

    public void setLastReportedStatus(boolean isEnabled) {
        this.isLastStatusReportedEnabled = isEnabled;
        logger.debug("Job status id: {}, enabled: {}", this.isLastStatusReportedEnabled, isEnabled);
    }

    public PersistentData getPersistentData() {
        return PersistentData.builder() //
            .id(id) //
            .jobData(jobData) //
            .jobStatusUrl(jobStatusUrl) //
            .owner(owner) //
            .targetUrl(targetUrl) //
            .typeId(type.getId()) //
            .lastUpdated(lastUpdated) //
            .build();
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof InfoJob) {
            return this.id.equals(((InfoJob) o).id);
        }
        return this.id.equals(o);
    }

    public synchronized void update(InfoJob job) {
        this.jobData = job.jobData;
        this.owner = job.owner;
        this.jobStatusUrl = job.jobStatusUrl;
        this.targetUrl = job.targetUrl;
    }

}
