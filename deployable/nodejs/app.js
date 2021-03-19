const {Logging} = require('@google-cloud/logging');
const logging = new Logging();

/**
 * Background Cloud Function to be triggered by Pub/Sub.
 * This function is exported by index.js, and executed when
 * the trigger topic receives a message.
 *
 * @param {object} message The Pub/Sub message.
 * @param {object} context The event metadata.
 */
exports.pubsubFunction = (message, context) => {
  console.log("success yayyyy");

  const msg = message.data
      ? Buffer.from(message.data, 'base64').toString()
      : 'World';

  console.log(`Message was: ${msg}!`);

  // TODO deflake this if needed
  const log = logging.log("mylog");

  const text_entry = log.entry('LogEntry: Hello world!');

  log.write(text_entry).then(r => console.log(r));
};
