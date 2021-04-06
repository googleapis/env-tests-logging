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

import logging
import unittest
import inspect

import google.cloud.logging

from ..common.common import Common


class CommonPython:
    def pylogging_test_receive_log(self):
        log_text = f"{inspect.currentframe().f_code.co_name}"
        log_list = self.trigger_and_retrieve(log_text, function="pylogging")

        found_log = None
        for log in log_list:
            message = (
                log.payload.get("message", None)
                if isinstance(log.payload, dict)
                else str(log.payload)
            )
            if message and log_text in message:
                found_log = log
        self.assertIsNotNone(found_log, "expected log text not found")

    def test_monitored_resource_pylogging(self):
        log_text = f"{inspect.currentframe().f_code.co_name}"
        log_list = self.trigger_and_retrieve(log_text, function="pylogging")
        found_resource = log_list[-1].resource

        self.assertIsNotNone(self.monitored_resource_name)
        self.assertIsNotNone(self.monitored_resource_labels)

        self.assertEqual(found_resource.type, self.monitored_resource_name)
        for label in self.monitored_resource_labels:
            self.assertTrue(
                found_resource.labels[label], f"resource.labels[{label}] is not set"
            )

    def test_severity_pylogging(self):
        severities = ["CRITICAL", "ERROR", "WARNING", "INFO", "DEBUG"]
        for severity in severities:
            log_text = f"{inspect.currentframe().f_code.co_name}"
            log_list = self.trigger_and_retrieve(
                log_text, function="pylogging", severity=severity
            )
            found_severity = log_list[-1].severity

            self.assertEqual(found_severity.lower(), severity.lower())

    def test_source_location_pylogging(self):
        log_text = f"{inspect.currentframe().f_code.co_name}"
        log_list = self.trigger_and_retrieve(log_text, function="pylogging")
        found_resource = log_list[-1].resource

        self.assertIsNotNone(found_resource.source_location)
        self.assertIsNotNone(found_resource.source_location['file'])
        self.assertIsNotNone(found_resource.source_location['function'])
        self.assertIsNotNone(found_resource.source_location['line'])
        self.assertEqual(found_resource.source_location['file'], "/workspace/snippets.py")
        self.assertEqual(found_resource.source_location['function'], "pylogging")
        self.assertTrue(int(found_resource.source_location['line']) > 0)

    def test_flask_http_request_pylogging(self):
        log_text = f"{inspect.currentframe().f_code.co_name}"

        expected_agent = "test-agent"
        exected_base_url = "http://test"
        expected_path = "/pylogging"
        expected_trace = "123"

        log_list = self.trigger_and_retrieve(log_text, function="pylogging_flask",
                path=expected_path, trace=expected_trace, base_url=exected_base_url, agent=expected_agent)
        found_resource = log_list[-1].resource

        print(found_resource.trace)
        print(found_resource.http_request)
        self.assertIsNotNone(found_resource.trace)
        self.assertIsNotNone(found_resource.httpRequest)
        self.assertIsNotNone(found_resource.httpRequest['requestMethod'])
        self.assertIsNotNone(found_resource.httpRequest['requestUrl'])
        self.assertIsNotNone(found_resource.httpRequest['userAgent'])
        self.assertIsNotNone(found_resource.httpRequest['protocol'])
        # self.assertEqual(found_resource.trace, expected_trace)
        self.assertEqual(found_resource.httpRequest['requestMethod'], 'GET')
        self.assertEqual(found_resource.httpRequest['requestUrl'], expected_base_url + expected_path)
        self.assertEqual(found_resource.httpRequest['userAgent'], expected_agent)
        self.assertEqual(found_resource.httpRequest['protocol'], 'HTTP/1.1')
