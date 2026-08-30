/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.echo.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongUnaryOperator;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.binding.echo.EchoOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.echo.internal.EchoConfiguration;
import io.aklivity.zilla.runtime.binding.echo.internal.EchoRouter;
import io.aklivity.zilla.runtime.binding.echo.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.echo.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.echo.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.echo.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.echo.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.echo.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.echo.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.echo.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.echo.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

public final class EchoServerFactory implements BindingHandler
{
    private static final int FLAGS_COMPLETE = 0x03;

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);
    private static final DirectBufferEx EMPTY_SRC = new UnsafeBufferEx(new byte[0]);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ChallengeFW challengeRO = new ChallengeFW();

    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx modelBuffer;
    private final LongUnaryOperator supplyReplyId;
    private final EngineContext context;

    private final EchoRouter router;
    private final Long2ObjectHashMap<ModelHandler> models;

    public EchoServerFactory(
        EchoConfiguration config,
        EngineContext context,
        EchoRouter router)
    {
        this.writeBuffer = requireNonNull(context.writeBuffer());
        this.modelBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.supplyReplyId = context::supplyReplyId;
        this.context = context;
        this.router = router;
        this.models = new Long2ObjectHashMap<>();
    }

    public void detach(
        long bindingId)
    {
        models.remove(bindingId);
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

        final BindingConfig binding = router.resolve(routedId, authorization);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final long initialId = begin.streamId();
            final ModelHandler model = binding.options instanceof EchoOptionsConfig options && options.value != null
                ? models.computeIfAbsent(binding.id, id -> context.supplyModel(options.value))
                : null;

            newStream = new EchoServer(
                    sender,
                    initialId,
                    model)::onMessage;
        }

        return newStream;
    }

    private final class EchoServer
    {
        private final MessageConsumer receiver;
        private final long initialId;
        private final long replyId;
        private final ModelPipeline pipeline;
        private final Deque<PendingEcho> pending;

        private PendingEcho active;

        private EchoServer(
            MessageConsumer receiver,
            long initialId,
            ModelHandler model)
        {
            this.receiver = receiver;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.pipeline = model != null
                ? model.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, this::onResumed)
                : null;
            this.pending = pipeline != null ? new ArrayDeque<>() : null;
        }

        private void onMessage(
            final int msgTypeId,
            final DirectBufferEx buffer,
            final int index,
            final int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onWindow(window);
                break;
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onChallenge(challenge);
                break;
            default:
                // ignore
                break;
            }
        }

        private void onBegin(
            final BeginFW begin)
        {
            final long originId = begin.originId();
            final long routedId = begin.routedId();
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();
            final OctetsFW extension = begin.extension();

            doBegin(receiver, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
                    authorization, affinity, extension);
        }

        private void onData(
            final DataFW data)
        {
            final long originId = data.originId();
            final long routedId = data.routedId();
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final int maximum = data.maximum();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            if (pipeline == null)
            {
                doData(receiver, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
                        authorization, flags, budgetId, reserved, payload, extension);
            }
            else
            {
                final String text = asText(payload);
                final PendingEcho message = new PendingEcho(originId, routedId, sequence, acknowledge, maximum,
                        traceId, authorization, flags, budgetId, reserved, text);

                if (active != null)
                {
                    pending.add(message);
                }
                else
                {
                    process(message);
                }
            }
        }

        private void process(
            PendingEcho message)
        {
            active = message;

            final byte[] bytes = message.text.getBytes(UTF_8);
            final DirectBufferEx src = new UnsafeBufferEx(bytes);

            advance(pipeline.transform(message.traceId, message.routedId, message.authorization, FLAGS_COMPLETE,
                    src, 0, bytes.length, modelBuffer, 0, modelBuffer.capacity()));
        }

        private void onResumed()
        {
            final PendingEcho message = active;

            advance(pipeline.transform(message.traceId, message.routedId, message.authorization, 0x00,
                    EMPTY_SRC, 0, 0, modelBuffer, 0, modelBuffer.capacity()));
        }

        private void advance(
            ModelPipelineResult result)
        {
            final ModelStatus status = result.status();
            final PendingEcho message = active;

            if (status == ModelStatus.REJECTED)
            {
                pipeline.reset();
                active = null;
                doEnd(receiver, message.originId, message.routedId, replyId, message.sequence, message.acknowledge,
                        message.maximum, message.traceId, message.authorization, EMPTY_OCTETS);
                processNext();
            }
            else if (status == ModelStatus.COMPLETE)
            {
                pipeline.reset();
                active = null;
                doData(receiver, message.originId, message.routedId, replyId, message.sequence, message.acknowledge,
                        message.maximum, message.traceId, message.authorization, message.flags, message.budgetId,
                        message.reserved, message.text, EMPTY_OCTETS);
                processNext();
            }
            else if (status == ModelStatus.OVERFLOW)
            {
                advance(pipeline.transform(message.traceId, message.routedId, message.authorization, 0x00,
                        EMPTY_SRC, 0, 0, modelBuffer, 0, modelBuffer.capacity()));
            }
            // SUSPENDED and UNDERFLOW both wait: SUSPENDED resumes via the model's resume callback,
            // UNDERFLOW never resolves since FLAGS_COMPLETE already offered every available byte
        }

        private void processNext()
        {
            final PendingEcho next = pending.poll();
            if (next != null)
            {
                process(next);
            }
        }

        private void onFlush(
            final FlushFW flush)
        {
            final long originId = flush.originId();
            final long routedId = flush.routedId();
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            doFlush(receiver, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
                    authorization, budgetId, reserved, extension);
        }

        private void onEnd(
            final EndFW end)
        {
            final long originId = end.originId();
            final long routedId = end.routedId();
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final int maximum = end.maximum();
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            final OctetsFW extension = end.extension();

            doEnd(receiver, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
                    authorization, extension);
        }

        private void onAbort(
            final AbortFW abort)
        {
            final long originId = abort.originId();
            final long routedId = abort.routedId();
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final int maximum = abort.maximum();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final OctetsFW extension = abort.extension();

            doAbort(receiver, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
                    authorization, extension);
        }

        private void onReset(
            final ResetFW reset)
        {
            final long originId = reset.originId();
            final long routedId = reset.routedId();
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final OctetsFW extension = reset.extension();

            doReset(receiver, originId, routedId, initialId, sequence, acknowledge, maximum, traceId,
                    authorization, extension);
        }

        private void onWindow(
            final WindowFW window)
        {
            final long originId = window.originId();
            final long routedId = window.routedId();
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            doWindow(receiver, originId, routedId, initialId, sequence, acknowledge, maximum, traceId,
                    budgetId, padding);
        }

        private void onChallenge(
            ChallengeFW challenge)
        {
            final long originId = challenge.originId();
            final long routedId = challenge.routedId();
            final long sequence = challenge.sequence();
            final long acknowledge = challenge.acknowledge();
            final int maximum = challenge.maximum();
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            doChallenge(receiver, originId, routedId, initialId, sequence, acknowledge, maximum, traceId,
                    authorization, extension);
        }
    }

    private static final class PendingEcho
    {
        private final long originId;
        private final long routedId;
        private final long sequence;
        private final long acknowledge;
        private final int maximum;
        private final long traceId;
        private final long authorization;
        private final int flags;
        private final long budgetId;
        private final int reserved;
        private final String text;

        private PendingEcho(
            long originId,
            long routedId,
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            String text)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.sequence = sequence;
            this.acknowledge = acknowledge;
            this.maximum = maximum;
            this.traceId = traceId;
            this.authorization = authorization;
            this.flags = flags;
            this.budgetId = budgetId;
            this.reserved = reserved;
            this.text = text;
        }
    }

    private static String asText(
        OctetsFW payload)
    {
        final DirectBufferEx buffer = payload.buffer();
        final int length = payload.sizeof();
        final byte[] bytes = new byte[length];
        buffer.getBytes(payload.offset(), bytes);

        return new String(bytes, UTF_8);
    }

    private void doBegin(
        final MessageConsumer receiver,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final long affinity,
        final OctetsFW extension)
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
        final MessageConsumer receiver,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final int flags,
        final long budgetId,
        final int reserved,
        final OctetsFW payload,
        final OctetsFW extension)
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

    private void doData(
        final MessageConsumer receiver,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final int flags,
        final long budgetId,
        final int reserved,
        final String text,
        final OctetsFW extension)
    {
        final byte[] bytes = text.getBytes(UTF_8);
        final DirectBufferEx payload = new UnsafeBufferEx(bytes);

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
                .payload(payload, 0, bytes.length)
                .extension(extension)
                .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doFlush(
        final MessageConsumer receiver,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final long budgetId,
        final int reserved,
        final OctetsFW extension)
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

    private void doAbort(
        final MessageConsumer receiver,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final OctetsFW extension)
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

    private void doEnd(
        final MessageConsumer receiver,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final OctetsFW extension)
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

    private void doReset(
        final MessageConsumer sender,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final OctetsFW extension)
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

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doWindow(
        final MessageConsumer sender,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long budgetId,
        final int padding)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .budgetId(budgetId)
                .padding(padding)
                .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doChallenge(
        final MessageConsumer sender,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final OctetsFW extension)
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

        sender.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
    }
}
