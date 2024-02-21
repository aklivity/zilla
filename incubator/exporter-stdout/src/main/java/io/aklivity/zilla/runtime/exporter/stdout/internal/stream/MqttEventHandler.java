/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.exporter.stdout.internal.stream;

import java.io.PrintStream;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.MqttAuthorizationFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.MqttEventFW;

public class MqttEventHandler extends EventHandler
{
    private static final String MQTT_AUTHORIZATION_FORMAT =
        "MQTT Authorization %s [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s] [identity = %s]%n";

    private final MqttEventFW mqttEventRO = new MqttEventFW();

    public MqttEventHandler(
        LongFunction<String> supplyNamespace,
        LongFunction<String> supplyLocalName,
        PrintStream out)
    {
        super(supplyNamespace, supplyLocalName, out);
    }

    public void handleEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        MqttEventFW event = mqttEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case AUTHORIZATION:
            MqttAuthorizationFW e = event.authorization();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(MQTT_AUTHORIZATION_FORMAT, result(e.result()), e.timestamp(), e.traceId(), namespace,
                binding, asString(e.identity()));
            break;
        }
    }
}