# Copyright 2021 Google LLC
#
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

FROM maven:3-jdk-11-slim AS build-env

# Create and change to the app directory.
WORKDIR /app

# Copy test script and local dependencies to the container image.
COPY pom.xml ./

# Install test app's dependencies.
RUN mvn verify --fail-never

COPY . ./

RUN mvn -Dmaven.test.skip=true package

# Build runtime image
FROM openjdk:11-jre-slim

# Copy compiled files over
COPY --from=build-env /app/target/ /app/

# Environment variable denoting whether to run an app server
ENV RUNSERVER=1

# Run the web service on container startup.
CMD [ "java", "-jar", "/app/java-logging-env-tests-1.0.0.jar" ]
