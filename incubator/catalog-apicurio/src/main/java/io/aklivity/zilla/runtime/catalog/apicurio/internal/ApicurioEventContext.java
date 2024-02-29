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
package io.aklivity.zilla.runtime.catalog.apicurio.internal;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.catalog.apicurio.internal.types.event.ApicurioEventFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class ApicurioEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final ApicurioEventFW.Builder apicurioEventRW = new ApicurioEventFW.Builder();
    private final MutableDirectBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final int apicurioTypeId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public ApicurioEventContext(
        EngineContext context)
    {
        this.apicurioTypeId = context.supplyTypeId(ApicurioCatalog.NAME);
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void remoteAccessRejected(
        long catalogId,
        HttpRequest httpRequest,
        int status)
    {
        ApicurioEventFW event = apicurioEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .remoteAccessRejected(e -> e
                .timestamp(clock.millis())
                .traceId(0L)
                .namespacedId(catalogId)
                .method(httpRequest.method())
                .url(httpRequest.uri().toString())
                .status((short) status)
            )
            .build();
        eventWriter.accept(apicurioTypeId, event.buffer(), event.offset(), event.limit());
    }
}
