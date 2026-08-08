/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.guard.x509.internal;

import static io.aklivity.zilla.runtime.guard.x509.internal.types.event.X509EventType.AUTHORIZATION_FAILED;

import java.nio.ByteBuffer;
import java.time.Clock;

import io.aklivity.zilla.runtime.common.agrona.buffer.AtomicBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.guard.x509.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.guard.x509.internal.types.event.X509EventExFW;

public class X509EventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBufferEx eventBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBufferEx extensionBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final X509EventExFW.Builder x509EventExRW = new X509EventExFW.Builder();
    private final int x509TypeId;
    private final int authorizationFailedEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public X509EventContext(
        EngineContext context)
    {
        this.x509TypeId = context.supplyTypeId(X509Guard.NAME);
        this.authorizationFailedEventId = context.supplyEventId("guard.x509.authorization.failed");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void authorizationFailed(
        long traceId,
        long bindingId,
        String identity,
        String reason)
    {
        X509EventExFW extension = x509EventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .authorizationFailed(e -> e
                .typeId(AUTHORIZATION_FAILED.value())
                .identity(identity)
                .reason(reason)
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
        eventWriter.accept(x509TypeId, event.buffer(), event.offset(), event.limit());
    }
}
