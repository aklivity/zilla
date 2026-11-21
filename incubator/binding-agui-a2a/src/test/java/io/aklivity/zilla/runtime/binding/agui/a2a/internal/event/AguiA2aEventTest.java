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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.function.Consumer;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public class AguiA2aEventTest
{
    private final DirectBufferEx[] written = new DirectBufferEx[1];
    private final AguiA2aEventFormatterFactory factory = new AguiA2aEventFormatterFactory();

    private String format(
        Consumer<AguiA2aEventContext> event)
    {
        final EngineContext context = mock(EngineContext.class);
        when(context.supplyTypeId("agui-a2a")).thenReturn(7);
        when(context.supplyEventId("binding.agui.a2a.run.routed")).thenReturn(1);
        when(context.supplyEventId("binding.agui.a2a.run.finished")).thenReturn(2);
        when(context.supplyEventId("binding.agui.a2a.run.failed")).thenReturn(3);
        when(context.supplyEventId("binding.agui.a2a.run.redirected")).thenReturn(4);
        when(context.supplyEventId("binding.agui.a2a.context.reset")).thenReturn(5);
        when(context.supplyEventId("binding.agui.a2a.handle.rejected")).thenReturn(6);
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn((typeId, buffer, index, length) ->
        {
            final byte[] copy = new byte[length];
            buffer.getBytes(index, copy);
            written[0] = new UnsafeBufferEx(copy);
        });

        event.accept(new AguiA2aEventContext(context));

        final EventFormatterSpi formatter = factory.create(new Configuration());
        return formatter.format(written[0], 0, written[0].capacity());
    }

    @Test
    public void shouldFormatType()
    {
        assertThat(factory.type(), equalTo("agui-a2a"));
    }

    @Test
    public void shouldFormatRunRouted()
    {
        assertThat(format(e -> e.runRouted(1L, 42L, "agent0", "task0")),
            equalTo("Run routed to agent (agent0) as task (task0)."));
    }

    @Test
    public void shouldFormatRunFinished()
    {
        assertThat(format(e -> e.runFinished(1L, 42L, "agent0", "task0", "context0", "SUCCESS")),
            equalTo("Run finished with outcome (SUCCESS) for agent (agent0) task (task0) context (context0)."));
    }

    @Test
    public void shouldFormatRunFailed()
    {
        assertThat(format(e -> e.runFailed(1L, 42L, "agent0", "task0", "agent_failed", "Agent failed")),
            equalTo("Run failed with code (agent_failed) for agent (agent0) task (task0): Agent failed."));
    }

    @Test
    public void shouldFormatRunRedirected()
    {
        assertThat(format(e -> e.runRedirected(1L, 42L, "agent0", 7L)),
            equalTo("Run for agent (agent0) redirected to affinity (7)."));
    }

    @Test
    public void shouldFormatContextReset()
    {
        assertThat(format(e -> e.contextReset(1L, 42L, "agent0", "context1")),
            equalTo("Context reset for agent (agent0) to context (context1)."));
    }

    @Test
    public void shouldFormatHandleRejected()
    {
        assertThat(format(e -> e.handleRejected(1L, 42L, "agent0", "handle_unknown")),
            equalTo("Handle rejected for agent (agent0) with code (handle_unknown)."));
    }

    @Test
    public void shouldFormatTagDropped()
    {
        assertThat(format(e -> e.tagDropped(1L, 42L, "agent0", "task1", "subagent")),
            equalTo("Tag (subagent) dropped for agent (agent0) task (task1)."));
    }

    @Test
    public void shouldFormatNullFieldsAsEmpty()
    {
        assertThat(format(e -> e.runRouted(1L, 42L, "agent0", null)),
            equalTo("Run routed to agent (agent0) as task ()."));
    }
}
