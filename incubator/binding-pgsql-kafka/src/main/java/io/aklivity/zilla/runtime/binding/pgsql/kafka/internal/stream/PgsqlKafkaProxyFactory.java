/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.stream;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.util.Objects.requireNonNull;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.IntArrayQueue;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;

import io.aklivity.zilla.runtime.binding.pgsql.kafka.config.PgsqlKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.config.PgsqlKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.PgsqlKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.stream.PgsqlBeginExFW;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.stream.PgsqlDataExFW;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.stream.PgsqlFlushExFW;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.CreateTable;

public final class PgsqlKafkaProxyFactory implements PgsqlKafkaStreamFactory
{
    private static final Byte STATEMENT_SEMICOLON = ';';

    private static final int END_OF_FIELD = 0x00;

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_CONT = 0x00;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMP = 0x03;

    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer(new byte[0]);
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private final CCJSqlParserManager parserManager = new CCJSqlParserManager();
    private final DirectBufferInputStream inputStream = new DirectBufferInputStream(EMPTY_BUFFER);
    private final Reader reader = new InputStreamReader(inputStream);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final SignalFW signalRO = new SignalFW();
    private final FlushFW flushRO = new FlushFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();

    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final PgsqlBeginExFW pgsqlBeginExRO = new PgsqlBeginExFW();
    private final PgsqlDataExFW pgsqlDataExRO = new PgsqlDataExFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();

    private final PgsqlBeginExFW.Builder beginExRW = new PgsqlBeginExFW.Builder();
    private final PgsqlDataExFW.Builder dataExRW = new PgsqlDataExFW.Builder();
    private final PgsqlFlushExFW.Builder flushExRW = new PgsqlFlushExFW.Builder();

    private final BufferPool bufferPool;
    private final PgsqlKafkaConfiguration config;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer statementBuffer;
    private final MutableDirectBuffer extBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongFunction<CatalogHandler> supplyCatalog;
    private final BindingHandler streamFactory;

    private final int decodeMax;

    private final Long2ObjectHashMap<PgsqlKafkaBindingConfig> bindings;
    private final int pgsqlTypeId;
    private final int kafkaTypeId;

    private final Object2ObjectHashMap<PgsqlKafkaCommandType, PgsqlDecoder> pgsqlDecoder;

    {
        Object2ObjectHashMap<PgsqlKafkaCommandType, PgsqlDecoder> pgsqlDecoder =
            new Object2ObjectHashMap<>();
        pgsqlDecoder.put(PgsqlKafkaCommandType.CREATE_TOPIC_COMMAND, this::onDecodeCreateTableCommand);
        pgsqlDecoder.put(PgsqlKafkaCommandType.UNKNOWN_COMMAND, this::onDecodeUnknownCommand);
        this.pgsqlDecoder = pgsqlDecoder;
    }

    public PgsqlKafkaProxyFactory(
        PgsqlKafkaConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.writeBuffer = requireNonNull(context.writeBuffer());
        this.extBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.statementBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.bufferPool = context.bufferPool();
        this.decodeMax = bufferPool.slotCapacity();
        this.supplyCatalog = context::supplyCatalog;

        this.bindings = new Long2ObjectHashMap<>();

        this.pgsqlTypeId = context.supplyTypeId("pgsql");
        this.kafkaTypeId = context.supplyTypeId("kafka");
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        PgsqlKafkaBindingConfig pgsqlKafkaBinding = new PgsqlKafkaBindingConfig(config, binding, supplyCatalog);
        bindings.put(binding.id, pgsqlKafkaBinding);
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
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer app)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();
        final PgsqlBeginExFW pgsqlBeginEx = extension.get(pgsqlBeginExRO::tryWrap);

        final Map<String, String> parameters = new LinkedHashMap<>();
        pgsqlBeginEx.parameters().forEach(p -> parameters.put(p.name().asString(), p.value().asString()));

        PgsqlKafkaBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            PgsqlKafkaRouteConfig route = binding.resolve(authorization);

            if (route != null)
            {
                newStream = new PgsqlProxy(
                    app,
                    originId,
                    routedId,
                    route.id,
                    initialId,
                    parameters)::onAppMessage;
            }
        }

        return newStream;
    }

    private final class PgsqlProxy
    {
        private final MessageConsumer app;
        private final PgsqlKafkaBindingConfig binding;
        private final Map<String, String> parameters;
        private final IntArrayQueue queries;
        private final String database;

        private final long initialId;
        private final long replyId;
        private final long originId;
        private final long routedId;
        private final KafkaProxy delegate;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialBudgetId;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPadding;
        private long replyBudgetId;

        private int parserSlot = NO_SLOT;
        private int parserSlotOffset;

        private int state;

        private int commandsProcessed = 0;
        private int queryProgressOffset;

        private PgsqlProxy(
            MessageConsumer app,
            long originId,
            long routedId,
            long resolvedId,
            long initialId,
            Map<String, String> parameters)
        {
            this.app = app;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.initialMax = decodeMax;
            this.binding = bindings.get(routedId);
            this.parameters = parameters;

            String dbValue = parameters.get("database\u0000");
            this.database = dbValue.substring(0, dbValue.length() - 1);
            this.queries = new IntArrayQueue();

            this.delegate = new KafkaProxy(this, routedId, resolvedId);
        }

        private void onAppMessage(
            final int msgTypeId,
            final DirectBuffer buffer,
            final int index,
            final int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onAppBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onAppData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onAppEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAppAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
                break;
            default:
                // ignore
                break;
            }
        }

        private void onAppBegin(
            final BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            state = PgsqlKafkaState.openingInitial(state);

            doAppWindow(traceId, authorization);

            doAppBegin(traceId, authorization);
        }

        private void onAppData(
            final DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int reserved = data.reserved();
            final int flags = data.flags();

            final OctetsFW payload = data.payload();
            final DirectBuffer buffer = payload.buffer();
            int offset = payload.offset();
            int limit = payload.limit();

            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + initialMax)
            {
                cleanup(traceId, authorization);
            }
            else
            {
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);

                final PgsqlDataExFW pgsqlDataEx = dataEx != null && dataEx.typeId() == pgsqlTypeId ?
                        extension.get(pgsqlDataExRO::tryWrap) : null;

                if (pgsqlDataEx != null &&
                    pgsqlDataEx.kind() == PgsqlDataExFW.KIND_QUERY)
                {
                    final int queryLength = payload.sizeof() + pgsqlDataEx.query().deferred();
                    queries.add(queryLength);

                    if (parserSlot == NO_SLOT)
                    {
                        parserSlot = bufferPool.acquire(initialId);
                    }
                }

                final MutableDirectBuffer slotBuffer = bufferPool.buffer(parserSlot);
                slotBuffer.putBytes(parserSlotOffset, buffer, offset, limit - offset);
                parserSlotOffset += limit - offset;

                if ((flags & FLAGS_FIN) != 0x00)
                {
                    doParseQuery(traceId, authorization);
                }
            }
        }

        private void onAppEnd(
            final EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = PgsqlKafkaState.closeInitial(state);

            doAppEnd(traceId, authorization);

            cleanup(traceId, authorization);
        }

        private void onAppAbort(
            final AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = PgsqlKafkaState.closeInitial(state);

        }

        private void onAppReset(
            final ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = PgsqlKafkaState.closeReply(state);

            cleanup(traceId, authorization);
        }

        private void onAppWindow(
            final WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert acknowledge >= replyAck;
            assert maximum + acknowledge >= replyMax + replyAck;

            replyBudgetId = budgetId;
            replyAck = acknowledge;
            replyMax = maximum;
            replyPadding = padding;

            assert replyAck <= replySeq;

        }

        private void onCommandCompleted(
            long traceId,
            long authorization,
            int progress,
            PgsqlKafkaCompletionCommand command)
        {
            commandsProcessed = 0;
            parserSlotOffset -= progress;

            doCommandCompletion(traceId, authorization, command);

            final MutableDirectBuffer parserBuffer = bufferPool.buffer(parserSlot);
            parserBuffer.putBytes(0, parserBuffer, progress, parserSlotOffset);

            final int queryLength = queries.peekInt();
            queryProgressOffset += progress;
            if (queryLength == queryProgressOffset)
            {
                queryProgressOffset = 0;
                queries.removeInt();
                doQueryReady(traceId, authorization);
            }

            if (parserSlotOffset == 0)
            {
                cleanupParserSlotIfNecessary();
            }
            else
            {
                doParseQuery(traceId, authorization);
            }

            doAppWindow(traceId, authorization);
        }

        private void doAppBegin(
            long traceId,
            long authorization)
        {
            doBegin(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, 0L, EMPTY_OCTETS);

            state = PgsqlKafkaState.openingReply(state);
        }

        private void doAppData(
            long traceId,
            long authorization,
            int flags,
            DirectBuffer buffer,
            int offset,
            int limit,
            OctetsFW extension)
        {
            final int length = limit - offset;
            final int reserved = length + initialPad;

            doData(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                flags, replyBudgetId, reserved, buffer, offset, length, extension);

            replySeq += reserved;
            assert replySeq <= replyAck + replyMax;
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (PgsqlKafkaState.replyOpened(state))
            {
                state = PgsqlKafkaState.closeInitial(state);

                doEnd(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }

        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (PgsqlKafkaState.replyOpened(state))
            {
                state = PgsqlKafkaState.closeReply(state);

                doAbort(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (PgsqlKafkaState.initialOpened(state))
            {
                state = PgsqlKafkaState.closeInitial(state);

                doReset(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doAppAbortAndReset(
            long traceId,
            long authorization)
        {
            doAppAbort(traceId, authorization);
            doAppReset(traceId, authorization);
        }

        private void doAppWindow(
            long traceId,
            long authorization)
        {
            final long newInitialAck = Math.max(initialSeq - parserSlotOffset, initialAck);

            if (newInitialAck > initialAck || !PgsqlKafkaState.initialOpened(state))
            {
                initialAck = newInitialAck;
                assert initialAck <= initialSeq;

                state = PgsqlKafkaState.openInitial(state);

                doWindow(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, initialBudgetId, initialPad);
            }
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            int reserved = (int) replySeq;

            doFlush(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId,
                    authorization, replyBudgetId, reserved, extension);
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            Consumer<OctetsFW.Builder> extension)
        {
            int reserved = (int) replySeq;

            doFlush(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId,
                    authorization, replyBudgetId, reserved, extension);
        }

        private void doCommandCompletion(
            long traceId,
            long authorization,
            PgsqlKafkaCompletionCommand command)
        {
            if (command != PgsqlKafkaCompletionCommand.)
            {
                extBuffer.putBytes(0, command.value());
                extBuffer.putInt(command.value().length, END_OF_FIELD);

                Consumer<OctetsFW.Builder> completionEx = e -> e.set((b, o, l) -> flushExRW.wrap(b, o, l)
                    .typeId(pgsqlTypeId)

                    .completion(c -> c.tag(extBuffer, 0,  command.value().length + 1))
                    .build().sizeof());

                doAppFlush(traceId, authorization, completionEx);
            }
        }

        private void doQueryReady(
            long traceId,
            long authorization)
        {
            Consumer<OctetsFW.Builder> readyEx = e -> e.set((b, o, l) -> flushExRW.wrap(b, o, l)
                    .typeId(pgsqlTypeId)
                    .ready(r -> r.status(s -> s.set(PgsqlStatus.IDLE)))
                    .build().sizeof());

            doAppFlush(traceId, authorization, readyEx);
        }

        private void doParseQuery(
            long traceId,
            long authorizationId)
        {
            if (parserSlot != NO_SLOT)
            {
                final MutableDirectBuffer parserBuffer = bufferPool.buffer(parserSlot);

                int statementOffset = 0;
                int progress = 0;

                parse:
                while (progress <= parserSlotOffset)
                {
                    if (parserBuffer.getByte(progress) == STATEMENT_SEMICOLON)
                    {
                        int length = progress - statementOffset + Byte.BYTES;
                        if (parserBuffer.getByte(progress + Byte.BYTES) == END_OF_FIELD)
                        {
                            length += Byte.BYTES;
                        }
                        final PgsqlKafkaCommandType command = decodeCommandType(parserBuffer, statementOffset, length);
                        final PgsqlDecoder transform = pgsqlDecoder.get(command);
                        transform.decode(this, traceId, authorizationId, parserBuffer, statementOffset, length);
                        break parse;
                    }

                    progress++;
                }
            }
        }

        private void cleanup(
            long traceId,
            long authorization)
        {
            doAppAbortAndReset(traceId, authorization);

            cleanupParserSlotIfNecessary();
        }

        private void cleanupParserSlotIfNecessary()
        {
            if (parserSlot != NO_SLOT)
            {
                bufferPool.release(parserSlot);
                parserSlot = NO_SLOT;
                parserSlotOffset = 0;
            }
        }
    }

    private final class KafkaProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final PgsqlProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private KafkaProxy(
            long originId,
            long routedId,
            PgsqlProxy delegate)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = PgsqlKafkaState.openingInitial(state);

            kafka = newKafkaFetcher(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, result);
        }

        private void doKafkaEnd(
            long traceId,
            long authorization)
        {
            if (!PgsqlKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = PgsqlKafkaState.closeInitial(state);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!PgsqlKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = PgsqlKafkaState.closeInitial(state);

                doAbort(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }


        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onKafkaData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onKafkaSignal(signal);
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final OctetsFW extension = begin.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = PgsqlKafkaState.openingReply(state);

            assert replyAck <= replySeq;

            final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
            final KafkaBeginExFW kafkaBeginEx =
                beginEx != null && beginEx.typeId() == kafkaTypeId ? extension.get(kafkaBeginExRO::tryWrap) : null;
        }

        private void onKafkaData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            delegate.cleanup(traceId, authorization);
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = PgsqlKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaEnd(traceId, authorization);
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = PgsqlKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaAbort(traceId, authorization);
        }

        private void onKafkaWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long authorization = window.authorization();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;
            assert maximum >= delegate.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = PgsqlKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.onKafkaWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;
            state = PgsqlKafkaState.closeInitial(state);

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.onKafkaReset(traceId, authorization);
        }

        private void onKafkaSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            doKafkaEnd(traceId, authorization);
        }

        private void doKafkaReset(
            long traceId,
            long authorization)
        {
            if (!PgsqlKafkaState.replyClosed(state))
            {
                state = PgsqlKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doKafkaWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            replyAck = Math.max(delegate.replyAck - replyPad, 0);
            replyMax = delegate.replyMax;

            doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding + replyPad);
        }
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
        DirectBuffer buffer,
        int offset,
        int length,
        Consumer<OctetsFW.Builder> extension)
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
                .payload(buffer, offset, length)
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
        DirectBuffer buffer,
        int offset,
        int length,
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
                .payload(buffer, offset, length)
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
        Flyweight extension)
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
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
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
        Consumer<OctetsFW.Builder> extension)
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
        long authorization,
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
                .authorization(authorization)
                .budgetId(budgetId)
                .padding(padding)
                .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void onDecodeCreateTableCommand(
        PgsqlProxy server,
        long traceId,
        long authorization,
        DirectBuffer buffer,
        int offset,
        int length)
    {
        if (server.commandsProcessed == 2)
        {
            server.onCommandCompleted(traceId, authorization, length, PgsqlKafkaCompletionCommand.CREATE_TABLE_COMMAND);
        }
        else
        {
            final PgsqlKafkaBindingConfig binding = server.binding;
            final CreateTable statement = (CreateTable) parseStatement(buffer, offset, length);

            String newStatement = "";
            int progress = 0;

            if (server.commandsProcessed == 0)
            {
                newStatement = binding.createTopic.generate(statement);
            }
            else if (server.commandsProcessed == 1 && primaryKey != null)
            {
                newStatement = binding.createTable.generate(statement);
            }
            else if (server.commandsProcessed == 1)
            {
                newStatement = binding.createSource.generate(statement);
            }

            statementBuffer.putBytes(progress, newStatement.getBytes());
            progress += newStatement.length();

            final PgsqlKafkaRouteConfig route =
                server.binding.resolve(authorization, statementBuffer, 0, progress);

            final KafkaProxy client = server.streamsByRouteIds.get(route.id);
            client.doPgsqlQuery(traceId, authorization, statementBuffer, 0, progress);
            client.typeCommand = ignoreFlushCommand;
        }
    }

    private void onDecodeUnknownCommand(
        PgsqlProxy server,
        long traceId,
        long authorization,
        DirectBuffer buffer,
        int offset,
        int length)
    {
    }

    private PgsqlKafkaCommandType decodeCommandType(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        PgsqlKafkaCommandType matchingCommand = PgsqlKafkaCommandType.UNKNOWN_COMMAND;

        command:
        for (PgsqlKafkaCommandType command : PgsqlKafkaCommandType.values())
        {
            int progressOffset = offset;

            boolean match = true;
            for (byte b : command.value())
            {
                if (buffer.getByte(progressOffset) != b)
                {
                    match = false;
                    break;
                }
                progressOffset++;
            }

            if (match)
            {
                matchingCommand = command;
                break command;
            }
        }

        return matchingCommand;
    }

    private Statement parseStatement(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        Statement statement = null;
        try
        {
            inputStream.wrap(buffer, offset, length);
            statement = parserManager.parse(reader);
        }
        catch (Exception ignored)
        {
            //NOOP
        }

        return statement;
    }

    @FunctionalInterface
    private interface PgsqlDecoder
    {
        void decode(
            PgsqlProxy server,
            long traceId,
            long authorization,
            DirectBuffer writeBuffer,
            int offset,
            int length);
    }
}
