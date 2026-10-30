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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.agui.a2a.AguiA2aWithConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.AguiA2aConfiguration;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aKeyHash;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiBeginExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.RedirectFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class AguiA2aProxyFactoryTest
{
    private static final int AGUI_TYPE_ID = 7;
    private static final long BINDING_ID = 1L;
    private static final long LOCAL_AFFINITY = 0x01L;
    private static final long OWNER_AFFINITY = 0x02L;

    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
    private final MutableDirectBufferEx beginBuffer = new UnsafeBufferEx(new byte[1024]);
    private final List<RedirectFW> redirects = new ArrayList<>();
    private final List<Integer> events = new ArrayList<>();

    private EngineContext context;
    private AguiA2aProxyFactory factory;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        when(context.writeBuffer()).thenReturn(writeBuffer);
        when(context.supplyTypeId("agui")).thenReturn(AGUI_TYPE_ID);
        when(context.affinity()).thenReturn(LOCAL_AFFINITY);
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn((msgTypeId, buffer, index, length) -> events.add(msgTypeId));

        RouteConfig route = RouteConfig.builder().order(0).exit("a2a0").build();
        route.authorized = (auth, identity) -> true;
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("agui-a2a")
            .kind(KindConfig.PROXY)
            .routes(List.of(route))
            .build();
        binding.resolveId = name -> 1L;
        binding.id = BINDING_ID;

        factory = new AguiA2aProxyFactory(new AguiA2aConfiguration(), context);
        factory.attach(binding);
    }

    @Test
    public void shouldAcceptRunWhenOwnerIsLocal()
    {
        when(context.resolveAffinityId(AguiA2aKeyHash.hash(null, "weather", "thread-1"))).thenReturn(LOCAL_AFFINITY);

        assertThat(newStream(BINDING_ID, "thread-1", "weather"), nullValue());
    }

    @Test
    public void shouldRedirectRunToOwnerEchoingBeginExtension()
    {
        when(context.resolveAffinityId(AguiA2aKeyHash.hash(null, "weather", "thread-1"))).thenReturn(OWNER_AFFINITY);

        final MessageConsumer handler = newStream(BINDING_ID, "thread-1", "weather");
        final BeginFW begin = begin(BINDING_ID, "thread-1", "weather");
        handler.accept(BeginFW.TYPE_ID, begin.buffer(), begin.offset(), begin.sizeof());

        assertThat(redirects.size(), equalTo(1));
        assertThat(events.size(), equalTo(1));
        final RedirectFW redirect = redirects.get(0);
        assertThat(redirect.streamId(), equalTo(begin.streamId()));
        assertThat(redirect.routedId(), equalTo(BINDING_ID));
        assertThat(redirect.maximum(), equalTo(0));
        assertThat(redirect.affinity(), equalTo(OWNER_AFFINITY));
        assertThat(redirect.extension().sizeof(), equalTo(begin.extension().sizeof()));
        for (int i = 0; i < begin.extension().sizeof(); i++)
        {
            assertThat(redirect.extension().buffer().getByte(redirect.extension().offset() + i),
                equalTo(begin.extension().buffer().getByte(begin.extension().offset() + i)));
        }
    }

    @Test
    public void shouldKeyOwnerOnRewrittenAgent()
    {
        RouteConfig route = RouteConfig.builder()
            .order(0)
            .with(AguiA2aWithConfig.builder().agent("forecast").build())
            .exit("a2a0")
            .build();
        route.authorized = (auth, identity) -> true;
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("app1")
            .type("agui-a2a")
            .kind(KindConfig.PROXY)
            .routes(List.of(route))
            .build();
        binding.resolveId = name -> 2L;
        binding.id = 2L;
        factory.attach(binding);
        when(context.resolveAffinityId(AguiA2aKeyHash.hash(null, "forecast", "thread-1"))).thenReturn(OWNER_AFFINITY);

        final MessageConsumer handler = newStream(2L, "thread-1", "weather");
        final BeginFW begin = begin(2L, "thread-1", "weather");
        handler.accept(BeginFW.TYPE_ID, begin.buffer(), begin.offset(), begin.sizeof());

        assertThat(redirects.get(0).affinity(), equalTo(OWNER_AFFINITY));
    }

    @Test
    public void shouldRejectRunForUnknownBinding()
    {
        assertThat(newStream(99L, "thread-1", "weather"), nullValue());
    }

    private MessageConsumer newStream(
        long routedId,
        String threadId,
        String agent)
    {
        final BeginFW begin = begin(routedId, threadId, agent);
        final MessageConsumer sender = (msgTypeId, buffer, index, length) ->
            redirects.add(new RedirectFW().wrap(copy(buffer, index, length), 0, length));

        return factory.newStream(BeginFW.TYPE_ID, begin.buffer(), begin.offset(), begin.sizeof(), sender);
    }

    private BeginFW begin(
        long routedId,
        String threadId,
        String agent)
    {
        final AguiBeginExFW beginEx = new AguiBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(new byte[256]), 0, 256)
            .typeId(AGUI_TYPE_ID)
            .threadId(threadId)
            .runId("run-1")
            .agent(agent)
            .build();

        return new BeginFW.Builder()
            .wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(10L)
            .routedId(routedId)
            .streamId(11L)
            .sequence(0L)
            .acknowledge(0L)
            .maximum(0)
            .traceId(12L)
            .authorization(0L)
            .affinity(0L)
            .extension(beginEx.buffer(), beginEx.offset(), beginEx.sizeof())
            .build();
    }

    private static DirectBufferEx copy(
        DirectBufferEx buffer,
        int index,
        int length)
    {
        final byte[] bytes = new byte[length];
        buffer.getBytes(index, bytes);
        return new UnsafeBufferEx(bytes);
    }
}
