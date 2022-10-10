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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.oransc.ics.controllers.a1e.A1eCallbacks;
import org.oransc.ics.controllers.r1producer.ProducerCallbacks;
import org.oransc.ics.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Dynamic representation of all EiProducers.
 */
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
@Component
public class InfoProducers {
    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Map<String, InfoProducer> allInfoProducers = new HashMap<>();

    @Autowired
    private ProducerCallbacks producerCallbacks;

    @Autowired
    private A1eCallbacks consumerCallbacks;

    @Autowired
    private InfoJobs infoJobs;

    @Autowired
    private InfoTypes infoTypes;

    public InfoProducer registerProducer(InfoProducerRegistrationInfo producerInfo) {
        final String producerId = producerInfo.getId();
        InfoProducer previousDefinition = this.get(producerId);

        InfoProducer producer = createProducer(producerInfo);
        allInfoProducers.put(producer.getId(), producer);

        Collection<InfoType> previousTypes =
            previousDefinition != null ? previousDefinition.getInfoTypes() : new ArrayList<>();

        producerCallbacks.startInfoJobs(producer, this.infoJobs) //
            .collectList() //
            .flatMapMany(list -> consumerCallbacks.notifyJobStatus(producer.getInfoTypes(), this)) //
            .collectList() //
            .flatMapMany(list -> consumerCallbacks.notifyJobStatus(previousTypes, this)) //
            .subscribe();

        return producer;
    }

    private InfoProducer createProducer(InfoProducerRegistrationInfo producerInfo) {
        return new InfoProducer(producerInfo.getId(), producerInfo.getSupportedTypes(),
            producerInfo.getJobCallbackUrl(), producerInfo.getProducerSupervisionCallbackUrl());
    }

    public synchronized Collection<InfoProducer> getAllProducers() {
        return new Vector<>(allInfoProducers.values());
    }

    public synchronized InfoProducer getProducer(String id) throws ServiceException {
        InfoProducer p = allInfoProducers.get(id);
        if (p == null) {
            throw new ServiceException("Could not find Information Producer: " + id, HttpStatus.NOT_FOUND);
        }
        return p;
    }

    public synchronized InfoProducer get(String id) {
        return allInfoProducers.get(id);
    }

    public synchronized int size() {
        return allInfoProducers.size();
    }

    public synchronized void clear() {
        this.allInfoProducers.clear();
    }

    public void deregisterProducer(InfoProducer producer) {
        allInfoProducers.remove(producer.getId());
        this.consumerCallbacks.notifyJobStatus(producer.getInfoTypes(), this) //
            .subscribe();

    }

    public synchronized Collection<InfoProducer> getProducersSupportingType(InfoType type) {
        InfoType.TypeId id = type.getTypeId();
        Collection<InfoProducer> result = new ArrayList<>();
        for (InfoProducer producer : this.allInfoProducers.values()) {
            if (producer.getInfoTypes().contains(type)
                || !InfoType.filterCompatibleWithVersion(producer.getInfoTypes(), id).isEmpty()) {
                result.add(producer);
            }
        }

        return result;
    }

    public synchronized Collection<String> getProducerIdsForType(InfoType type) {
        Collection<String> producerIds = new ArrayList<>();
        for (InfoProducer p : this.getProducersSupportingType(type)) {
            producerIds.add(p.getId());
        }
        return producerIds;
    }

    public synchronized boolean isJobEnabled(InfoJob job) {
        InfoType type;
        try {
            type = this.infoTypes.getType(job.getTypeId());

            for (InfoProducer producer : this.getProducersSupportingType(type)) {
                if (producer.isJobEnabled(job)) {
                    return true;
                }
            }
        } catch (ServiceException e) {
            logger.error("Unexpected execption: {}", e.getMessage());
        }
        return false;
    }

}
