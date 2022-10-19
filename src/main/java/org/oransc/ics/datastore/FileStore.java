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

package org.oransc.ics.datastore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.oransc.ics.configuration.ApplicationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileSystemUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class FileStore implements DataStore {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    ApplicationConfig applicationConfig;
    private final String location;

    public FileStore(ApplicationConfig applicationConfig, String location) {
        this.applicationConfig = applicationConfig;
        this.location = location;
    }

    @Override
    public Flux<String> listObjects(String prefix) {
        Path root = Path.of(path().toString(), prefix);
        if (!root.toFile().exists()) {
            root = root.getParent();
        }

        logger.debug("Listing files in: {}", root);

        List<String> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root, Integer.MAX_VALUE)) {

            stream.forEach(path -> filterListFiles(path, prefix, result));

            return Flux.fromIterable(result);
        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    private void filterListFiles(Path path, String prefix, List<String> result) {
        if (path.toFile().isFile() && externalName(path).startsWith(prefix)) {
            result.add(externalName(path));
        } else {
            logger.debug("Ignoring file/directory {}, prefix: {}", path, prefix);
        }
    }

    private String externalName(Path path) {
        String fullName = path.toString();
        String externalName = fullName.substring(path().toString().length());
        if (externalName.startsWith("/")) {
            externalName = externalName.substring(1);
        }
        return externalName;
    }

    @Override
    public Mono<byte[]> readObject(String fileName) {
        try {
            byte[] contents = Files.readAllBytes(path(fileName));
            return Mono.just(contents);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Boolean> deleteObject(String name) {
        try {
            Files.delete(path(name));
            return Mono.just(true);
        } catch (Exception e) {
            return Mono.just(false);
        }
    }

    @Override
    public Mono<String> createDataStore() {
        try {
            Files.createDirectories(path());
        } catch (IOException e) {
            logger.error("Could not create directory: {}, reason: {}", path(), e.getMessage());
        }
        return Mono.just("OK");
    }

    private Path path(String name) {
        return Path.of(path().toString(), name);
    }

    private Path path() {
        return Path.of(applicationConfig.getVardataDirectory(), "database", this.location);
    }

    @Override
    public Flux<String> deleteAllData() {
        return listObjects("") //
            .flatMap(this::deleteObject) //
            .map(o -> "OK");
    }

    @Override
    public Mono<byte[]> writeObject(String fileName, byte[] fileData) {
        try {
            File outputFile = path(fileName).toFile();
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                outputStream.write(fileData);
            }
        } catch (IOException e) {
            logger.debug("Could not write file: {}, reason; {}", path(fileName), e.getMessage());
        }
        return Mono.just(fileData);
    }

    @Override
    public Mono<String> deleteBucket() {
        try {
            FileSystemUtils.deleteRecursively(path(""));
        } catch (IOException e) {
            logger.warn("Could not delete: {}, reason: {}", path(""), e.getMessage());
        }
        return Mono.just(path("").toString());
    }

}
