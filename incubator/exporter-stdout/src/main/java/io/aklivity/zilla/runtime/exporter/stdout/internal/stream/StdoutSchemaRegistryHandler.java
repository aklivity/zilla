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

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.exporter.stdout.internal.StdoutExporterContext;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.SchemaRegistryEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.SchemaRegistryRemoteAccessRejectedFW;

public class StdoutSchemaRegistryHandler extends EventHandler
{
    private static final String REMOTE_ACCESS_REJECTED = "%s - [%s] REMOTE_ACCESS_REJECTED %s %s %d%n";

    private final SchemaRegistryEventFW schemaRegistryEventRO = new SchemaRegistryEventFW();

    public StdoutSchemaRegistryHandler(
        StdoutExporterContext context,
        PrintStream out)
    {
        super(context, out);
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
            SchemaRegistryRemoteAccessRejectedFW e = event.remoteAccessRejected();
            String qname = context.supplyQName(e.namespacedId());
            out.printf(REMOTE_ACCESS_REJECTED, qname, asDateTime(e.timestamp()), asString(e.method()), asString(e.url()),
                e.status());
            break;
        }
    }
}