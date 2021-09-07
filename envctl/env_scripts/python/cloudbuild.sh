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


SERVICE_NAME="log-py-build-$(echo $ENVCTL_ID | head -c 10)"

destroy() {
  set +e
  # delete pubsub resources
  gcloud pubsub topics delete $SERVICE_NAME -q 2> /dev/null
  gcloud pubsub subscriptions delete $SERVICE_NAME-subscriber -q 2> /dev/null
  # delete service
  ACTIVE_JOBS=$(gcloud builds list | grep -e WORKING -e QUEUED -e PENDING | cut -d" " -f 1)
  for job in $ACTIVE_JOBS; do
    if [[ "$(gcloud builds describe $job)" == *"$SERVICE_NAME"* ]]; then
      # found active job
      gcloud builds cancel $job 2> /dev/null
    fi
  done
}

verify() {
  set +e
  ACTIVE_JOBS=$(gcloud builds list | grep -e WORKING -e QUEUED -e PENDING | cut -d" " -f 1)
  for job in $ACTIVE_JOBS; do
    if [[ "$(gcloud builds describe $job)" == *"$SERVICE_NAME"* ]]; then
      # found active job
      echo "TRUE"
      exit 0
    fi
  done
  # no active jobs found
  echo "FALSE"
  exit 1
}

deploy() {
  build_container
  # create pub/sub topic
  set +e
  gcloud pubsub topics create $SERVICE_NAME 2>/dev/null
  set -e
  # create cloud build config
  cat <<EOF > $TMP_DIR/cloudbuild.yaml
steps:
- name: '$GCR_PATH'
  dir: /app
  env:
  - "ENABLE_SUBSCRIBER=true"
  - "PUBSUB_TOPIC=$SERVICE_NAME"
  - "PYTHONUNBUFFERED=TRUE"
EOF
  # deploy
  gcloud builds submit --config $TMP_DIR/cloudbuild.yaml --timeout 30m --async
}

filter-string() {
  echo "resource.type=\"build\""
}

