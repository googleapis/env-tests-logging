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

import google.cloud.logging
from google.cloud.logging_v2.resource import Resource

from ..common.common import Common
from ..common.python import CommonPython


class TestCloudRun(Common, CommonPython, unittest.TestCase):

    environment = "cloudrun"
    language = "python"

    monitored_resource_name = "cloud_run_revision"
    monitored_resource_labels = [
        "project_id",
        "service_name",
        "revision_name",
        "location",
        "configuration_name",
    ]
