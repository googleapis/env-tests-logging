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

try:
    import google.cloud.logging
except ImportError:
    # import at runtime for GAE environments
    import pip
    import importlib
    import site

    pip.main(["install", "-e", "./python-logging"])
    importlib.reload(site)
    import google.cloud.logging


def simple_log(log_name=None, log_text="simple_log", **kwargs):
    client = google.cloud.logging.Client()
    logger = client.logger(log_name)
    logger.log_text(log_text)


def pylogging(log_text="pylogging", severity="warning", **kwargs):
    # allowed severity: debug, info, warning, error, critical
    if severity == "debug":
        logging.debug(log_text)
    elif severity == "info":
        logging.info(log_text)
    elif severity == "warning":
        logging.warning(log_text)
    elif severity == "error":
        logging.error(log_text)
    else:
        logging.critical(log_text)
