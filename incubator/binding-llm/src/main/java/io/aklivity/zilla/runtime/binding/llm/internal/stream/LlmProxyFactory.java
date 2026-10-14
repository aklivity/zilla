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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongUnaryOperator;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmGuardrail;
import io.aklivity.zilla.runtime.binding.llm.config.LlmGuardrailStage;
import io.aklivity.zilla.runtime.binding.llm.config.LlmRouteConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmBinding;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;
import io.aklivity.zilla.runtime.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmAbortExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmChallengeExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmResetExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.util.Flags;

// Routes purely on LlmBeginEx.dialect/model, both already resolved and populated by the llm(server)
// hop that produced this stream's own begin extension -- unlike llm(server)/llm(client), this never
// decodes or re-encodes request/response content: DATA/FLUSH/END/ABORT/RESET/WINDOW/CHALLENGE relay
// verbatim between the resolved exit and the accepted network stream once the route is picked.
//
// The sections of a request and of its response pass through the guardrail stages configured for the requested
// model, followed by those of a fallback model once one is taken. The stages that name a section type are
// chained in configuration order. A validating chain leaves the bytes unchanged, so the original bytes continue;
// a chain with a transforming stage forwards what it produces, within the credit the other side has granted.
// The first section a stage rejects aborts the stream toward the sender's peer and resets or aborts the stream
// back with an error naming that section.
//
// A route with a fallback list is resolved when the stream begins and locks as soon as the exit sends a
// window: until then, an exit that resets the stream is retried with the next fallback model, and the
// network stream is challenged with that model so the request carries it.
public final class LlmProxyFactory implements LlmStreamFactory
{
    private static final DirectBufferEx EMPTY_SOURCE = new UnsafeBufferEx(new byte[0]);
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_SOURCE, 0, 0);
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int NO_USAGE = -1;
    private static final String ERROR_TYPE_INVALID_REQUEST = "invalid_request_error";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final LlmBeginExFW llmBeginExRO = new LlmBeginExFW();
    private final LlmDataExFW llmDataExRO = new LlmDataExFW();
    private final OctetsFW fallbackOctetsRO = new OctetsFW();
    private final OctetsFW heldPayloadRO = new OctetsFW();
    private final OctetsFW heldExtensionRO = new OctetsFW();
    private final OctetsFW forwardPayloadRO = new OctetsFW();
    private final OctetsFW forwardExtensionRO = new OctetsFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();
    private final LlmBeginExFW.Builder llmBeginExRW = new LlmBeginExFW.Builder();
    private final LlmChallengeExFW.Builder llmChallengeExRW = new LlmChallengeExFW.Builder();
    private final LlmResetExFW.Builder llmResetExRW = new LlmResetExFW.Builder();
    private final LlmAbortExFW.Builder llmAbortExRW = new LlmAbortExFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx guardrailBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;
    private final int llmTypeId;
    private final EngineContext context;
    private final Long2ObjectHashMap<LlmBindingConfig> bindings;

    public LlmProxyFactory(
        LlmConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = requireNonNull(context.writeBuffer());
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.guardrailBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.llmTypeId = context.supplyTypeId(LlmBinding.NAME);
        this.context = context;
        this.bindings = new Long2ObjectHashMap<>();
    }

    @Override
    public int originTypeId()
    {
        return llmTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, new LlmBindingConfig(binding, context));
    }

    @Override
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
        MessageConsumer network)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routedId = begin.routedId();
        final long authorization = begin.authorization();

        final LlmBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final OctetsFW extension = begin.extension();
            final LlmBeginExFW llmBeginEx = extension.get(llmBeginExRO::tryWrap);

            if (llmBeginEx != null)
            {
                final String dialect = llmBeginEx.dialect().asString();
                final String model = llmBeginEx.model().asString();

                final LlmRouteConfig route = binding.resolve(authorization, dialect, model);

                if (route != null)
                {
                    newStream = new LlmServer(
                            network,
                            begin.originId(),
                            routedId,
                            begin.streamId(),
                            route.id,
                            binding,
                            dialect,
                            route.fallback,
                            binding.guardrails(model))::onNetMessage;
                }
            }
        }

        return newStream;
    }

    private final class LlmServer
    {
        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final LlmBindingConfig binding;
        private final String dialect;
        private final List<String> fallback;
        private final LlmGuardrail requestedGuardrail;

        private LlmGuardrail guardrail;
        private LlmSectionGuard requestGuard;

        private long exitId;

        private LlmClient app;

        private byte[] beginExtension;
        private long beginAffinity;
        private long beginAuthorization;
        private int fallbackIndex;

        private long replyBudgetId;
        private int replyPadding;
        private int replyMinimum;
        private int replyCapabilities;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private LlmServer(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            long exitId,
            LlmBindingConfig binding,
            String dialect,
            List<String> fallback,
            LlmGuardrail guardrail)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.exitId = exitId;
            this.binding = binding;
            this.dialect = dialect;
            this.fallback = fallback;
            this.requestedGuardrail = guardrail;
            this.guardrail = guardrail;
            this.requestGuard = guardrail.request.isEmpty() ? null : new LlmRequestGuard(guardrail.request);
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onNetBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onNetData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onNetEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onNetAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case FlushFW.TYPE_ID:
                onNetFlush(flushRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onNetWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onNetReset(resetRO.wrap(buffer, index, index + length));
                break;
            case ChallengeFW.TYPE_ID:
                onNetChallenge(challengeRO.wrap(buffer, index, index + length));
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            this.initialSeq = begin.sequence();
            this.initialAck = begin.acknowledge();
            this.state = LlmState.openingInitial(state);

            if (fallback != null)
            {
                final OctetsFW extension = begin.extension();
                this.beginExtension = new byte[extension.sizeof()];
                extension.buffer().getBytes(extension.offset(), beginExtension);
                this.beginAffinity = affinity;
                this.beginAuthorization = authorization;
            }

            this.app = new LlmClient(this);
            app.doAppBegin(traceId, authorization, affinity, begin.extension());
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            this.initialSeq = data.sequence() + reserved;

            if (requestGuard != null)
            {
                requestGuard.data(traceId, authorization, flags, budgetId, reserved, data.payload(), data.extension());
            }
            else
            {
                app.doAppData(traceId, authorization, flags, budgetId, reserved, data.payload(), data.extension());
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            this.initialSeq = end.sequence();
            this.state = LlmState.closeInitial(state);

            if (requestGuard != null)
            {
                requestGuard.end(traceId, authorization, end.extension());
            }
            else
            {
                app.doAppEnd(traceId, authorization, end.extension());
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            this.initialSeq = abort.sequence();
            this.state = LlmState.closeInitial(state);

            if (requestGuard != null)
            {
                requestGuard.close();
            }

            app.doAppAbort(traceId, authorization, abort.extension());
        }

        private void onNetFlush(
            FlushFW flush)
        {
            if (requestGuard != null)
            {
                requestGuard.flush(flush.traceId(), flush.authorization(), flush.budgetId(), flush.reserved(),
                    flush.extension());
            }
            else
            {
                app.doAppFlush(flush.traceId(), flush.authorization(), flush.budgetId(), flush.reserved(), flush.extension());
            }
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int minimum = window.minimum();
            final int capabilities = window.capabilities();

            this.replyAck = window.acknowledge();
            this.replyMax = window.maximum();
            this.state = LlmState.openReply(state);

            this.replyBudgetId = budgetId;
            this.replyPadding = padding;
            this.replyMinimum = minimum;
            this.replyCapabilities = capabilities;

            if (app.replyGuard != null)
            {
                app.replyGuard.onCredit(traceId, authorization);
            }
            else
            {
                doAppReplyWindow(traceId, authorization);
            }
        }

        private void doAppReplyWindow(
            long traceId,
            long authorization)
        {
            final int replyWin = replyMax - (int)(replySeq - replyAck) - app.heldReplyBytes();
            if (replyWin > 0)
            {
                app.doAppWindow(traceId, authorization, replyBudgetId, replyMinimum,
                    replyCapabilities, replyWin, replyPadding, replyMax);
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            this.state = LlmState.closeReply(state);

            app.doAppReset(reset.traceId(), reset.authorization(), reset.extension());
        }

        private void onNetChallenge(
            ChallengeFW challenge)
        {
            app.doAppChallenge(challenge.traceId(), challenge.authorization(), challenge.extension());
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long affinity,
            OctetsFW extension)
        {
            doBegin(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, extension);
            this.state = LlmState.openingReply(state);
        }

        private void doNetData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW payload,
            OctetsFW extension)
        {
            doData(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, flags, budgetId, reserved, payload, extension);
            this.replySeq += reserved;
        }

        private void doNetEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.replyClosed(state))
            {
                doEnd(network, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, extension);
                this.state = LlmState.closeReply(state);
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.replyClosed(state))
            {
                doAbort(network, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, extension);
                this.state = LlmState.closeReply(state);
            }
        }

        private void doNetFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved, extension);
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int minimum,
            int capabilities,
            int minInitialWin,
            int minInitialPad,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialSeq - (minInitialMax - minInitialWin), initialAck);

            if (newInitialAck > initialAck || minInitialMax > initialMax)
            {
                this.initialAck = newInitialAck;
                this.initialMax = minInitialMax;
                this.state = LlmState.openInitial(state);

                doWindow(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, minInitialPad, minimum, capabilities);
            }
        }

        private boolean doFallback(
            long traceId,
            long authorization)
        {
            LlmRouteConfig route = null;
            String model = null;

            while (route == null &&
                !LlmState.initialClosed(state) &&
                fallback != null &&
                fallbackIndex < fallback.size())
            {
                model = fallback.get(fallbackIndex++);
                route = binding.resolve(beginAuthorization, dialect, model);
            }

            if (route != null)
            {
                doNetModelChallenge(traceId, authorization, model);

                this.exitId = route.id;
                this.guardrail = requestedGuardrail.then(binding.guardrails(model));
                this.requestGuard = guardrail.request.isEmpty() ? null : new LlmRequestGuard(guardrail.request);
                this.app = new LlmClient(this);
                app.doAppBegin(traceId, authorization, beginAffinity, fallbackBeginEx(model));

                if (LlmState.replyOpened(state))
                {
                    doAppReplyWindow(traceId, authorization);
                }
            }

            return route != null;
        }

        private OctetsFW fallbackBeginEx(
            String model)
        {
            final UnsafeBufferEx buffer = new UnsafeBufferEx(beginExtension);
            final LlmBeginExFW original = llmBeginExRO.wrap(buffer, 0, buffer.capacity());

            final LlmBeginExFW.Builder builder = llmBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(original.typeId())
                .dialect(original.dialect().asString());

            final String contentType = original.contentType().asString();
            if (contentType != null)
            {
                builder.contentType(contentType);
            }

            builder.model(model);

            final String id = original.id().asString();
            if (id != null)
            {
                builder.id(id);
            }

            final OctetsFW extension = original.extension();
            final LlmBeginExFW llmBeginEx = builder
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

            return fallbackOctetsRO.wrap(llmBeginEx.buffer(), llmBeginEx.offset(), llmBeginEx.limit());
        }

        private void doNetModelChallenge(
            long traceId,
            long authorization,
            String model)
        {
            final LlmChallengeExFW llmChallengeEx = llmChallengeExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .dialect(dialect)
                .model(model)
                .build();

            doNetChallenge(traceId, authorization,
                fallbackOctetsRO.wrap(llmChallengeEx.buffer(), llmChallengeEx.offset(), llmChallengeEx.limit()));
        }

        private int heldRequestBytes()
        {
            return requestGuard != null ? requestGuard.heldBytes : 0;
        }

        private void doRejectRequest(
            long traceId,
            long authorization,
            String section,
            String param)
        {
            final LlmResetExFW llmResetEx = llmResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .error(e -> e.status(STATUS_BAD_REQUEST)
                             .type(ERROR_TYPE_INVALID_REQUEST)
                             .param(param)
                             .message(section + " rejected"))
                .build();

            app.doAppAbort(traceId, authorization, EMPTY_OCTETS);
            doNetReset(traceId, authorization,
                fallbackOctetsRO.wrap(llmResetEx.buffer(), llmResetEx.offset(), llmResetEx.limit()));
        }

        private void doNetReset(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.initialClosed(state))
            {
                doReset(network, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                    extension);
                this.state = LlmState.closeInitial(state);
            }
        }

        private void doNetChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.initialOpened(state))
            {
                this.state = LlmState.openInitial(state);

                doWindow(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, 0L, 0, 0, 0);
            }

            doChallenge(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);
        }

        private final class LlmRequestGuard extends LlmSectionGuard
        {
            private LlmRequestGuard(
                List<LlmGuardrailStage> stages)
            {
                super(routedId, stages);
            }

            @Override
            void doForwardData(
                long traceId,
                long authorization,
                int flags,
                long budgetId,
                int reserved,
                OctetsFW payload,
                OctetsFW extension)
            {
                app.doAppData(traceId, authorization, flags, budgetId, reserved, payload, extension);
            }

            @Override
            void doForwardEnd(
                long traceId,
                long authorization,
                OctetsFW extension)
            {
                app.doAppEnd(traceId, authorization, extension);
            }

            @Override
            void doForwardFlush(
                long traceId,
                long authorization,
                long budgetId,
                int reserved,
                OctetsFW extension)
            {
                app.doAppFlush(traceId, authorization, budgetId, reserved, extension);
            }

            @Override
            void doReject(
                long traceId,
                long authorization,
                String section,
                String param)
            {
                doRejectRequest(traceId, authorization, section, param);
            }

            @Override
            int credit()
            {
                return app.initialMax - (int)(app.initialSeq - app.initialAck);
            }

            @Override
            int padding()
            {
                return app.initialPadding;
            }

            @Override
            void refreshWindow(
                long traceId,
                long authorization)
            {
                app.refreshNetWindow(traceId, authorization);
            }
        }
    }

    private final class LlmClient
    {
        private final LlmServer net;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final LlmSectionGuard replyGuard;

        private MessageConsumer app;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBudgetId;
        private int initialPadding;
        private int initialMinimum;
        private int initialCapabilities;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private LlmClient(
            LlmServer net)
        {
            this.net = net;
            this.originId = net.routedId;
            this.routedId = net.exitId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.replyGuard = net.guardrail.response.isEmpty() ? null : new LlmReplyGuard(net.guardrail.response);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            long affinity,
            OctetsFW extension)
        {
            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(extension)
                .build();

            this.app = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(),
                this::onAppMessage);
            app.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

            this.state = LlmState.openingInitial(state);
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
                onAppBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onAppData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onAppEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onAppAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case FlushFW.TYPE_ID:
                onAppFlush(flushRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onAppWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onAppReset(resetRO.wrap(buffer, index, index + length));
                break;
            case ChallengeFW.TYPE_ID:
                onAppChallenge(challengeRO.wrap(buffer, index, index + length));
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            this.replySeq = begin.sequence();
            this.replyAck = begin.acknowledge();
            this.state = LlmState.openingReply(state);

            net.doNetBegin(traceId, authorization, affinity, begin.extension());
        }

        private void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            this.replySeq = data.sequence() + reserved;

            if (replyGuard != null)
            {
                replyGuard.data(traceId, authorization, flags, budgetId, reserved, data.payload(), data.extension());
            }
            else
            {
                net.doNetData(traceId, authorization, flags, budgetId, reserved, data.payload(), data.extension());
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            this.replySeq = end.sequence();
            this.state = LlmState.closeReply(state);

            if (replyGuard != null)
            {
                replyGuard.end(traceId, authorization, end.extension());
            }
            else
            {
                net.doNetEnd(traceId, authorization, end.extension());
            }
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            this.replySeq = abort.sequence();
            this.state = LlmState.closeReply(state);

            if (replyGuard != null)
            {
                replyGuard.close();
            }

            net.doNetAbort(traceId, authorization, abort.extension());
        }

        private void onAppFlush(
            FlushFW flush)
        {
            if (replyGuard != null)
            {
                replyGuard.flush(flush.traceId(), flush.authorization(), flush.budgetId(), flush.reserved(),
                    flush.extension());
            }
            else
            {
                net.doNetFlush(flush.traceId(), flush.authorization(), flush.budgetId(), flush.reserved(), flush.extension());
            }
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int minimum = window.minimum();
            final int capabilities = window.capabilities();

            this.initialAck = window.acknowledge();
            this.initialMax = window.maximum();
            this.state = LlmState.openInitial(state);

            this.initialBudgetId = budgetId;
            this.initialPadding = padding;
            this.initialMinimum = minimum;
            this.initialCapabilities = capabilities;

            if (net.requestGuard != null)
            {
                net.requestGuard.onCredit(traceId, authorization);
            }
            else
            {
                refreshNetWindow(traceId, authorization);
            }
        }

        private void refreshNetWindow(
            long traceId,
            long authorization)
        {
            final int initialWin = initialMax - (int)(initialSeq - initialAck) - net.heldRequestBytes();

            if (initialWin > 0)
            {
                net.doNetWindow(traceId, authorization, initialBudgetId, initialMinimum, initialCapabilities,
                    initialWin, initialPadding, initialMax);
            }
        }

        private void onAppReset(
            ResetFW reset)
        {
            final boolean refused = !LlmState.initialOpened(state);

            this.state = LlmState.closeInitial(state);

            if (!refused || !net.doFallback(reset.traceId(), reset.authorization()))
            {
                net.doNetReset(reset.traceId(), reset.authorization(), reset.extension());
            }
        }

        private void onAppChallenge(
            ChallengeFW challenge)
        {
            net.doNetChallenge(challenge.traceId(), challenge.authorization(), challenge.extension());
        }

        private void doAppData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW payload,
            OctetsFW extension)
        {
            doData(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, flags, budgetId, reserved, payload, extension);
            this.initialSeq += reserved;
        }

        private void doAppEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.initialClosed(state))
            {
                doEnd(app, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                        extension);
                this.state = LlmState.closeInitial(state);
            }
        }

        private void doAppAbort(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.initialClosed(state))
            {
                doAbort(app, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                        extension);
                this.state = LlmState.closeInitial(state);
            }
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, reserved, extension);
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int minimum,
            int capabilities,
            int minReplyWin,
            int minReplyPad,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(replySeq - (minReplyMax - minReplyWin), replyAck);

            if (newReplyAck > replyAck || minReplyMax > replyMax)
            {
                this.replyAck = newReplyAck;
                this.replyMax = minReplyMax;

                doWindow(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, budgetId, minReplyPad, minimum, capabilities);
            }
        }

        private void doAppReset(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.replyClosed(state))
            {
                doReset(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, extension);
                this.state = LlmState.closeReply(state);
            }
        }

        private void doAppChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            doChallenge(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, extension);
        }

        private int heldReplyBytes()
        {
            return replyGuard != null ? replyGuard.heldBytes : 0;
        }

        private void doRejectResponse(
            long traceId,
            long authorization,
            String section,
            String param)
        {
            final String message = section + " rejected";

            final LlmAbortExFW llmAbortEx = llmAbortExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .usage(u -> u.inputTokens(NO_USAGE))
                .error(e -> e.status(STATUS_BAD_REQUEST)
                             .type(ERROR_TYPE_INVALID_REQUEST)
                             .param(param)
                             .message(message))
                .build();

            net.doNetAbort(traceId, authorization,
                fallbackOctetsRO.wrap(llmAbortEx.buffer(), llmAbortEx.offset(), llmAbortEx.limit()));

            final LlmResetExFW llmResetEx = llmResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .error(e -> e.status(STATUS_BAD_REQUEST)
                             .type(ERROR_TYPE_INVALID_REQUEST)
                             .param(param)
                             .message(message))
                .build();

            doAppReset(traceId, authorization,
                fallbackOctetsRO.wrap(llmResetEx.buffer(), llmResetEx.offset(), llmResetEx.limit()));
        }

        private final class LlmReplyGuard extends LlmSectionGuard
        {
            private LlmReplyGuard(
                List<LlmGuardrailStage> stages)
            {
                super(net.routedId, stages);
            }

            @Override
            void doForwardData(
                long traceId,
                long authorization,
                int flags,
                long budgetId,
                int reserved,
                OctetsFW payload,
                OctetsFW extension)
            {
                net.doNetData(traceId, authorization, flags, budgetId, reserved, payload, extension);
            }

            @Override
            void doForwardEnd(
                long traceId,
                long authorization,
                OctetsFW extension)
            {
                net.doNetEnd(traceId, authorization, extension);
            }

            @Override
            void doForwardFlush(
                long traceId,
                long authorization,
                long budgetId,
                int reserved,
                OctetsFW extension)
            {
                net.doNetFlush(traceId, authorization, budgetId, reserved, extension);
            }

            @Override
            void doReject(
                long traceId,
                long authorization,
                String section,
                String param)
            {
                doRejectResponse(traceId, authorization, section, param);
            }

            @Override
            int credit()
            {
                return net.replyMax - (int)(net.replySeq - net.replyAck);
            }

            @Override
            int padding()
            {
                return net.replyPadding;
            }

            @Override
            void refreshWindow(
                long traceId,
                long authorization)
            {
                net.doAppReplyWindow(traceId, authorization);
            }
        }
    }

    private enum LlmGuardVerdict
    {
        ACCEPTED,
        REJECTED,
        SUSPENDED,
        BLOCKED
    }

    private static final class LlmHeldFrame
    {
        private static final int KIND_DATA = 0;
        private static final int KIND_END = 1;
        private static final int KIND_FLUSH = 2;

        private final int kind;
        private final long traceId;
        private final long authorization;
        private final int flags;
        private final long budgetId;
        private final int reserved;
        private final byte[] payload;
        private final byte[] extension;

        private int consumed;
        private boolean started;

        private LlmHeldFrame(
            int kind,
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW payload,
            OctetsFW extension)
        {
            this.kind = kind;
            this.traceId = traceId;
            this.authorization = authorization;
            this.flags = flags;
            this.budgetId = budgetId;
            this.reserved = reserved;
            this.payload = copyOf(payload);
            this.extension = copyOf(extension);
        }

        private static byte[] copyOf(
            OctetsFW octets)
        {
            final byte[] copy = new byte[octets.sizeof()];
            octets.buffer().getBytes(octets.offset(), copy);
            return copy;
        }
    }

    // Applies the guardrail stages of one direction to the sections flowing through it. The stages that name a
    // section type are chained in configuration order, each applied to what the previous one produced. A chain of
    // validating stages leaves the bytes unchanged, so the original frames continue once the chain accepts them.
    // A chain with a transforming stage emits what it produces instead, in fragments no larger than the credit
    // the destination has granted: the input it has not yet consumed, and every frame received behind it, is held
    // until credit arrives, and the held bytes count against the credit granted to the sender. A stage that
    // suspends holds the frame that raised the question in the same way, until it answers.
    private abstract class LlmSectionGuard
    {
        private final long routedId;
        private final Map<LlmSectionType, ModelPipeline> pipelines;
        private final Deque<LlmHeldFrame> held;

        private ModelPipeline sectionPipeline;
        private LlmSectionType sectionType;
        private int sectionMessage;
        private byte[] sectionExtension;
        private boolean sectionTransforms;
        private boolean outputStarted;
        private boolean suspended;
        private boolean closed;
        private int heldBytes;
        private int progress;
        private long traceId;
        private long authorization;

        private LlmSectionGuard(
            long routedId,
            List<LlmGuardrailStage> stages)
        {
            this.routedId = routedId;
            this.pipelines = new EnumMap<>(LlmSectionType.class);
            this.held = new ArrayDeque<>();

            for (LlmGuardrailStage stage : stages)
            {
                final ModelPipeline pipeline = stage.handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE,
                    this::onResumed);
                stage.sections.forEach(section -> pipelines.merge(section, pipeline, ModelPipeline::andThen));
            }
        }

        abstract void doForwardData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW payload,
            OctetsFW extension);

        abstract void doForwardEnd(
            long traceId,
            long authorization,
            OctetsFW extension);

        abstract void doForwardFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OctetsFW extension);

        abstract void doReject(
            long traceId,
            long authorization,
            String section,
            String param);

        abstract int credit();

        abstract int padding();

        abstract void refreshWindow(
            long traceId,
            long authorization);

        private void data(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW payload,
            OctetsFW extension)
        {
            this.traceId = traceId;
            this.authorization = authorization;

            if (!closed)
            {
                if (suspended || !held.isEmpty())
                {
                    holdLast(new LlmHeldFrame(LlmHeldFrame.KIND_DATA, traceId, authorization, flags, budgetId, reserved,
                        payload, extension));
                }
                else
                {
                    final LlmGuardVerdict verdict = process(traceId, authorization, flags, budgetId, reserved,
                        payload.buffer(), payload.offset(), payload.offset(), payload.limit(), true, extension);

                    switch (verdict)
                    {
                    case REJECTED:
                        reject(traceId, authorization);
                        break;
                    case SUSPENDED:
                    case BLOCKED:
                        final LlmHeldFrame frame = new LlmHeldFrame(LlmHeldFrame.KIND_DATA, traceId, authorization, flags,
                            budgetId, reserved, payload, extension);
                        frame.consumed = progress - payload.offset();
                        frame.started = true;
                        suspended = verdict == LlmGuardVerdict.SUSPENDED;
                        holdFirst(frame);
                        break;
                    default:
                        break;
                    }

                    refreshWindow(traceId, authorization);
                }
            }
        }

        private void end(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (suspended || !held.isEmpty())
            {
                holdLast(new LlmHeldFrame(LlmHeldFrame.KIND_END, traceId, authorization, 0, 0L, 0,
                    EMPTY_OCTETS, extension));
            }
            else if (!closed)
            {
                doForwardEnd(traceId, authorization, extension);
            }
        }

        private void flush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            if (suspended || !held.isEmpty())
            {
                holdLast(new LlmHeldFrame(LlmHeldFrame.KIND_FLUSH, traceId, authorization, 0, budgetId, reserved,
                    EMPTY_OCTETS, extension));
            }
            else if (!closed)
            {
                doForwardFlush(traceId, authorization, budgetId, reserved, extension);
            }
        }

        private void close()
        {
            this.closed = true;
            held.clear();
            this.heldBytes = 0;
        }

        private LlmGuardVerdict process(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int start,
            int offset,
            int limit,
            boolean initial,
            OctetsFW extension)
        {
            if (initial && Flags.hasInit(flags))
            {
                selectSection(extension);
            }

            final int fragmentFlags = initial ? flags : flags & ~Flags.INIT;

            LlmGuardVerdict verdict;
            if (sectionPipeline == null)
            {
                this.progress = limit;
                verdict = LlmGuardVerdict.ACCEPTED;
                doForwardData(traceId, authorization, flags, budgetId, reserved,
                    forwardPayloadRO.wrap(buffer, start, limit), extension);
            }
            else if (sectionTransforms)
            {
                verdict = transform(traceId, authorization, fragmentFlags, budgetId, buffer, offset, limit);
            }
            else
            {
                verdict = validate(traceId, authorization, fragmentFlags, buffer, offset, limit);

                if (verdict == LlmGuardVerdict.ACCEPTED)
                {
                    doForwardData(traceId, authorization, flags, budgetId, reserved,
                        forwardPayloadRO.wrap(buffer, start, limit), extension);
                }
            }

            if (verdict == LlmGuardVerdict.ACCEPTED && Flags.hasFin(flags))
            {
                if (sectionPipeline != null)
                {
                    sectionPipeline.reset();
                }

                this.sectionPipeline = null;
            }

            return verdict;
        }

        private void selectSection(
            OctetsFW extension)
        {
            final LlmDataExFW llmDataEx = extension.get(llmDataExRO::tryWrap);

            this.sectionPipeline = null;
            this.sectionTransforms = false;
            this.outputStarted = false;

            if (llmDataEx != null && llmDataEx.kind() == LlmDataExFW.KIND_SECTION)
            {
                final LlmSectionDataExFW section = llmDataEx.section();

                this.sectionType = section.type().get();
                this.sectionMessage = section.message();
                this.sectionPipeline = pipelines.get(sectionType);

                if (sectionPipeline != null)
                {
                    sectionPipeline.reset();
                    this.sectionTransforms = !sectionPipeline.identity();

                    if (sectionTransforms)
                    {
                        this.sectionExtension = new byte[extension.sizeof()];
                        extension.buffer().getBytes(extension.offset(), sectionExtension);
                    }
                }
            }
        }

        private LlmGuardVerdict validate(
            long traceId,
            long authorization,
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            int position = offset;
            int fragmentFlags = flags;
            ModelStatus status;

            do
            {
                final ModelPipelineResult result = sectionPipeline.transform(traceId, routedId, authorization,
                    fragmentFlags, buffer, position, limit, guardrailBuffer, 0, guardrailBuffer.capacity());

                status = result.status();
                position += result.consumed();
                fragmentFlags &= ~Flags.INIT;
            }
            while (status == ModelStatus.OVERFLOW);

            this.progress = position;

            return status == ModelStatus.REJECTED
                ? LlmGuardVerdict.REJECTED
                : status == ModelStatus.SUSPENDED ? LlmGuardVerdict.SUSPENDED : LlmGuardVerdict.ACCEPTED;
        }

        private LlmGuardVerdict transform(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            int position = offset;
            int fragmentFlags = flags;
            LlmGuardVerdict verdict = null;

            while (verdict == null)
            {
                final int outputLimit = Math.min(credit() - padding(), guardrailBuffer.capacity() / 2);

                if (outputLimit <= 0)
                {
                    verdict = LlmGuardVerdict.BLOCKED;
                }
                else
                {
                    final ModelPipelineResult result = sectionPipeline.transform(traceId, routedId, authorization,
                        fragmentFlags, buffer, position, limit, guardrailBuffer, 0, outputLimit);

                    final ModelStatus status = result.status();
                    final int produced = result.produced();

                    position += result.consumed();
                    fragmentFlags &= ~Flags.INIT;

                    if (produced > 0 || status == ModelStatus.COMPLETE)
                    {
                        doForwardOutput(traceId, authorization, budgetId, produced, status == ModelStatus.COMPLETE);
                    }

                    switch (status)
                    {
                    case REJECTED:
                        verdict = LlmGuardVerdict.REJECTED;
                        break;
                    case SUSPENDED:
                        verdict = LlmGuardVerdict.SUSPENDED;
                        break;
                    case OVERFLOW:
                        break;
                    default:
                        verdict = LlmGuardVerdict.ACCEPTED;
                        break;
                    }
                }
            }

            this.progress = position;

            return verdict;
        }

        private void doForwardOutput(
            long traceId,
            long authorization,
            long budgetId,
            int produced,
            boolean complete)
        {
            final int flags = (outputStarted ? 0 : Flags.INIT) | (complete ? Flags.FIN : 0);
            final OctetsFW extension = outputStarted
                ? EMPTY_OCTETS
                : forwardExtensionRO.wrap(new UnsafeBufferEx(sectionExtension), 0, sectionExtension.length);

            doForwardData(traceId, authorization, flags, budgetId, produced + padding(),
                forwardPayloadRO.wrap(guardrailBuffer, 0, produced), extension);

            this.outputStarted = !complete;
        }

        private void onResumed()
        {
            if (suspended && !closed)
            {
                this.suspended = false;
                release();
                refreshWindow(traceId, authorization);
            }
        }

        private void onCredit(
            long traceId,
            long authorization)
        {
            this.traceId = traceId;
            this.authorization = authorization;

            release();
            refreshWindow(traceId, authorization);
        }

        private void release()
        {
            boolean blocked = false;

            while (!blocked && !suspended && !closed && !held.isEmpty())
            {
                final LlmHeldFrame frame = held.peekFirst();

                if (frame.kind == LlmHeldFrame.KIND_DATA)
                {
                    final UnsafeBufferEx payload = new UnsafeBufferEx(frame.payload);
                    final OctetsFW extension = heldExtensionRO.wrap(new UnsafeBufferEx(frame.extension), 0,
                        frame.extension.length);

                    final LlmGuardVerdict verdict = process(frame.traceId, frame.authorization, frame.flags, frame.budgetId,
                        frame.reserved, payload, 0, frame.consumed, frame.payload.length, !frame.started, extension);

                    frame.consumed = progress;
                    frame.started = true;

                    switch (verdict)
                    {
                    case ACCEPTED:
                        held.pollFirst();
                        this.heldBytes -= frame.reserved;
                        break;
                    case REJECTED:
                        reject(frame.traceId, frame.authorization);
                        break;
                    case SUSPENDED:
                        this.suspended = true;
                        break;
                    case BLOCKED:
                        blocked = true;
                        break;
                    }
                }
                else
                {
                    held.pollFirst();
                    this.heldBytes -= frame.reserved;
                    forward(frame);
                }
            }
        }

        private void forward(
            LlmHeldFrame frame)
        {
            final OctetsFW payload = heldPayloadRO.wrap(new UnsafeBufferEx(frame.payload), 0, frame.payload.length);
            final OctetsFW extension = heldExtensionRO.wrap(new UnsafeBufferEx(frame.extension), 0, frame.extension.length);

            switch (frame.kind)
            {
            case LlmHeldFrame.KIND_DATA:
                doForwardData(frame.traceId, frame.authorization, frame.flags, frame.budgetId, frame.reserved,
                    payload, extension);
                break;
            case LlmHeldFrame.KIND_END:
                doForwardEnd(frame.traceId, frame.authorization, extension);
                break;
            case LlmHeldFrame.KIND_FLUSH:
                doForwardFlush(frame.traceId, frame.authorization, frame.budgetId, frame.reserved, extension);
                break;
            }
        }

        private void holdFirst(
            LlmHeldFrame frame)
        {
            held.addFirst(frame);
            this.heldBytes += frame.reserved;
        }

        private void holdLast(
            LlmHeldFrame frame)
        {
            held.addLast(frame);
            this.heldBytes += frame.reserved;
        }

        private void reject(
            long traceId,
            long authorization)
        {
            close();

            final String section = sectionType.name().toLowerCase().replace('_', '-');
            final String param = sectionMessage >= 0 ? String.format("%s[%d]", section, sectionMessage) : section;

            doReject(traceId, authorization, section, param);
        }
    }

    private void doBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        OctetsFW extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(extension)
            .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        OctetsFW payload,
        OctetsFW extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .flags(flags)
            .budgetId(budgetId)
            .reserved(reserved)
            .payload(payload)
            .extension(extension)
            .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        OctetsFW extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension)
            .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        OctetsFW extension)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension)
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doFlush(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        OctetsFW extension)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .reserved(reserved)
            .extension(extension)
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doWindow(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int padding,
        int minimum,
        int capabilities)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .padding(padding)
            .minimum(minimum)
            .capabilities(capabilities)
            .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        OctetsFW extension)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension)
            .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doChallenge(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        OctetsFW extension)
    {
        final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension)
            .build();

        receiver.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
    }
}
