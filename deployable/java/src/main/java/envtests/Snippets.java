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

import com.google.cloud.logging.*;

import java.util.Collections;

public class Snippets {
    public void SimpleLog(String logName, String logText, Severity severity) {

        try (Logging logging = LoggingOptions.getDefaultInstance().getService()) {
            LogEntry simpleLog = LogEntry.newBuilder(Payload.StringPayload.of(logText))
                    .setLogName(logName)
                    .setSeverity(severity)
                    .build();
            logging.write(Collections.singleton(simpleLog));
        } catch (Exception ex) {
            System.out.printf("Error logging %s", ex);
        }
    }
}
