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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapterFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Vector;

import org.oransc.ics.configuration.ApplicationConfig;
import org.oransc.ics.controllers.r1producer.ProducerCallbacks;
import org.oransc.ics.datastore.DataStore;
import org.oransc.ics.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Dynamic representation of all existing Information Jobs.
 */
public class InfoJobs {
    private Map<String, InfoJob> allEiJobs = new HashMap<>();

    private MultiMap<String, InfoJob> jobsByType = new MultiMap<>();
    private MultiMap<String, InfoJob> jobsByOwner = new MultiMap<>();
    private final Gson gson;
    private final InfoTypes infoTypes;

    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ProducerCallbacks producerCallbacks;

    private final DataStore dataStore;

    public InfoJobs(ApplicationConfig config, InfoTypes infoTypes, ProducerCallbacks producerCallbacks) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        ServiceLoader.load(TypeAdapterFactory.class).forEach(gsonBuilder::registerTypeAdapterFactory);
        this.gson = gsonBuilder.create();
        this.producerCallbacks = producerCallbacks;
        this.infoTypes = infoTypes;
        this.dataStore = DataStore.create(config, "infojobs");
        this.dataStore.createDataStore().subscribe();
    }

    public synchronized Flux<InfoJob> restoreJobsFromDatabase() {
        return dataStore.listObjects("") //
            .flatMap(dataStore::readObject) //
            .map(this::toPersistentData) //
            .map(this::toInfoJob) //
            .filter(Objects::nonNull) //
            .doOnNext(this::doPut) //
            .doOnError(t -> logger.error("Could not restore jobs from datastore, reason: {}", t.getMessage()));
    }

    private InfoJob.PersistentData toPersistentData(byte[] bytes) {
        String json = new String(bytes);
        return gson.fromJson(json, InfoJob.PersistentData.class);
    }

    private InfoJob toInfoJob(InfoJob.PersistentData data) {
        InfoType type;
        try {
            type = infoTypes.getType(data.getTypeId());
            return InfoJob.builder() //
                .id(data.getId()) //
                .type(type) //
                .owner(data.getOwner()) //
                .jobData(data.getJobData()) //
                .targetUrl(data.getTargetUrl()) //
                .jobStatusUrl(data.getJobStatusUrl()) //
                .lastUpdated(data.getLastUpdated()) //
                .build();
        } catch (ServiceException e) {
            logger.error("Error restoring info job: {}, reason: {}", data.getId(), e.getMessage());
        }
        return null;
    }

    public synchronized void put(InfoJob job) {
        this.doPut(job);
        storeJob(job);
    }

    public synchronized Collection<InfoJob> getJobs() {
        return new Vector<>(allEiJobs.values());
    }

    public synchronized Mono<InfoJob> getJobMono(String id) {
        InfoJob job = allEiJobs.get(id);
        if (job == null) {
            return Mono.error(new ServiceException("Could not find Information job: " + id, HttpStatus.NOT_FOUND));
        }
        return Mono.just(job);
    }

    public synchronized InfoJob getJob(String id) throws ServiceException {
        InfoJob ric = allEiJobs.get(id);
        if (ric == null) {
            throw new ServiceException("Could not find Information job: " + id, HttpStatus.NOT_FOUND);
        }
        return ric;
    }

    public synchronized Collection<InfoJob> getJobsForType(String typeId) {
        return jobsByType.get(typeId);
    }

    public synchronized Collection<InfoJob> getJobsForType(InfoType type) {
        return jobsByType.get(type.getId());
    }

    public synchronized Collection<InfoJob> getJobsForOwner(String owner) {
        return jobsByOwner.get(owner);
    }

    public synchronized InfoJob get(String id) {
        return allEiJobs.get(id);
    }

    public synchronized InfoJob remove(String id, InfoProducers infoProducers) {
        InfoJob job = allEiJobs.get(id);
        if (job != null) {
            remove(job, infoProducers);
        }
        return job;
    }

    public synchronized void remove(InfoJob job, InfoProducers infoProducers) {
        this.allEiJobs.remove(job.getId());
        jobsByType.remove(job.getType().getId(), job);
        jobsByOwner.remove(job.getOwner(), job);

        this.dataStore.deleteObject(getPath(job)).subscribe();

        this.producerCallbacks.stopInfoJob(job, infoProducers);

    }

    public synchronized int size() {
        return allEiJobs.size();
    }

    public synchronized void clear() {
        this.allEiJobs.clear();
        this.jobsByType.clear();
        jobsByOwner.clear();

        dataStore.deleteAllData().flatMap(s -> dataStore.createDataStore()).blockLast();
    }

    private void doPut(InfoJob job) {
        InfoJob prevDefinition = this.get(job.getId());
        if (prevDefinition == null) {
            allEiJobs.put(job.getId(), job);
            jobsByType.put(job.getType().getId(), job);
            jobsByOwner.put(job.getOwner(), job);
        } else {
            prevDefinition.update(job);
        }
    }

    private void storeJob(InfoJob job) {
        String json = gson.toJson(job.getPersistentData());
        byte[] bytes = json.getBytes();
        this.dataStore.writeObject(this.getPath(job), bytes) //
            .doOnError(t -> logger.error("Could not store job in datastore, reason: {}", t.getMessage())) //
            .subscribe();
    }

    private String getPath(InfoJob job) {
        return job.getId();
    }

}
