/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package envtests;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.logging.Severity;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.TopicName;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Router {

  private static Snippets snippets = new Snippets();

  private static void subscribe(String projectId, String subscriptionId) {
    ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId);

    MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
      triggerTest(message, snippets);
      System.out.println("Id: " + message.getMessageId());
      System.out.println("Data: " + message.getData().toStringUtf8());
      consumer.ack();
    };

    Subscriber subscriber = null;
    try {
      subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
      subscriber.startAsync().awaitRunning();
      System.out.printf("Listening for messages on %s:\n", subscriptionName.toString());
      subscriber.awaitTerminated(30, TimeUnit.SECONDS);
    } catch (TimeoutException timeoutException) {
      subscriber.stopAsync();
    }
  }

  private static void triggerTest(PubsubMessage message, Snippets snippets) {
    // TODO while we could make it generic and use reflection, it's not safe so for now - ugly ifs
    if (message == null)
      return;

    String testName = message.getData().toStringUtf8();
    if (testName == null || testName == "")
      return;

    String logName = message.getAttributesOrDefault("log_name", null);
    String logText = message.getAttributesOrDefault("log_text", null);
    Severity severity = Severity.valueOf(message.getAttributesOrDefault("severity", null));
    if (testName == "simpleLog") {
      snippets.SimpleLog(logName, logText, severity);
    }
  }

  // Event driven function - TODO - zeltser - rewrite
  /*
  public void PubSubGcf(Message message, Context context) {
    if (message != null && message.getData() != null) {
      String messageStr = new String(Base64.getDecoder().decode(message.getData()
              .getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }
  }*/

  public static void main(String[] args) throws IOException {

    String projectId = "MY_PROJECT_ID";
    String topicId;
    String subscriptionId;

    // ****************** GAE, GKE, GCE ******************
    // Enable app subscriber for all environments except GCR
    Boolean enableSubscriber = Boolean.parseBoolean(System.getenv().getOrDefault("ENABLE_SUBSCRIBER", "false"));
    System.out.format("ENV: ENABLE_SUBSCRIBER=true\n");
    if (enableSubscriber) {

      // TODO - zeltser - figure out where to read it from
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
      if (credentials instanceof ServiceAccountCredentials) {
        projectId = ((ServiceAccountCredentials) credentials).getProjectId();
      }

      topicId = System.getenv("PUBSUB_TOPIC");
      if (topicId != null && topicId != "") {
        topicId = "logging-test";
      }

      subscriptionId = topicId + "-subscriber";
      TopicName topicName = TopicName.of(projectId, topicId);
      ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId);
      SubscriptionAdminClient subscriptionClient = SubscriptionAdminClient.create();
      subscriptionClient.createSubscription(
              subscriptionName,
              topicName,
              PushConfig.newBuilder().build(),
              20);
      subscribe(projectId, subscriptionId);
    }

    // GCR, GAE Standard
    Boolean runServer = Boolean.parseBoolean(System.getenv().getOrDefault("RUNSERVER", "0"));
    System.out.format("ENV: RUNSERVER=%b\n", runServer);
    if (runServer) {
      Integer port = 8080;
      if (System.getenv("PORT") != null && System.getenv("PORT") != "") {
        port = Integer.parseInt(System.getenv("PORT"));
      }

      // Start a web server for Cloud Run
      try {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", (var t) -> {
          byte[] response = "Hello World!".getBytes();
          t.sendResponseHeaders(200, response.length);
          try (OutputStream os = t.getResponseBody()) {
            os.write(response);
          }
        });
        server.start();
      } catch (Throwable tr) {
        tr.printStackTrace();
      }
    }
  }
}

