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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.event;

import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aEventType.CONTEXT_RESET;
import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aEventType.HANDLE_REJECTED;
import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aEventType.RUN_FAILED;
import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aEventType.RUN_FINISHED;
import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aEventType.RUN_REDIRECTED;
import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aEventType.RUN_ROUTED;

import java.nio.ByteBuffer;
import java.time.Clock;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.AguiA2aBinding;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aEventExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.AtomicBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class AguiA2aEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBufferEx eventBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBufferEx extensionBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final AguiA2aEventExFW.Builder aguiA2aEventExRW = new AguiA2aEventExFW.Builder();
    private final int aguiA2aTypeId;
    private final int runRoutedId;
    private final int runFinishedId;
    private final int runFailedId;
    private final int runRedirectedId;
    private final int contextResetId;
    private final int handleRejectedId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public AguiA2aEventContext(
        EngineContext context)
    {
        this.aguiA2aTypeId = context.supplyTypeId(AguiA2aBinding.TYPE);
        this.runRoutedId = context.supplyEventId("binding.agui.a2a.run.routed");
        this.runFinishedId = context.supplyEventId("binding.agui.a2a.run.finished");
        this.runFailedId = context.supplyEventId("binding.agui.a2a.run.failed");
        this.runRedirectedId = context.supplyEventId("binding.agui.a2a.run.redirected");
        this.contextResetId = context.supplyEventId("binding.agui.a2a.context.reset");
        this.handleRejectedId = context.supplyEventId("binding.agui.a2a.handle.rejected");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void runRouted(
        long traceId,
        long bindingId,
        String agent,
        String taskId)
    {
        AguiA2aEventExFW extension = aguiA2aEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .runRouted(e -> e
                .typeId(RUN_ROUTED.value())
                .agent(agent)
                .taskId(taskId)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(runRoutedId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(aguiA2aTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void runFinished(
        long traceId,
        long bindingId,
        String agent,
        String taskId,
        String contextId,
        String outcome)
    {
        AguiA2aEventExFW extension = aguiA2aEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .runFinished(e -> e
                .typeId(RUN_FINISHED.value())
                .agent(agent)
                .taskId(taskId)
                .contextId(contextId)
                .outcome(outcome)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(runFinishedId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(aguiA2aTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void runFailed(
        long traceId,
        long bindingId,
        String agent,
        String taskId,
        String code,
        String detail)
    {
        AguiA2aEventExFW extension = aguiA2aEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .runFailed(e -> e
                .typeId(RUN_FAILED.value())
                .agent(agent)
                .taskId(taskId)
                .code(code)
                .detail(detail)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(runFailedId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(aguiA2aTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void runRedirected(
        long traceId,
        long bindingId,
        String agent,
        long affinity)
    {
        AguiA2aEventExFW extension = aguiA2aEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .runRedirected(e -> e
                .typeId(RUN_REDIRECTED.value())
                .agent(agent)
                .affinity(affinity)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(runRedirectedId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(aguiA2aTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void contextReset(
        long traceId,
        long bindingId,
        String agent,
        String contextId)
    {
        AguiA2aEventExFW extension = aguiA2aEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .contextReset(e -> e
                .typeId(CONTEXT_RESET.value())
                .agent(agent)
                .contextId(contextId)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(contextResetId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(aguiA2aTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void handleRejected(
        long traceId,
        long bindingId,
        String agent,
        String code)
    {
        AguiA2aEventExFW extension = aguiA2aEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .handleRejected(e -> e
                .typeId(HANDLE_REJECTED.value())
                .agent(agent)
                .code(code)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(handleRejectedId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(aguiA2aTypeId, event.buffer(), event.offset(), event.limit());
    }
}
