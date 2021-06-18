
package envtest.deployable.web;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Payload.StringPayload;
import com.google.cloud.logging.Severity;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;

import io.grpc.StatusRuntimeException;

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

/**
 * Defines a controller to handle HTTP requests.
 */
@RestController
public final class DeployableHttpController {


    private void startSubscriber() throws IOException, InterruptedException {
        String topicId = System.getenv().getOrDefault("PUBSUB_TOPIC", "logging-test");
        String projectId = System.getenv("PROJECT_ID");
        String subscriptionId = topicId + "-subscriber";

        ProjectSubscriptionName subscriptionName =
                    ProjectSubscriptionName.of(projectId, subscriptionId);
        ProjectTopicName topicName = ProjectTopicName.of(projectId, topicId);

        //SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create();
        //subscriptionAdminClient.createSubscription(subscriptionName, topicName, PushConfig.getDefaultInstance(), 0);


        MessageReceiver receiver = new MessageReceiver() {
            @Override
            public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
                System.out.println("got message: " + message.getData().toStringUtf8());
                consumer.ack();
            }
        };

        Subscriber subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
        subscriber.addListener(
            new Subscriber.Listener() {
                @Override
                public void failed(Subscriber.State from, Throwable failure) {
                    // Handle failure. This is called when the Subscriber encountered a fatal error and is shutting down.
                    System.err.println(failure);
                }
            },
            MoreExecutors.directExecutor());
        subscriber.startAsync().awaitRunning();
        System.out.printf("Listening for messages on %s:\n", subscriptionName.toString());
    }


    @Autowired
    public DeployableHttpController() throws Exception {
        String listener = System.getenv("ENABLE_SUBSCRIBER");
        if (listener != null){
            try {
                this.startSubscriber();
            } catch (StatusRuntimeException e) {}
        }

    }

    @GetMapping("/")
    public String helloWorld() {
        String message = "It's running!";

        // Instantiates a client
        Logging logging = LoggingOptions.getDefaultInstance().getService();
        LogEntry entry =
            LogEntry.newBuilder(StringPayload.of(message))
                .setSeverity(Severity.ERROR)
                .setLogName("java")
                .setResource(MonitoredResource.newBuilder("global").build())
                .build();

         //Writes the log entry asynchronously
        logging.write(Collections.singleton(entry));

        return message;
    }
}
