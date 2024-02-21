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

import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.KafkaAuthorizationFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.KafkaEventFW;

public class KafkaEventHandler extends EventHandler
{
    private static final String KAFKA_AUTHORIZATION_FORMAT =
        "Kafka Authorization %s [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s]%n";
    private static final String KAFKA_API_VERSION_REJECTED =
        "Kafka API Version Rejected [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s]%n";

    private final KafkaEventFW kafkaEventRO = new KafkaEventFW();

    public KafkaEventHandler(
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
        final KafkaEventFW event = kafkaEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case AUTHORIZATION:
        {
            KafkaAuthorizationFW e = event.authorization();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(KAFKA_AUTHORIZATION_FORMAT, result(e.result()), e.timestamp(), e.traceId(), namespace,
                binding);
            break;
        }
        case API_VERSION_REJECTED:
        {
            EventFW e = event.apiVersionRejected();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(KAFKA_API_VERSION_REJECTED, e.timestamp(), e.traceId(), namespace, binding);
            break;
        }
        }
    }
}