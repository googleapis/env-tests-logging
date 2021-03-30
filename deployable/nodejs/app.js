// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

const {Logging} = require('@google-cloud/logging');
const logging = new Logging();

/**
 * Only triggers for GCP services that require a running app server.
 * For instance, Cloud Functions does not execute this block.
 * RUNSERVER env var is set in the Dockerfile.
 */
if (process.env.RUNSERVER) {

  const express = require('express');
  const bodyParser = require('body-parser');
  const app = express();

  app.use(bodyParser.json());

  /**
   * Cloud Run to be triggered by Pub/Sub.
   * TODO(nicolezhu): refactor the following to work for all GCP services that
   * require an app server.
   */
  app.post('/', (req, res) => {
    if (!req.body) {
      const msg = 'no Pub/Sub message received';
      console.error(`error: ${msg}`);
      res.status(400).send(`Bad Request: ${msg}`);
      return;
    }
    if (!req.body.message) {
      const msg = 'invalid Pub/Sub message format';
      console.error(`error: ${msg}`);
      res.status(400).send(`Bad Request: ${msg}`);
      return;
    }

    const pubSubMessage = req.body.message;
    const name = pubSubMessage.data
        ? Buffer.from(pubSubMessage.data, 'base64').toString().trim()
        : 'World';

    console.log(`Hello ${name}!`);
    res.status(204).send();
  });

// TODO(nicolezhu): if this mucks up: refactor according to:
// https://cloud.google.com/run/docs/tutorials/pubsub#looking_at_the_code
  const PORT = process.env.PORT || 8080;
  app.listen(PORT, () =>
      console.log(`nodejs-pubsub-tutorial listening on port ${PORT}`)
  );
}

/**
 * Background Cloud Function to be triggered by Pub/Sub.
 * This function is exported by index.js, and executed when
 * the trigger topic receives a message.
 *
 * @param {object} message The Pub/Sub message.
 * @param {object} context The event metadata.
 */
exports.pubsubFunction = (message, context) => {
  const msg = message.data
      ? Buffer.from(message.data, 'base64').toString()
      : console.log("no log function was invoked");

  console.log('attributes if any: ');
  console.log(message.attributes);

  // TODO later (nicolezhu):
  // write fns in separate file and do var funcFo0 = function(){}... modules.exports={ func: funcFoo}
  // var methods = require()... methods['funcString']()
  switch (msg) {
    case 'simplelog':
      if (message.attributes) {
        simplelog(message.attributes['log_name'], message.attributes['log_text']);
      } else {
        simplelog();
      }
      break;
    default:
      console.log(`Invalid log function was invoked.`);
  }
};

/**
 * envctl nodejs <env> trigger simplelog log_name=foo,log_text=bar
 */
function simplelog(logname = "my-log", logtext = "hello world" ) {
  const log = logging.log(logname);

  const text_entry = log.entry(logtext);

  log.write(text_entry).then(r => console.log(r));
}
