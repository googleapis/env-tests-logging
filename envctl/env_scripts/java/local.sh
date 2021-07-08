#!/bin/bash
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

set -e # exit on any failure
set -o pipefail # any step in pipe caused failure
set -u # undefined variables cause exit

SERVICE_NAME="log-java-local-$(echo $ENVCTL_ID | head -c 8)"
SA_NAME=$SERVICE_NAME-invoker
LIBRARY_NAME="java-logging"

destroy() {
  set +e
  # delete pubsub resources
  gcloud pubsub topics delete $SERVICE_NAME -q  2> /dev/null
  gcloud pubsub subscriptions delete $SERVICE_NAME-subscriber -q  2> /dev/null
  # stop container
  docker stop $SERVICE_NAME 2> /dev/null
  set -e
}

verify() {
  set +e
  if [ "$( docker container inspect -f '{{.State.Status}}' $SERVICE_NAME )" == "running" ]; then
     echo "TRUE"
     exit 0
   else
     echo "FALSE"
     exit 1
  fi
  set -e
}

build_java_container() {
  export GCR_PATH=gcr.io/$PROJECT_ID/logging:$SERVICE_NAME
  # copy super-repo into deployable dir
  _env_tests_relative_path=${REPO_ROOT#"$SUPERREPO_ROOT/"}
  _deployable_dir=$REPO_ROOT/deployable/$LANGUAGE

  # copy over local copy of library
  pushd $SUPERREPO_ROOT
    tar -cvf $_deployable_dir/lib.tar --exclude target --exclude env-tests-logging --exclude test --exclude system-test --exclude .nox --exclude samples --exclude docs .
  popd
  mkdir -p $_deployable_dir/$LIBRARY_NAME
  tar -xvf $_deployable_dir/lib.tar --directory $_deployable_dir/$LIBRARY_NAME

  # build container
  docker build -t $GCR_PATH $_deployable_dir
}

deploy() {
  build_java_container
  FLAGS=${@:-"-d"}
  docker run --rm --name $SERVICE_NAME -e RUNSERVER=false -e ENABLE_SUBSCRIBER=true -e PUBSUB_TOPIC=$SERVICE_NAME \
    # todo: link in local service account for authentication
    #-v ~/service-account.json:/service-account.json -e GOOGLE_APPLICATION_CREDENTIALS=/service-account.json \
    $FLAGS $GCR_PATH
}

filter-string() {
  echo "resource.type=\"global\""
}
