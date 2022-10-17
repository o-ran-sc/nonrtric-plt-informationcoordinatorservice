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
import org.oransc.ics.datastore.DataStore;
import org.oransc.ics.datastore.FileStore;
import org.oransc.ics.datastore.S3ObjectStore;
import org.oransc.ics.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;

/**
 * Dynamic representation of all Information Types in the system.
 */
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class InfoTypes {
    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Map<String, InfoType> allInfoTypes = new HashMap<>();
    private final ApplicationConfig config;
    private final Gson gson;
    private final DataStore dataStore;

    public InfoTypes(ApplicationConfig config) {
        this.config = config;
        GsonBuilder gsonBuilder = new GsonBuilder();
        ServiceLoader.load(TypeAdapterFactory.class).forEach(gsonBuilder::registerTypeAdapterFactory);
        this.gson = gsonBuilder.create();

        this.dataStore =
            config.isS3Enabled() ? new S3ObjectStore(config, "infotypes") : new FileStore(config, "infotypes");
        this.dataStore.create().subscribe();
    }

    public synchronized Flux<InfoType> restoreTypesFromDatabase() {
        return dataStore.listObjects("") //
            .flatMap(dataStore::readObject) //
            .map(this::toInfoType) //
            .filter(Objects::nonNull) //
            .doOnNext(type -> allInfoTypes.put(type.getId(), type)) //
            .doOnError(t -> logger.error("Could not restoore types from S3, reason: {}", t.getMessage()));
    }

    private InfoType toInfoType(byte[] bytes) {
        String json = new String(bytes);
        return gson.fromJson(json, InfoType.class);
    }

    public synchronized void put(InfoType type) {
        allInfoTypes.put(type.getId(), type);
        storeInFile(type);
    }

    public synchronized Collection<InfoType> getAllInfoTypes() {
        return new Vector<>(allInfoTypes.values());
    }

    public synchronized InfoType getType(String id) throws ServiceException {
        InfoType type = allInfoTypes.get(id);
        if (type == null) {
            throw new ServiceException("Information type not found: " + id, HttpStatus.NOT_FOUND);
        }
        return type;
    }

    public synchronized InfoType get(String id) {
        return allInfoTypes.get(id);
    }

    public synchronized void remove(InfoType type) {
        allInfoTypes.remove(type.getId());
        dataStore.deleteObject(getPath(type)).block();
    }

    public synchronized int size() {
        return allInfoTypes.size();
    }

    public synchronized void clear() {
        this.allInfoTypes.clear();
        dataStore.delete().flatMap(s -> dataStore.create()).block();
    }

    public synchronized InfoType getCompatibleType(String typeId) throws ServiceException {
        InfoType res = this.get(typeId);
        if (res != null) {
            return res;
        }

        Collection<InfoType> compatibleTypes =
            InfoType.getCompatibleTypes(this.getAllInfoTypes(), InfoType.TypeId.ofString(typeId));
        if (compatibleTypes.isEmpty()) {
            throw new ServiceException("Information type not found: " + typeId, HttpStatus.NOT_FOUND);
        }
        return compatibleTypes.iterator().next();
    }

    private void storeInFile(InfoType type) {
        String json = gson.toJson(type);
        byte[] bytes = json.getBytes();
        this.dataStore.writeObject(this.getPath(type), bytes)
            .doOnError(t -> logger.error("Could not store infotype in S3, reason: {}", t.getMessage())) //
            .subscribe();
    }

    private String getPath(InfoType type) {
        return type.getId();
    }
}
