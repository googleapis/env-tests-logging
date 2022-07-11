#!/bin/bash
# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e # exit on any failure
set -o pipefail # any step in pipe caused failure
set -u # undefined variables cause exit

pushd library
# build current version of java-logging library
mvn verify --fail-never
mvn -Dmaven.test.skip=true package
COPY target/*.jar ./java-logging.jar

# install the built library into local repo
mvn install:install-file \
    -Dfile=./java-logging.jar \
    -DgroupId=com.google.cloud.local \
    -DartifactId=google-cloud-logging \
    -Dversion=0.0.1 \
    -Dpackaging=jar \
    -DgeneratePom=true
popd

# build uber jar with functions.TestFunctions test
pushd cf
mvn -Dmaven.test.skip=true package
popd
