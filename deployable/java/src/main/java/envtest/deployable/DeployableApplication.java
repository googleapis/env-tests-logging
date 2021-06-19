package envtest.deployable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.io.IOException;
import java.lang.Thread;
import java.lang.InterruptedException;

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
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class serves as an entry point for the Spring Boot app
 * Here, we check to ensure all required environment variables are set
 */
@SpringBootApplication
public class DeployableApplication {

    private static final Logger logger = LoggerFactory.getLogger(DeployableApplication.class);

    private static void subscribe(String projectId, String subscriptionId) {
        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId);

        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
        // triggerTest(message, snippets);
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

    public static void main(String[] args) throws IOException {
        String projectId = "";
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

          topicId = System.getenv().getOrDefault("PUBSUB_TOPIC", "logging-test");
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
          HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
          server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                System.out.println("received message");
                // send response
                OutputStream outputStream = exchange.getResponseBody();
                exchange.sendResponseHeaders(200, 0);
                outputStream.close();
            }
          });
          System.out.println("listening for http requests on port " + System.getenv("PORT"));
          server.start();
        }
    }
}
