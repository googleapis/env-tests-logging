
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


    @GetMapping("/")
    public String helloWorld() {
        String message = "It's running!";

        return message;
    }
}
