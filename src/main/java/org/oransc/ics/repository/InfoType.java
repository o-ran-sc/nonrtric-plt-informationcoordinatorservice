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
import java.util.List;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import org.oransc.ics.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

@ToString
public class InfoType {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Builder
    @Getter
    @EqualsAndHashCode
    public static class PersistentInfo {
        @Getter
        private String id;

        @Getter
        private Object jobDataSchema;

        @Getter
        private Object typeSpecificInfo;
    }

    @Getter
    @ToString
    public static class Version {
        public final int major;
        public final int minor;
        public final int patch;

        public Version(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        public static Version ofString(String version) throws ServiceException {
            String[] versionTokenized = version.split("\\.");
            if (versionTokenized.length != 3) {
                throw new ServiceException("Version must contain major.minor.patch code: " + version,
                    HttpStatus.BAD_REQUEST);
            }

            try {
                return new Version( //
                    Integer.parseInt(versionTokenized[0]), //
                    Integer.parseInt(versionTokenized[1]), //
                    Integer.parseInt(versionTokenized[2]) //
                );
            } catch (Exception e) {
                throw new ServiceException("Syntax error in " + version, HttpStatus.BAD_REQUEST);
            }
        }

        public int compareTo(Version other) {
            if (major != other.major)
                return major - other.major;
            if (minor != other.minor)
                return minor - other.minor;
            return patch - other.patch;
        }

        public boolean isCompatibleWith(Version other) {
            return (major == other.major && minor >= other.minor);
        }
    }

    @ToString
    @Getter
    public static class TypeId {
        private final String name;
        private final String version;

        public TypeId(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public static TypeId ofString(String typeId) {
            StringBuilder name = new StringBuilder();
            String version = "";
            String[] tokens = typeId.split("_");

            if (tokens.length >= 2) {
                version = tokens[tokens.length - 1]; // Last token
                for (int i = 0; i < tokens.length - 1; ++i) {
                    if (i != 0) {
                        name.append("_");
                    }
                    name.append(tokens[i]); // All other tokens
                }
                return new TypeId(name.toString(), version);
            } else {
                return new TypeId(typeId, "");
            }
        }
    }

    public static Collection<InfoType> filterCompatibleWithVersion(Collection<InfoType> types, TypeId requestedTypeId) {
        List<InfoType> result = new ArrayList<>();
        try {
            InfoType.Version requestedVersion = InfoType.Version.ofString(requestedTypeId.version);
            for (InfoType type : types) {
                TypeId typeId = type.getTypeId();
                if (requestedTypeId.getName().equals(typeId.getName())
                    && InfoType.Version.ofString(typeId.getVersion()).isCompatibleWith(requestedVersion)) {
                    result.add(type);
                }
            }
            result.sort((left, right) -> left.getVersion().compareTo(right.getVersion()));

        } catch (ServiceException e) {
            logger.warn("Failed to find compatible version with: {}, reason: {}", requestedTypeId, e.getMessage());
        }
        return result;
    }

    @Getter
    private final String id;

    @Getter
    private final Object jobDataSchema;

    @Getter
    private final Object typeSpecificInfo;

    public InfoType(String id, Object jobDataSchema, Object typeSpecificInfo) {
        this.id = id;
        this.jobDataSchema = jobDataSchema;
        this.typeSpecificInfo = typeSpecificInfo;
    }

    public InfoType(PersistentInfo info) {
        this.id = info.getId();
        this.jobDataSchema = info.getJobDataSchema();
        this.typeSpecificInfo = info.getTypeSpecificInfo();
    }

    public PersistentInfo getPersistentInfo() {
        return PersistentInfo.builder() //
            .id(this.id) //
            .jobDataSchema(this.jobDataSchema)//
            .typeSpecificInfo(this.typeSpecificInfo) //
            .build();
    }

    public TypeId getTypeId() {
        return TypeId.ofString(getId());
    }

    public Version getVersion() {
        try {
            return Version.ofString(getTypeId().getVersion());
        } catch (ServiceException e) {
            logger.warn("Not possible to get version from info type ID {}, {}", getId(), e.getMessage());
            return new Version(0, 0, 0);
        }
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof InfoType) {
            return this.id.equals(((InfoType) o).id);
        }
        return this.id.equals(o);
    }

}
