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

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aContextResetExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aEventExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aHandleRejectedExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aRunFailedExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aRunFinishedExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aRunRedirectedExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.AguiA2aRunRoutedExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public final class AguiA2aEventFormatter implements EventFormatterSpi
{
    private final EventFW eventRO = new EventFW();
    private final AguiA2aEventExFW aguiA2aEventExRO = new AguiA2aEventExFW();

    AguiA2aEventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBufferEx buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final AguiA2aEventExFW extension = aguiA2aEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        String result = null;
        switch (extension.kind())
        {
        case RUN_ROUTED:
        {
            AguiA2aRunRoutedExFW ex = extension.runRouted();
            result = String.format("Run routed to agent (%s) as task (%s).", asString(ex.agent()), asString(ex.taskId()));
            break;
        }
        case RUN_FINISHED:
        {
            AguiA2aRunFinishedExFW ex = extension.runFinished();
            result = String.format("Run finished with outcome (%s) for agent (%s) task (%s) context (%s).",
                asString(ex.outcome()), asString(ex.agent()), asString(ex.taskId()), asString(ex.contextId()));
            break;
        }
        case RUN_FAILED:
        {
            AguiA2aRunFailedExFW ex = extension.runFailed();
            result = String.format("Run failed with code (%s) for agent (%s) task (%s): %s.",
                asString(ex.code()), asString(ex.agent()), asString(ex.taskId()), asString(ex.detail()));
            break;
        }
        case RUN_REDIRECTED:
        {
            AguiA2aRunRedirectedExFW ex = extension.runRedirected();
            result = String.format("Run for agent (%s) redirected to affinity (%d).", asString(ex.agent()), ex.affinity());
            break;
        }
        case CONTEXT_RESET:
        {
            AguiA2aContextResetExFW ex = extension.contextReset();
            result = String.format("Context reset for agent (%s) to context (%s).",
                asString(ex.agent()), asString(ex.contextId()));
            break;
        }
        case HANDLE_REJECTED:
        {
            AguiA2aHandleRejectedExFW ex = extension.handleRejected();
            result = String.format("Handle rejected for agent (%s) with code (%s).",
                asString(ex.agent()), asString(ex.code()));
            break;
        }
        }
        return result;
    }

    private static String asString(
        String16FW stringFW)
    {
        String s = stringFW.asString();
        return s == null ? "" : s;
    }
}
