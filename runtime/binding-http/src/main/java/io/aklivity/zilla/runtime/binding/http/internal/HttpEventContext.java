/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.Map;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.http.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpEventFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class HttpEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 2048;
    private static final String8FW HEADER_SCHEME = new String8FW(":scheme");
    private static final String8FW HEADER_METHOD = new String8FW(":method");
    private static final String8FW HEADER_PATH = new String8FW(":path");

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final HttpEventFW.Builder httpEventRW = new HttpEventFW.Builder();
    private final int httpTypeId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public HttpEventContext(
        EngineContext context)
    {
        this.httpTypeId = context.supplyTypeId(HttpBinding.NAME);
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void authorizationFailed(
        long sessionId,
        long traceId,
        long routedId,
        GuardHandler guard,
        long authorization)
    {
        if (sessionId == 0)
        {
            String identity = guard == null ? null : guard.identity(authorization);
            HttpEventFW event = httpEventRW
                .wrap(eventBuffer, 0, eventBuffer.capacity())
                .authorizationFailed(e -> e
                    .timestamp(clock.millis())
                    .traceId(traceId)
                    .namespacedId(routedId)
                    .identity(identity)
                )
                .build();
            eventWriter.accept(httpTypeId, event.buffer(), event.offset(), event.limit());
        }
    }

    public void requestAccepted(
        long traceId,
        long routedId,
        GuardHandler guard,
        long authorization,
        Map<String, String> headers)
    {
        String identity = guard == null ? null : guard.identity(authorization);
        HttpEventFW event = httpEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .requestAccepted(e -> e
                .timestamp(clock.millis())
                .traceId(traceId)
                .namespacedId(routedId)
                .identity(identity)
                .scheme(headers.get(":scheme"))
                .method(headers.get(":method"))
                .path(headers.get(":path"))
            )
            .build();
        eventWriter.accept(httpTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void requestAccepted(
        long traceId,
        long routedId,
        GuardHandler guard,
        long authorization,
        Array32FW<HttpHeaderFW> headers)
    {
        String identity = guard == null ? null : guard.identity(authorization);
        HttpEventFW event = httpEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .requestAccepted(e -> e
                .timestamp(clock.millis())
                .traceId(traceId)
                .namespacedId(routedId)
                .identity(identity)
                .scheme(headers.matchFirst(h -> HEADER_SCHEME.equals(h.name())).value().asString())
                .method(headers.matchFirst(h -> HEADER_METHOD.equals(h.name())).value().asString())
                .path(headers.matchFirst(h -> HEADER_PATH.equals(h.name())).value().asString())
            )
            .build();
        eventWriter.accept(httpTypeId, event.buffer(), event.offset(), event.limit());
    }
}
