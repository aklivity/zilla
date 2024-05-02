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

import static io.aklivity.zilla.runtime.guard.jwt.internal.types.event.JwtEventType.AUTHORIZATION_FAILED;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.guard.jwt.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.guard.jwt.internal.types.event.JwtEventExFW;

public class JwtEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final JwtEventExFW.Builder jwtEventExRW = new JwtEventExFW.Builder();
    private final int jwtTypeId;
    private final int authorizationFailedEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public JwtEventContext(
        EngineContext context)
    {
        this.jwtTypeId = context.supplyTypeId(JwtGuard.NAME);
        this.authorizationFailedEventId = context.supplyEventId("guard.jwt.authorization.failed");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void authorizationFailed(
        long traceId,
        long bindingId,
        String identity)
    {
        JwtEventExFW extension = jwtEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .authorizationFailed(e -> e
                .typeId(AUTHORIZATION_FAILED.value())
                .identity(identity)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(authorizationFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(jwtTypeId, event.buffer(), event.offset(), event.limit());
    }
}
