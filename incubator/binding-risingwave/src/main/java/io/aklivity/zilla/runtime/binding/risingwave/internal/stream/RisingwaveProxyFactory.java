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
package io.aklivity.zilla.runtime.binding.risingwave.internal.stream;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.util.Objects.requireNonNull;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;

import io.aklivity.zilla.runtime.binding.risingwave.internal.RisingwaveBinding;
import io.aklivity.zilla.runtime.binding.risingwave.internal.RisingwaveConfiguration;
import io.aklivity.zilla.runtime.binding.risingwave.internal.config.RisingwaveBindingConfig;
import io.aklivity.zilla.runtime.binding.risingwave.internal.config.RisingwaveRouteConfig;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.Array32FW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlBeginExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlColumnInfoFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlDataExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlFlushExFW;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.CreateTable;

public final class RisingwaveProxyFactory implements RisingwaveStreamFactory
{
    private static final Byte STATEMENT_SEMICOLON = ';';

    private static final byte[] CREATE_TOPIC_COMMAND = "CREATE TOPIC".getBytes();

    private static final int END_OF_FIELD = 0x00;

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_CONT = 0x00;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMP = 0x03;

    private enum CommandType
    {
        CREATE_TABLE_COMMAND("CREATE TABLE".getBytes()),
        UNKNOWN_COMMAND("UNKNOWN".getBytes());

        private final byte[] value;

        CommandType(byte[] value)
        {
            this.value = value;
        }

        public byte[] getValue()
        {
            return value;
        }
    }

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
    private final PgsqlFlushExFW pgsqlFlushExRO = new PgsqlFlushExFW();

    private final PgsqlBeginExFW.Builder beginExRW = new PgsqlBeginExFW.Builder();
    private final PgsqlDataExFW.Builder dataExRW = new PgsqlDataExFW.Builder();
    private final PgsqlFlushExFW.Builder flushExRW = new PgsqlFlushExFW.Builder();

    private final Array32FW.Builder<PgsqlColumnInfoFW.Builder, PgsqlColumnInfoFW> columnsRW =
        new Array32FW.Builder<>(new PgsqlColumnInfoFW.Builder(), new PgsqlColumnInfoFW());

    private final BufferPool bufferPool;
    private final RisingwaveConfiguration config;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer statementBuffer;
    private final MutableDirectBuffer clientBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;

    private final Long2ObjectHashMap<RisingwaveBindingConfig> bindings;
    private final int pgsqlTypeId;

    private final Object2ObjectHashMap<CommandType, PgsqlTransform> clientTransforms;
    {
        Object2ObjectHashMap<CommandType, PgsqlTransform> clientTransforms =
            new Object2ObjectHashMap<>();
        clientTransforms.put(CommandType.CREATE_TABLE_COMMAND, this::onDecodeCreateTableCommand);
        clientTransforms.put(CommandType.UNKNOWN_COMMAND, this::onDecodeUnknownCommand);
        this.clientTransforms = clientTransforms;
    }

    public RisingwaveProxyFactory(
        RisingwaveConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.writeBuffer = requireNonNull(context.writeBuffer());
        this.statementBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.clientBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.bufferPool = context.bufferPool();

        this.bindings = new Long2ObjectHashMap<>();

        this.pgsqlTypeId = context.supplyTypeId(RisingwaveBinding.NAME);
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        RisingwaveBindingConfig risingwaveBinding = new RisingwaveBindingConfig(config, binding);
        bindings.put(binding.id, risingwaveBinding);
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
        MessageConsumer network)
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

        RisingwaveBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            RisingwaveRouteConfig route = binding.resolve(authorization, clientBuffer, 0, 0);

            if (route != null)
            {
                newStream = new PgsqlServer(
                    network,
                    originId,
                    routedId,
                    initialId,
                    parameters)::onApplicationMessage;
            }
        }

        return newStream;
    }

    private final class PgsqlServer
    {
        private final MessageConsumer application;
        private final Long2ObjectHashMap<PgsqlClient> streamsByRouteIds;
        private final RisingwaveBindingConfig binding;
        private final Map<String, String> parameters;

        private final long initialId;
        private final long replyId;
        private final long originId;
        private final long routedId;

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
        private int parserSlotReserved;

        private int state;

        private int commandsProcessed = 0;

        private PgsqlServer(
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId,
            Map<String, String> parameters)
        {
            this.application = application;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.binding = bindings.get(routedId);
            this.parameters = parameters;

            this.streamsByRouteIds = new Long2ObjectHashMap<>();
            binding.routes.forEach(r -> streamsByRouteIds.put(r.id, new PgsqlClient(this, routedId, r.id)));
        }

        private void onApplicationMessage(
            final int msgTypeId,
            final DirectBuffer buffer,
            final int index,
            final int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onApplicationBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onApplicationData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onApplicationEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onApplicationAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onApplicationReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onApplicationWindow(window);
                break;
            default:
                // ignore
                break;
            }
        }

        private void onApplicationBegin(
            final BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            state = RisingwaveState.openingInitial(state);

            streamsByRouteIds.values().forEach(c -> c.doApplicationBegin(traceId, authorization, affinity));
        }

        private void onApplicationData(
            final DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
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
                    if (parserSlot == NO_SLOT)
                    {
                        parserSlot = bufferPool.acquire(initialId);
                    }
                }

                final MutableDirectBuffer slotBuffer = bufferPool.buffer(parserSlot);
                slotBuffer.putBytes(parserSlotOffset, buffer, offset, limit - offset);
                parserSlotOffset += limit - offset;
                parserSlotReserved += reserved;

                if ((flags & FLAGS_FIN) != 0x00)
                {
                    //parseStatement
                    cleanupParserSlotIfNecessary();
                }
            }
        }

        private void onApplicationEnd(
            final EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = RisingwaveState.closeInitial(state);

        }

        private void onApplicationAbort(
            final AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = RisingwaveState.closeInitial(state);

        }

        private void onApplicationReset(
            final ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = RisingwaveState.closeReply(state);
        }

        private void onApplicationWindow(
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

        private void onStatementCompleted(
            long traceId,
            long authorization,
            int progress)
        {
            commandsProcessed = 0;
            parserSlotOffset += progress;
            doParseQuery(traceId, authorization);
        }

        private void doApplicationBegin(
            long traceId,
            long authorization)
        {
            doBegin(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, 0L, EMPTY_OCTETS);

            state = RisingwaveState.openingReply(state);
        }

        private void doApplicationData(
            long traceId,
            long authorization,
            int flags,
            DirectBuffer buffer,
            int offset,
            int limit,
            Consumer<OctetsFW.Builder> extension)
        {
            final int length = limit - offset;
            final int reserved = length + initialPad;

            doData(application, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                flags, replyBudgetId, reserved, buffer, offset, length, extension);

            replySeq += reserved;
            assert replySeq <= replyAck + replyMax;
        }

        private void doApplicationEnd(
            long traceId,
            long authorization)
        {
            state = RisingwaveState.closeInitial(state);

            doEnd(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, EMPTY_OCTETS);
        }

        private void doApplicationAbort(
            long traceId,
            long authorization)
        {
            state = RisingwaveState.closeReply(state);

            doAbort(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, EMPTY_OCTETS);
        }

        private void doApplicationReset(
            long traceId,
            long authorization)
        {
            state = RisingwaveState.closeInitial(state);

            doReset(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, EMPTY_OCTETS);
        }

        private void doApplicationAbortAndReset(
            long traceId,
            long authorization)
        {
            doApplicationAbort(traceId, authorization);
            doApplicationReset(traceId, authorization);
        }

        private void doApplicationWindow(
            long traceId,
            long authorization,
            long budgetId,
            int pendingAck,
            int paddingMin)
        {
            long initialAckMax = Math.max(initialSeq - pendingAck, initialAck);
            if (initialAckMax > initialAck || initialMax > initialMax)
            {
                initialAck = initialAckMax;
                initialMax = initialMax;
                assert initialAck <= initialSeq;

                int initialPad = paddingMin;

                state = RisingwaveState.openInitial(state);

                doWindow(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, initialPad);
            }
        }

        private void doApplicationFlush(
            long traceId,
            long authorization,
            int reserved,
            Consumer<OctetsFW.Builder> extension)
        {
            replySeq += reserved;

            doFlush(application, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId,
                    authorization, replyBudgetId, reserved, extension);
        }

        private void doParseQuery(
            long traceId,
            long authorizationId)
        {
            final MutableDirectBuffer parserBuffer = bufferPool.buffer(parserSlot);

            int statementOffset = 0;
            int progress = 0;

            parse:
            while (progress <= parserSlotOffset)
            {
                if (parserBuffer.getByte(progress) == STATEMENT_SEMICOLON)
                {
                    int length = progress - statementOffset;
                    final CommandType command = getCommand(parserBuffer, statementOffset, length);
                    final PgsqlTransform transform = clientTransforms.get(command);
                    transform.transform(this, traceId, authorizationId, parserBuffer, statementOffset, length);
                    break parse;
                }

                progress++;
            }
        }

        private void cleanup(
            long traceId,
            long authorization)
        {
        }

        private void cleanupParserSlotIfNecessary()
        {
            if (parserSlot != NO_SLOT)
            {
                bufferPool.release(parserSlot);
                parserSlot = NO_SLOT;
                parserSlotOffset = 0;
                parserSlotReserved = 0;
            }
        }
    }

    private final class PgsqlClient
    {
        private final PgsqlServer server;

        private MessageConsumer application;

        private final long initialId;
        private final long replyId;
        private final long originId;
        private final long routedId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialBudgetId;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private PgsqlClient(
            PgsqlServer server,
            long originId,
            long routedId)
        {
            this.server = server;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void onApplicationMessage(
            final int msgTypeId,
            final DirectBuffer buffer,
            final int index,
            final int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onApplicationBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onApplicationData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onApplicationEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onApplicationAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onApplicationFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onApplicationReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onApplicationWindow(window);
                break;
            default:
                // ignore
                break;
            }
        }

        private void onApplicationBegin(
            final BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            state = RisingwaveState.openingReply(state);

            doApplicationWindow(traceId, authorization, server.replyBudgetId, 0, 0);
        }

        private void onApplicationData(
            final DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;
            assert budgetId == server.replyBudgetId;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                server.cleanup(traceId, authorization);
            }
            else
            {
                final OctetsFW extension = data.extension();
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);

                final PgsqlDataExFW pgsqlDataEx = dataEx != null && dataEx.typeId() == pgsqlTypeId ?
                        extension.get(pgsqlDataExRO::tryWrap) : null;

                if (pgsqlDataEx.kind() == PgsqlDataExFW.KIND_ROW)
                {
                    final OctetsFW payload = data.payload();

                    if (payload != null)
                    {
                    }
                }
            }
        }

        private void onApplicationFlush(
            final FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;

            assert replyAck <= replySeq;

            if (replySeq > replySeq + replyMax)
            {
                server.cleanup(traceId, authorization);
            }

            final OctetsFW extension = flush.extension();
            final ExtensionFW flushEx = extension.get(extensionRO::tryWrap);

            final PgsqlFlushExFW pgsqlFlushEx = flushEx != null && flushEx.typeId() == pgsqlTypeId ?
                    extension.get(pgsqlFlushExRO::tryWrap) : null;

            assert pgsqlFlushEx != null;

            switch (pgsqlFlushEx.kind())
            {
            case PgsqlFlushExFW.KIND_TYPE:
                break;
            case PgsqlFlushExFW.KIND_COMPLETION:
                break;
            case PgsqlFlushExFW.KIND_READY:
                break;
            default:
                assert false;
                break;
            }
        }

        private void onApplicationEnd(
            final EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            if (!RisingwaveState.closed(server.state))
            {
                state = RisingwaveState.closeReply(state);

            }
        }

        private void onApplicationAbort(
            final AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = RisingwaveState.closeReply(state);

        }

        private void onApplicationReset(
            final ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = RisingwaveState.closeInitial(state);

        }

        private void onApplicationWindow(
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
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            initialBudgetId = budgetId;
            initialAck = acknowledge;
            initialMax = maximum;
            initialPad = padding;

            assert initialAck <= initialSeq;

            server.doParseQuery(traceId, authorization);
        }

        private void doApplicationBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            if (!RisingwaveState.initialOpening(state))
            {
                Consumer<OctetsFW.Builder> beginEx = e -> e.set((b, o, l) -> beginExRW.wrap(b, o, l)
                    .typeId(pgsqlTypeId)
                    .parameters(p -> server.parameters.forEach((k, v) -> p.item(i -> i.name(k).value(v))))
                    .build().sizeof());

                state = RisingwaveState.openingInitial(state);

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
                        .extension(beginEx)
                        .build();

                application = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(),
                        this::onApplicationMessage);

                application.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
            }
        }

        private void doApplicationData(
            long traceId,
            long authorization,
            int flags,
            DirectBuffer buffer,
            int offset,
            int limit,
            Consumer<OctetsFW.Builder> extension)
        {
            final int length = limit - offset;
            final int reserved = length + initialPad;

            doData(application, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                flags, initialBudgetId, reserved, buffer, offset, length, extension);

            initialSeq += reserved;
            assert initialSeq <= initialAck + initialMax;
        }

        private void doApplicationEnd(
            long traceId,
            long authorization)
        {
            doEnd(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, EMPTY_OCTETS);

            state = RisingwaveState.closeInitial(state);
        }

        private void doApplicationAbort(
            long traceId,
            long authorization)
        {
            state = RisingwaveState.closeInitial(state);

            doAbort(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, EMPTY_OCTETS);
        }

        private void doApplicationReset(
            long traceId,
            long authorization)
        {
            state = RisingwaveState.closeReply(state);

            doReset(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, EMPTY_OCTETS);
        }

        private void doApplicationAbortAndReset(
            long traceId,
            long authorization)
        {
            doApplicationAbort(traceId, authorization);
            doApplicationReset(traceId, authorization);
        }

        private void doApplicationWindow(
            long traceId,
            long authorization,
            long budgetId,
            int pendingAck,
            int paddingMin)
        {
            final long replyAckMax = Math.max(replySeq - pendingAck, replyAck);
            if (RisingwaveState.replyOpening(state) &&
                (replyAckMax > replyAck || server.replyMax > replyMax))
            {
                replyAck = replyAckMax;
                replyMax = server.replyMax;
                assert replyAck <= replySeq;

                state = RisingwaveState.openReply(state);

                doWindow(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, paddingMin);
            }
        }

        private void doPgsqlQuery(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            Consumer<OctetsFW.Builder> queryEx = e -> e.set((b, o, l) -> dataExRW.wrap(b, o, l)
                .typeId(pgsqlTypeId)
                .query(q -> q.deferred(0))
                .build().sizeof());

            doApplicationData(traceId, authorization, FLAGS_COMP, buffer, offset, limit, queryEx);
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
        PgsqlServer server,
        long traceId,
        long authorization,
        DirectBuffer buffer,
        int offset,
        int length)
    {
        final RisingwaveBindingConfig binding = server.binding;
        final CreateTable statement = (CreateTable) parseStatement(buffer, offset, length);
        final String primaryKey = binding.createTable.getPrimaryKey(statement);

        int progress = 0;

        if (server.commandsProcessed == 0)
        {
            final String newStatement = binding.createTopic.generate(statement);
            statementBuffer.putBytes(progress, newStatement.getBytes());
            progress += newStatement.length();
        }
        else if (server.commandsProcessed == 1 && primaryKey != null)
        {
            final String newStatement = binding.createTable.generate(statement);
            statementBuffer.putBytes(progress, newStatement.getBytes());
            progress += newStatement.length();
        }

        if (server.commandsProcessed < 1)
        {
            final RisingwaveRouteConfig route =
                server.binding.resolve(authorization, statementBuffer, 0, progress);

            final PgsqlClient client = server.streamsByRouteIds.get(route.id);
            client.doPgsqlQuery(traceId, authorization, statementBuffer, 0, progress);
        }
        else
        {
            server.onStatementCompleted(traceId, authorization, length);
        }
    }

    private void onDecodeUnknownCommand(
        PgsqlServer server,
        long traceId,
        long authorization,
        DirectBuffer buffer,
        int offset,
        int length)
    {
        final RisingwaveRouteConfig route =
            server.binding.resolve(authorization, buffer, offset, length);

        final PgsqlClient client = server.streamsByRouteIds.get(route.id);
        client.doPgsqlQuery(traceId, authorization, buffer, offset, length);
    }

    private CommandType getCommand(
        DirectBuffer statement,
        int offset,
        int length)
    {
        CommandType matchingCommand = CommandType.UNKNOWN_COMMAND;

        command:
        for (CommandType command : CommandType.values())
        {
            int progressOffset = offset;

            boolean match = true;
            for (byte b : command.getValue())
            {
                if (statement.getByte(progressOffset) != b)
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
            reader.reset();
            parserManager.parse(reader);
        }
        catch (Exception ignored)
        {
        }

        return statement;
    }

    @FunctionalInterface
    private interface PgsqlTransform
    {
        void transform(
            PgsqlServer server,
            long traceId,
            long authorization,
            DirectBuffer writeBuffer,
            int offset,
            int length);
    }
}
