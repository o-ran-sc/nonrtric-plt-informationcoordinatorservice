#
# ============LICENSE_START=======================================================
#  Copyright (C) 2020 Nordix Foundation.
# ================================================================================
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# ============LICENSE_END=========================================================
#
FROM openjdk:17-jdk as jre-build

RUN $JAVA_HOME/bin/jlink \
--verbose \
--add-modules ALL-MODULE-PATH \
--strip-debug \
--no-man-pages \
--no-header-files \
--compress=2 \
--output /customjre

# Use debian base image (same as openjdk uses)
FROM debian:11-slim

ENV JAVA_HOME=/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

#copy JRE from the base image
COPY --from=jre-build /customjre $JAVA_HOME

ARG JAR

WORKDIR /opt/app/information-coordinator-service
RUN mkdir -p /var/log/information-coordinator-service
RUN mkdir -p /opt/app/information-coordinator-service/etc/cert/
RUN mkdir -p /var/information-coordinator-service

EXPOSE 8083 8434

ADD /config/application.yaml /opt/app/information-coordinator-service/config/application.yaml
ADD target/${JAR} /opt/app/information-coordinator-service/information-coordinator-service.jar
ADD /config/keystore.jks /opt/app/information-coordinator-service/etc/cert/keystore.jks
ADD /config/truststore.jks /opt/app/information-coordinator-service/etc/cert/truststore.jks

ARG user=nonrtric
ARG group=nonrtric

RUN groupadd $user && \
    useradd -r -g $group $user
RUN chown -R $user:$group /opt/app/information-coordinator-service
RUN chown -R $user:$group /var/log/information-coordinator-service
RUN chown -R $user:$group /var/information-coordinator-service

USER ${user}

CMD ["/jre/bin/java", "-jar", "/opt/app/information-coordinator-service/information-coordinator-service.jar"]




