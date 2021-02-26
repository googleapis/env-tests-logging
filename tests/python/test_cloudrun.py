# Copyright 2016 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import logging
import unittest
import inspect
import uuid
from time import sleep

import google.cloud.logging
from google.cloud.logging_v2.resource import Resource
from test_utils.retry import RetryErrors

from ..common.common import Common
from ..common.common import LogsNotFound


class TestCloudRun(Common, unittest.TestCase):

    environment = "cloudrun"
    language = "python"

    @RetryErrors(exception=LogsNotFound)
    def test_monitored_resource(self):
        log_text = f"{inspect.currentframe().f_code.co_name}: {uuid.uuid1()}"
        self._trigger("pylogging", log_text=log_text)
        # give the command time to be received
        sleep(30)
        filter_str = self._add_time_condition_to_filter(log_text)
        # retrieve resulting logs
        log_list = self._get_logs(filter_str)

        found_resource = None
        for log in log_list:
            message = (
                log.payload.get("message", None)
                if isinstance(log.payload, dict)
                else str(log.payload)
            )
            if message and log_text in message:
                found_resource = log.resource
        self.assertEqual(found_resource.type, "cloud_run_revision")
        self.assertIsNotNone(found_resource.labels["project_id"])
        self.assertIsNotNone(found_resource.labels["service_name"])
        self.assertIsNotNone(found_resource.labels["revision_name"])
        self.assertIsNotNone(found_resource.labels["location"])
        self.assertIsNotNone(found_resource.labels["configuration_name"])
