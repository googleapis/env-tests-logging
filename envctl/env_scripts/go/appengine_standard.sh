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

SERVICE_NAME="logging-go-standard-$(echo $ENVCTL_ID | head -c 8)"\

destroy() {
  set +e
  # delete pubsub resources
  gcloud pubsub topics delete $SERVICE_NAME -q 2> /dev/null
  gcloud pubsub subscriptions delete $SERVICE_NAME-subscriber -q 2> /dev/null
  # delete service
  gcloud app services delete $SERVICE_NAME -q 2> /dev/null
  set -e
}

verify() {
  set +e
  gcloud app services describe $SERVICE_NAME -q > /dev/null 2> /dev/null
  if [[ $? == 0 ]]; then
     echo "TRUE"
     exit 0
   else
     echo "FALSE"
     exit 1
  fi
  set -e
}

deploy() {
  # create pub/sub topic
  set +e
  gcloud pubsub topics create $SERVICE_NAME 2>/dev/null
  set -ex
  # set up deployment directory
  # Copy over local copy of library to use as dependency
  _deployable_dir=$REPO_ROOT/deployable/$LANGUAGE
  pushd $SUPERREPO_ROOT/logging
    tar -cvf $_deployable_dir/lib.tar \
      --exclude logging --exclude */env-tests-logging  \
      --exclude .nox --exclude docs --exclude __pycache__ .
  popd
  mkdir -p $_deployable_dir/logging
  tar -xvf $_deployable_dir/lib.tar --directory $_deployable_dir/logging

  # Create vendor folder based on local dependency
  pushd $REPO_ROOT/deployable/go
    go mod tidy
    go mod vendor
  popd

  # move code into a temp directory used to deploy the cloud function
  cp -rf $REPO_ROOT/deployable/go/vendor $TMP_DIR/vendor

  # clean up vendor folder
  pushd $REPO_ROOT/deployable/go
    rm -rf vendor/
  popd

  # manual_scaling allows 1 instance to continuously run regardless of the load level.
  cat <<EOF > $TMP_DIR/app.yaml
    runtime: go119
    service: $SERVICE_NAME
    manual_scaling:
      instances: 1
    env_variables:
      ENABLE_SUBSCRIBER: "true"
      PUBSUB_TOPIC: $SERVICE_NAME
EOF
  # deploy
  pushd $TMP_DIR
    gcloud app deploy -q
  popd
  # wait for the pub/sub subscriber to start
  NUM_SUBSCRIBERS=0
  TRIES=0
  while [[ "${NUM_SUBSCRIBERS}" -lt 1 && "${TRIES}" -lt 10 ]]; do
    sleep 30
    NUM_SUBSCRIBERS=$(gcloud pubsub topics list-subscriptions $SERVICE_NAME 2> /dev/null | wc -l)
    TRIES=$((TRIES + 1))
  done
}

filter-string() {
  echo "resource.type=\"gae_app\" AND resource.labels.module_id=\"$SERVICE_NAME\""
}
