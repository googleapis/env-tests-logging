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

import google.cloud.logging
from google.cloud._helpers import UTC
from google.cloud.logging_v2.handlers.handlers import CloudLoggingHandler
from google.cloud.logging_v2.handlers.transports import SyncTransport
from google.cloud.logging_v2 import Client
from google.cloud.logging_v2.resource import Resource
from google.cloud.logging_v2 import entries
from google.cloud.logging_v2._helpers import LogSeverity

from time import sleep
from datetime import datetime
from datetime import timezone
from datetime import timedelta
import os
import sys
import uuid
import inspect

from test_utils.retry import RetryErrors
from grpc import RpcError

from .script_utils import ScriptRunner
from .script_utils import Command


class LogsNotFound(RuntimeError):
    """raised when filter returns no logs."""

    pass


class Common:
    _client = Client()
    # environment name and monitored resource values must be set by subclass
    environment = None
    monitored_resource_name = None
    monitored_resource_labels = None

    def _add_time_condition_to_filter(self, filter_str, timestamp=None):
        time_format = "%Y-%m-%dT%H:%M:%S.%f%z"
        if not timestamp:
            timestamp = datetime.now(timezone.utc) - timedelta(minutes=10)
        return f'"{filter_str}" AND timestamp > "{timestamp.strftime(time_format)}"'

    def _get_logs(self, filter_str=None):
        if not filter_str:
            _, filter_str, _ = self._script.run_command(Command.GetFilter)
        iterator = self._client.list_entries(filter_=filter_str)
        entries = list(iterator)
        if not entries:
            raise LogsNotFound
        return entries

    def _trigger(self, snippet, **kwargs):
        timestamp = datetime.now(timezone.utc)
        args_str = ",".join([f'{k}="{v}"' for k, v in kwargs.items()])
        self._script.run_command(Command.Trigger, [snippet, args_str])

    @RetryErrors(exception=(LogsNotFound, RpcError), delay=2, max_tries=2)
    def trigger_and_retrieve(
        self, log_text, snippet, append_uuid=True, max_tries=6, **kwargs
    ):
        if append_uuid:
            log_text = f"{log_text} {uuid.uuid1()}"
        self._trigger(snippet, log_text=log_text, **kwargs)
        sleep(2)
        filter_str = self._add_time_condition_to_filter(log_text)
        # give the command time to be received
        tries = 0
        while tries < max_tries:
            # retrieve resulting logs
            try:
                log_list = self._get_logs(filter_str)
                return log_list
            except (LogsNotFound, RpcError) as e:
                sleep(5)
                tries += 1
        # log not found
        raise LogsNotFound

    @classmethod
    def setUpClass(cls):
        if not cls.environment:
            raise NotImplementedError("environment not set by subclass")
        if not cls.language:
            raise NotImplementedError("language not set by subclass")
        cls._script = ScriptRunner(cls.environment, cls.language)
        # check if already setup
        status, _, _ = cls._script.run_command(Command.Verify)
        if status == 0:
            if os.getenv("NO_CLEAN"):
                # ready to go
                return
            else:
                # reset environment
                status, _, _ = cls._script.run_command(Command.Destroy)
                assert status == 0
        # deploy test code to GCE
        status, _, err = cls._script.run_command(Command.Deploy)
        if status != 0:
            print(err)
        # verify code is running
        status, _, err = cls._script.run_command(Command.Verify)
        if status != 0:
            print(err)
        assert status == 0

    @classmethod
    def tearDown_class(cls):
        # by default, destroy environment on each run
        # allow skipping deletion for development
        if not os.getenv("NO_CLEAN"):
            cls._script.run_command(Command.Destroy)

    def test_receive_log(self):
        log_text = f"{inspect.currentframe().f_code.co_name}"
        log_list = self.trigger_and_retrieve(log_text, "simplelog")

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

    def test_receive_unicode_log(self):
        log_text = f"{inspect.currentframe().f_code.co_name} 嗨 世界 😀"
        log_list = self.trigger_and_retrieve(log_text, "simplelog")

        found_log = None
        for log in log_list:
            message = (
                log.payload.get("message", None)
                if isinstance(log.payload, dict)
                else str(log.payload)
            )
            if message and log_text in message:
                found_log = log
        self.assertIsNotNone(found_log, "expected unicode log not found")

    def test_monitored_resource(self):
        if self.language not in ["nodejs", "go"]:
            # TODO: other languages to also support this test
            return True
        log_text = f"{inspect.currentframe().f_code.co_name}"
        log_list = self.trigger_and_retrieve(log_text, "simplelog")
        found_resource = log_list[-1].resource

        self.assertIsNotNone(self.monitored_resource_name)
        self.assertIsNotNone(self.monitored_resource_labels)

        self.assertEqual(found_resource.type, self.monitored_resource_name)
        for label in self.monitored_resource_labels:
            self.assertTrue(found_resource.labels[label],
                f'resource.labels[{label}] is not set')

    def test_request_log(self):
        if self.language not in ["nodejs"]:
            # TODO: other languages to also support this test
            return True
        log_text = f"{inspect.currentframe().f_code.co_name}"
        log_list = self.trigger_and_retrieve(log_text, "stdoutlog")
        found_request = log_list[-1].http_request
        # TODO(nicolezhu): remove hasattr check later.
        if hasattr(self, 'request_props'):
            for prop in self.request_props:
                self.assertTrue(found_request[prop],
                f'httpRequest[{prop}] is not set')

    def test_stdout_log(self):
        if self.language not in ["nodejs"]:
            # TODO: other languages to also support this test
            return True
        log_text = f"{inspect.currentframe().f_code.co_name}"
        log_list = self.trigger_and_retrieve(log_text, "stdoutlog")
        found = log_list[-1]

        # Agents lift fields inconsistently among envs, so check if is expected.
        if hasattr(self, 'stdout_log_name'):
           self.assertEqual(found.log_name, self.stdout_log_name)
        if hasattr(self, 'stdout_severity'):
            self.assertEqual(found.severity, self.stdout_severity)
        if hasattr(self, 'stdout_insert_id'):
            self.assertEqual(found.insert_id, self.stdout_insert_id)
        if hasattr(self, 'stdout_timestamp'):
            self.assertEqual(found.timestamp, self.stdout_timestamp)
        if hasattr(self, 'stdout_trace'):
            self.assertEqual(found.trace, self.stdout_trace)
        if hasattr(self, 'stdout_span_id'):
            self.assertEqual(found.span_id, self.span_id)
        if hasattr(self, 'stdout_trace_sampled'):
            self.assertEqual(found.severity, self.stdout_trace_sampled)
        if hasattr(self, 'stdout_labels'):
            for prop in self.stdout_labels:
                self.assertTrue(found.labels[prop],
                f'httpRequest[{prop}] is not set')
        if hasattr(self, 'stdout_resource_type'):
            self.assertEqual(found.resource.type, self.stdout_resource_type)
        if hasattr(self, 'stdout_resource_labels'):
            for prop in self.stdout_resource_labels:
                self.assertTrue(found.resource.labels[prop],
                f'httpRequest[{prop}] is not set')
        if hasattr(self, 'stdout_payload_props'):
            for prop in self.stdout_payload_props:
                self.assertTrue(found.payload[prop],
                f'httpRequest[{prop}] is not set')

    def test_severity(self):
        if self.language != "python":
            # to do: enable test for other languages
            return True
        log_text = f"{inspect.currentframe().f_code.co_name}"
        severities = [
            "EMERGENCY",
            "ALERT",
            "CRITICAL",
            "ERROR",
            "WARNING",
            "NOTICE",
            "INFO",
            "DEBUG",
        ]
        for severity in severities:
            log_list = self.trigger_and_retrieve(
                log_text, "simplelog", severity=severity
            )
            found_severity = log_list[-1].severity
            self.assertEqual(found_severity.lower(), severity.lower())
        # DEFAULT severity should result in empty field
        log_list = self.trigger_and_retrieve(log_text, "simplelog", severity="DEFAULT")
        self.assertIsNone(log_list[-1].severity)
