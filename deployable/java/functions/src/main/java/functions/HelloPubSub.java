/*
 * Copyright 2020-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package functions;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
//import com.google.events.cloud.pubsub.v1.PubsubMessage;
//import com.google.pubsub.v1.PubsubMessage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class HelloPubSub implements BackgroundFunction<PubSubMessage> {
  private static final Logger logger = Logger.getLogger(HelloPubSub.class.getName());

  @Override
  public void accept(PubSubMessage message, Context context) {
      String fnName = new String(Base64.getDecoder().decode(message.data.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    Map<String, String> args = message.attributes;

    triggerSnippet(fnName, args);
    return;
  }

  public static void triggerSnippet(String fnName, Map<String,String> args) {
    try {
      Snippets obj = new Snippets();
      Class<?> c = obj.getClass();
      Method found = c.getDeclaredMethod(fnName, new Class[] {Map.class});
      found.invoke(obj, args);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      System.out.println(e.toString());
    }
  }
}

class PubSubMessage {
  public String data;
  public Map<String, String> attributes;
  public String messageId;
  public String publishTime;
}
