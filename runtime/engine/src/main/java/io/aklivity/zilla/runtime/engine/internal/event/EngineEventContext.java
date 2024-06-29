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
package io.aklivity.zilla.runtime.engine.internal.event;

import static io.aklivity.zilla.runtime.engine.internal.types.event.EngineEventType.CONFIG_WATCHER_FAILED;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.event.EngineEventExFW;
import io.aklivity.zilla.runtime.engine.internal.types.event.EventFW;

public final class EngineEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final MutableDirectBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final MutableDirectBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));

    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final EngineEventExFW.Builder eventExRW = new EngineEventExFW.Builder();

    private final long engineId;
    private final int engineTypeId;
    private final int configWatcherFailedEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public EngineEventContext(
        Engine engine)
    {
        this.engineId = engine.supplyNamespacedId(Engine.NAME, "events");
        this.engineTypeId = engine.supplyLabelId(Engine.NAME);
        this.configWatcherFailedEventId = engine.supplyLabelId("engine.config.watcher.failed");
        this.eventWriter = engine.supplyEventWriter();
        this.clock = engine.clock();
    }

    public void configWatcherFailed(
        long traceId,
        String reason)
    {
        EngineEventExFW extension = eventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .configWatcherFailed(e -> e
                .typeId(CONFIG_WATCHER_FAILED.value())
                .reason(reason)
            )
            .build();

        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(configWatcherFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(engineId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();

        eventWriter.accept(engineTypeId, event.buffer(), event.offset(), event.limit());
    }

}
