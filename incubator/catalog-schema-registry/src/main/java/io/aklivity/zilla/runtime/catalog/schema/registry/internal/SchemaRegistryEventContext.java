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
    private final MessageConsumer logEvent;
    private final LongSupplier timestamp;

    public SchemaRegistryEventContext(
        EngineContext context)
    {
        this.schemaRegistryTypeId = context.supplyTypeId(SchemaRegistryCatalog.NAME);
        this.logEvent = context.logger();
        this.timestamp = context.timestamp();
    }

    public void remoteAccessRejected(
        long catalogId,
        String url,
        String method,
        int status)
    {
        SchemaRegistryEventFW event = schemaRegistryEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .remoteAccessRejected(e -> e
                .timestamp(timestamp.getAsLong())
                .catalogId(catalogId)
                .url(url)
                .method(method)
                .status((short) status)
            )
            .build();
        System.out.println(event); // TODO: Ati
        logEvent.accept(schemaRegistryTypeId, event.buffer(), event.offset(), event.limit());
    }
}
