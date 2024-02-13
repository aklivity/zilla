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

import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.SchemaRegistryEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.SchemaRegistryRemoteAccessRejectedEventFW;

public class SchemaRegistryEventHandler extends EventHandler
{
    private static final String SCHEMA_REGISTRY_REMOTE_ACCESS_REJECTED =
        "ERROR: Schema Registry Remote Access Rejected [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s] [url = %s]" +
            "[method = %s] [status = %d]%n";

    private final SchemaRegistryEventFW schemaRegistryEventRO = new SchemaRegistryEventFW();

    public SchemaRegistryEventHandler(
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
        SchemaRegistryEventFW event = schemaRegistryEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case REMOTE_ACCESS_REJECTED:
            SchemaRegistryRemoteAccessRejectedEventFW e = event.remoteAccessRejected();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(SCHEMA_REGISTRY_REMOTE_ACCESS_REJECTED, e.timestamp(), e.traceId(), namespace, binding, asString(e.url()),
                asString(e.method()), e.status());
            break;
        }
    }
}
