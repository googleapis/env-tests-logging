package functions;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.events.cloud.pubsub.v1.Message;
import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Payload.StringPayload;
import com.google.cloud.logging.Severity;
import java.util.Collections;

public class TestFunctions implements BackgroundFunction<Message> {

  @Override
  public void accept(Message message, Context context) {
    String payload = "hello world";
    if (message != null && message.getData() != null) {
      payload = new String(
          Base64.getDecoder().decode(message.getData().getBytes(StandardCharsets.UTF_8)),
          StandardCharsets.UTF_8);
    }
    try (Logging logging = LoggingOptions.getDefaultInstance().getService()) {
        logging.setWriteSynchronicity(Synchronicity.SYNC);

        LogEntry entry = LogEntry.newBuilder(StringPayload.of(payload))
            .setLogName("test-log")
            .build();
        logging.write(Collections.singleton(entry));
    }
  }
}
