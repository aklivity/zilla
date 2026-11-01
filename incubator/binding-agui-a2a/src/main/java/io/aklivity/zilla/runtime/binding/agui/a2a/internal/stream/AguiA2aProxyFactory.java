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

import java.security.SecureRandom;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.AguiA2aConfiguration;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.config.AguiA2aBindingConfig;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.config.AguiA2aRouteConfig;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.event.AguiA2aEventContext;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aKeyHash;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aLocalOwnerState;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aOwnerState;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiBeginExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.RedirectFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class AguiA2aProxyFactory implements BindingHandler
{
    private static final String AGUI_TYPE_NAME = "agui";

    private final BeginFW beginRO = new BeginFW();
    private final AguiBeginExFW aguiBeginExRO = new AguiBeginExFW();

    private final RedirectFW.Builder redirectRW = new RedirectFW.Builder();

    private final AguiA2aConfiguration config;
    private final EngineContext context;
    private final MutableDirectBufferEx writeBuffer;
    private final Long2ObjectHashMap<AguiA2aBindingConfig> bindings;
    private final AguiA2aEventContext events;
    private final AguiA2aOwnerState state;
    private final int aguiTypeId;
    private final long affinity;

    public AguiA2aProxyFactory(
        AguiA2aConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.bindings = new Long2ObjectHashMap<>();
        this.events = new AguiA2aEventContext(context);
        this.aguiTypeId = context.supplyTypeId(AGUI_TYPE_NAME);
        this.affinity = context.affinity();
        this.state = new AguiA2aLocalOwnerState(
            context.signaler(),
            () -> context.clock().millis(),
            new SecureRandom(),
            config.threadTtl().toMillis(),
            config.handleTtl().toMillis(),
            config.threadCapacity());
    }

    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, new AguiA2aBindingConfig(binding, context::supplyGuard));
    }

    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routedId = begin.routedId();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();

        MessageConsumer newStream = null;

        final AguiBeginExFW beginEx = aguiBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
        final AguiBeginExFW aguiBeginEx = beginEx != null && beginEx.typeId() == aguiTypeId ? beginEx : null;

        final AguiA2aBindingConfig binding = bindings.get(routedId);
        final String agent = aguiBeginEx != null && aguiBeginEx.agent() != null ? aguiBeginEx.agent().asString() : null;
        final AguiA2aRouteConfig route = binding != null && aguiBeginEx != null
            ? binding.resolveRoute(authorization, agent)
            : null;

        if (route != null)
        {
            final long keyHash = AguiA2aKeyHash.hash(
                route.resolveIdentity(authorization),
                route.resolveAgent(agent),
                aguiBeginEx.threadId().value(),
                0,
                aguiBeginEx.threadId().length());
            final long ownerAffinity = context.resolveAffinityId(keyHash);

            if (ownerAffinity != affinity)
            {
                newStream = new AguiA2aRedirectHandler(sender, binding.id, agent, ownerAffinity)::onAppMessage;
            }
        }

        return newStream;
    }

    private final class AguiA2aRedirectHandler
    {
        private final MessageConsumer sender;
        private final long bindingId;
        private final String agent;
        private final long ownerAffinity;

        private AguiA2aRedirectHandler(
            MessageConsumer sender,
            long bindingId,
            String agent,
            long ownerAffinity)
        {
            this.sender = sender;
            this.bindingId = bindingId;
            this.agent = agent;
            this.ownerAffinity = ownerAffinity;
        }

        private void onAppMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onAppBegin(begin);
                break;
            case AbortFW.TYPE_ID:
            case DataFW.TYPE_ID:
            case FlushFW.TYPE_ID:
            case EndFW.TYPE_ID:
            case ResetFW.TYPE_ID:
            case WindowFW.TYPE_ID:
            case ChallengeFW.TYPE_ID:
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            doRedirect(sender, begin.originId(), begin.routedId(), begin.streamId(), begin.sequence(),
                begin.acknowledge(), traceId, begin.authorization(), ownerAffinity, begin.extension());

            events.runRedirected(traceId, bindingId, agent, ownerAffinity);
        }
    }

    private void doRedirect(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        final RedirectFW redirect = redirectRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(0)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        sender.accept(redirect.typeId(), redirect.buffer(), redirect.offset(), redirect.sizeof());
    }
}
