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
package io.aklivity.zilla.runtime.catalog.karapace.internal;


import static io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceEventType.REMOTE_ACCESS_REJECTED;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.catalog.karapace.internal.types.event.KarapaceEventExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class KarapaceEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final KarapaceEventExFW.Builder karapaceEventExRW = new KarapaceEventExFW.Builder();
    private final int karapaceTypeId;
    private final int remoteAccessRejectedEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public KarapaceEventContext(
        EngineContext context)
    {
        this.karapaceTypeId = context.supplyTypeId(KarapaceCatalog.NAME);
        this.remoteAccessRejectedEventId = context.supplyEventId("catalog.karapace.remote.access.rejected");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void remoteAccessRejected(
        long catalogId,
        HttpRequest httpRequest,
        int status)
    {
        KarapaceEventExFW extension = karapaceEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .remoteAccessRejected(e -> e
                .typeId(REMOTE_ACCESS_REJECTED.value())
                .method(httpRequest.method())
                .url(httpRequest.uri().toString())
                .status((short) status)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(remoteAccessRejectedEventId)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(karapaceTypeId, event.buffer(), event.offset(), event.limit());
    }
}
