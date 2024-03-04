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
package io.aklivity.zilla.runtime.guard.jwt.internal;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.guard.jwt.internal.types.event.JwtEventFW;

public class JwtEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final JwtEventFW.Builder jwtEventRW = new JwtEventFW.Builder();
    private final MutableDirectBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final int jwtTypeId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public JwtEventContext(
        EngineContext context)
    {
        this.jwtTypeId = context.supplyTypeId(JwtGuard.NAME);
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void authorizationFailed(
        long traceId,
        long bindingId,
        String identity)
    {
        JwtEventFW event = jwtEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .authorizationFailed(e -> e
                .timestamp(clock.millis())
                .traceId(traceId)
                .namespacedId(bindingId)
                .identity(identity)
            )
            .build();
        eventWriter.accept(jwtTypeId, event.buffer(), event.offset(), event.limit());
    }
}
