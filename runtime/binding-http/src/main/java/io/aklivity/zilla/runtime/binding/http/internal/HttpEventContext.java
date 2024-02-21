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
import io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpEventFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.event.Result;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class HttpEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 2048;
    private static final int HEADER_BUFFER_CAPACITY = 1024;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final HttpEventFW.Builder httpEventRW = new HttpEventFW.Builder();
    private final AtomicBuffer headerBuffer = new UnsafeBuffer(new byte[HEADER_BUFFER_CAPACITY]);
    private final Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW> headersRW =
        new Array32FW.Builder<>(new HttpHeaderFW.Builder(), new HttpHeaderFW());
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

    public void authorization(
        long sessionId,
        long traceId,
        long routedId,
        String identity)
    {
        Result result = sessionId == 0 ? Result.FAILURE : Result.SUCCESS;
        HttpEventFW event = httpEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .authorization(e -> e
                .timestamp(clock.millis())
                .traceId(traceId)
                .namespacedId(routedId)
                .result(r -> r.set(result))
                .identity(identity)
            )
            .build();
        eventWriter.accept(httpTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void request(
        long traceId,
        long routedId,
        Array32FW<HttpHeaderFW> headers)
    {
        HttpEventFW event = httpEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .request(e -> e
                .timestamp(clock.millis())
                .traceId(traceId)
                .namespacedId(routedId)
                .headers(headers))
            .build();
        eventWriter.accept(httpTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void request(
        long traceId,
        long routedId,
        Map<String, String> headers)
    {
        headersRW.wrap(headerBuffer, 0, headerBuffer.capacity());
        headers.forEach((n, v) -> headersRW.item(i -> i.name(n).value(v)));
        request(traceId, routedId, headersRW.build());
    }
}
