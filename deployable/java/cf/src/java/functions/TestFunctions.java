package functions;

import com.google.cloud.functions.CloudEventsFunction;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Payload.StringPayload;
import com.google.cloud.logging.Severity;
import com.google.cloud.logging.Synchronicity;
import io.cloudevents.CloudEvent;
import java.util.Collections;

public class TestFunctions implements CloudEventsFunction {
  private static final Logging logging = LoggingOptions.getDefaultInstance().getService();
  
  @Override
  public void accept(CloudEvent event) {
    // Get cloud event data as JSON string
    String cloudEventData = new String(event.getData().toBytes());
    logging.setWriteSynchronicity(Synchronicity.SYNC);

    LogEntry entry = LogEntry.newBuilder(StringPayload.of(cloudEventData))
        .setLogName("test-log")
        .build();
    logging.write(Collections.singleton(entry));
  }
}
