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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.function.LongSupplier;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryEventFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class SchemaRegistryEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final SchemaRegistryEventFW.Builder schemaRegistryEventRW = new SchemaRegistryEventFW.Builder();
    private final MutableDirectBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final int schemaRegistryTypeId;
    private final MessageConsumer eventWriter;
    private final LongSupplier timestamp;

    public SchemaRegistryEventContext(
        EngineContext context)
    {
        this.schemaRegistryTypeId = context.supplyTypeId(SchemaRegistryCatalog.NAME);
        this.eventWriter = context.supplyEventWriter();
        this.timestamp = context.timestamp();
    }

    public void remoteAccessRejected(
        long catalogId,
        HttpRequest httpRequest,
        int status)
    {
        SchemaRegistryEventFW event = schemaRegistryEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .remoteAccessRejected(e -> e
                .timestamp(timestamp.getAsLong())
                .namespacedId(catalogId)
                .url(httpRequest.uri().toString())
                .method(httpRequest.method())
                .status((short) status)
            )
            .build();
        eventWriter.accept(schemaRegistryTypeId, event.buffer(), event.offset(), event.limit());
    }
}
