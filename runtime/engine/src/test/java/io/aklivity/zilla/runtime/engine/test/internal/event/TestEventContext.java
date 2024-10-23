/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.event;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.engine.test.internal.binding.TestBinding;

public class TestEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 2048;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final int testTypeId;
    private final int connectedEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public TestEventContext(
        EngineContext context)
    {
        this.testTypeId = context.supplyTypeId(TestBinding.NAME);
        this.connectedEventId = context.supplyEventId("binding.test.connected");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void connected(
        long traceId,
        long bindingId,
        long timestamp,
        String message)
    {
        String8FW extension = new String8FW(message);
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(connectedEventId)
            .timestamp(timestamp)
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(testTypeId, event.buffer(), event.offset(), event.limit());
    }
}
