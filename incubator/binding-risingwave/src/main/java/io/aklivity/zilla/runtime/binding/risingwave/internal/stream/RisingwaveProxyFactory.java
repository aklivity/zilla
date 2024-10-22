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

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.IntArrayQueue;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongArrayQueue;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.pgsql.parser.PgsqlParser;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.FunctionInfo;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.StreamInfo;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableInfo;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.ViewInfo;
import io.aklivity.zilla.runtime.binding.risingwave.internal.RisingwaveConfiguration;
import io.aklivity.zilla.runtime.binding.risingwave.internal.config.RisingwaveBindingConfig;
import io.aklivity.zilla.runtime.binding.risingwave.internal.config.RisingwaveCommandType;
import io.aklivity.zilla.runtime.binding.risingwave.internal.config.RisingwaveRouteConfig;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.String32FW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlBeginExFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlDataExFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlStatus;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class RisingwaveProxyFactory implements RisingwaveStreamFactory
{
    private static final int END_OF_FIELD = 0x00;

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_CONT = 0x00;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMP = 0x03;

    private static final int COMMAND_PROCESSED_ERRORED = -1;
    private static final int COMMAND_PROCESSED_NONE = 0;

    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer(new byte[0]);
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private final PgsqlParser parser = new PgsqlParser();
    private final List<String> statements = new ArrayList<>();
    private final StringBuilder currentStatement = new StringBuilder();

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

    private final String32FW columnRO = new String32FW(ByteOrder.BIG_ENDIAN);

    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final PgsqlBeginExFW pgsqlBeginExRO = new PgsqlBeginExFW();
    private final PgsqlDataExFW pgsqlDataExRO = new PgsqlDataExFW();
    private final PgsqlFlushExFW pgsqlFlushExRO = new PgsqlFlushExFW();

    private final PgsqlBeginExFW.Builder beginExRW = new PgsqlBeginExFW.Builder();
    private final PgsqlDataExFW.Builder dataExRW = new PgsqlDataExFW.Builder();
    private final PgsqlFlushExFW.Builder flushExRW = new PgsqlFlushExFW.Builder();

    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();

    private final BufferPool bufferPool;
    private final RisingwaveConfiguration config;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer statementBuffer;
    private final MutableDirectBuffer extBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongFunction<CatalogHandler> supplyCatalog;
    private final BindingHandler streamFactory;

    private final int decodeMax;

    private final Long2ObjectHashMap<RisingwaveBindingConfig> bindings;
    private final int pgsqlTypeId;

    private final PgsqlFlushCommand typeFlushCommand = this::typeFlushCommand;
    private final PgsqlFlushCommand proxyFlushCommand = this::proxyFlushCommand;
    private final PgsqlFlushCommand ignoreFlushCommand = this::ignoreFlushCommand;

    private final PgsqlDataCommand proxyDataCommand = this::proxyDataCommand;
    private final PgsqlDataCommand rowDataCommand = this::rowDataCommand;

    private final Object2ObjectHashMap<RisingwaveCommandType, PgsqlTransform> clientTransforms;
    {
        Object2ObjectHashMap<RisingwaveCommandType, PgsqlTransform> clientTransforms =
            new Object2ObjectHashMap<>();
        clientTransforms.put(RisingwaveCommandType.CREATE_TABLE_COMMAND, this::decodeCreateTableCommand);
        clientTransforms.put(RisingwaveCommandType.CREATE_STREAM_COMMAND, this::decodeCreateStreamCommand);
        clientTransforms.put(RisingwaveCommandType.CREATE_MATERIALIZED_VIEW_COMMAND, this::decodeCreateMaterializedViewCommand);
        clientTransforms.put(RisingwaveCommandType.CREATE_FUNCTION_COMMAND, this::decodeCreateFunctionCommand);
        clientTransforms.put(RisingwaveCommandType.UNKNOWN_COMMAND, this::decodeUnknownCommand);
        this.clientTransforms = clientTransforms;
    }

    public RisingwaveProxyFactory(
        RisingwaveConfiguration config,
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
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        RisingwaveBindingConfig risingwaveBinding = new RisingwaveBindingConfig(config, binding, supplyCatalog);
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

        RisingwaveBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            RisingwaveRouteConfig route = binding.resolve(authorization, extBuffer, 0, 0);

            if (route != null)
            {
                newStream = new PgsqlServer(
                    app,
                    originId,
                    routedId,
                    initialId,
                    parameters)::onAppMessage;
            }
        }

        return newStream;
    }

    private final class PgsqlServer
    {
        private final MessageConsumer app;
        private final Long2ObjectHashMap<PgsqlClient> streamsByRouteIds;
        private final RisingwaveBindingConfig binding;
        private final Map<String, String> parameters;
        private final LongArrayQueue responses;
        private final IntArrayQueue queries;
        private final List<String> columnTypes;
        private final List<String> columnDescriptions;
        private final Map<String, String> columns;
        private final String database;

        private final long initialId;
        private final long replyId;
        private final long originId;
        private final long routedId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialBudgetId;
        private long affinity;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPadding;
        private long replyBudgetId;

        private int parserSlot = NO_SLOT;
        private int parserSlotOffset;

        private int state;

        private int commandsProcessed = COMMAND_PROCESSED_NONE;
        private int queryProgressOffset;

        private PgsqlServer(
            MessageConsumer app,
            long originId,
            long routedId,
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

            this.columns = new Object2ObjectHashMap<>();
            this.columnTypes = new ArrayList<>();
            this.columnDescriptions = new ArrayList<>();
            this.streamsByRouteIds = new Long2ObjectHashMap<>();
            this.responses = new LongArrayQueue();
            this.queries = new IntArrayQueue();

            binding.routes.forEach(r -> streamsByRouteIds.put(r.id, new PgsqlClient(this, routedId, r.id)));
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
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onAppFlush(flush);
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

            this.affinity = begin.affinity();

            state = RisingwaveState.openingInitial(state);

            streamsByRouteIds.values().forEach(c -> c.doAppBegin(traceId, authorization, affinity));

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

        private void onAppFlush(
            final FlushFW flush)
        {
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();

            doAppWindow(traceId, authorization);
        }

        private void onAppEnd(
            final EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = RisingwaveState.closeInitial(state);

            doAppEnd(traceId, authorization);

            cleanup(traceId, authorization);
        }

        private void onAppAbort(
            final AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = RisingwaveState.closeInitial(state);

        }

        private void onAppReset(
            final ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = RisingwaveState.closeReply(state);

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

            state = RisingwaveState.openReply(state);

            if (responses.isEmpty())
            {
                streamsByRouteIds.values().forEach(c -> c.doAppWindow(authorization, traceId));
            }

            int credit = (int)(acknowledge - initialAck) + (maximum - initialMax);

            while (credit > 0 && !responses.isEmpty())
            {
                final long routeId = responses.peekLong();
                PgsqlClient client = streamsByRouteIds.get(routeId);

                if (client != null)
                {
                    final long streamAckSnapshot = client.initialAck;

                    client.doAppWindow(authorization, traceId);

                    credit = Math.max(credit - (int)(client.initialAck - streamAckSnapshot), 0);

                    if (client.initialAck != client.initialSeq)
                    {
                        break;
                    }
                }

                responses.removeLong();
            }
        }

        private void onCommandCompleted(
            long traceId,
            long authorization,
            int progress,
            RisingwaveCompletionCommand command)
        {
            final MutableDirectBuffer parserBuffer = bufferPool.buffer(parserSlot);

            if (commandsProcessed != COMMAND_PROCESSED_ERRORED)
            {
                doCommandCompletion(traceId, authorization, command);
            }

            parserSlotOffset -= progress;
            commandsProcessed = COMMAND_PROCESSED_NONE;

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

        private void onQueryReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW pgsqlFlushEx)
        {
            PgsqlStatus pgsqlStatus = pgsqlFlushEx.ready().status().get();
            if (pgsqlStatus == PgsqlStatus.IDLE)
            {
                commandsProcessed = commandsProcessed != COMMAND_PROCESSED_ERRORED
                    ? commandsProcessed + 1
                    : COMMAND_PROCESSED_ERRORED;
                doParseQuery(traceId, authorization);
            }
            else
            {
                cleanup(traceId, authorization);
            }
        }

        private void doAppBegin(
            long traceId,
            long authorization)
        {
            doBegin(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, 0L, EMPTY_OCTETS);

            state = RisingwaveState.openingReply(state);
        }

        private void doAppData(
            long clientRouteId,
            long traceId,
            long authorization,
            int flags,
            DirectBuffer buffer,
            int offset,
            int limit,
            OctetsFW extension)
        {
            responses.add(clientRouteId);

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
            if (RisingwaveState.replyOpened(state))
            {
                state = RisingwaveState.closeReply(state);

                doEnd(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }

        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (RisingwaveState.replyOpened(state))
            {
                state = RisingwaveState.closeReply(state);

                doAbort(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (RisingwaveState.initialOpened(state))
            {
                state = RisingwaveState.closeInitial(state);

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

            if (newInitialAck > initialAck || !RisingwaveState.initialOpened(state))
            {
                initialAck = newInitialAck;
                assert initialAck <= initialSeq;

                state = RisingwaveState.openInitial(state);

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
            RisingwaveCompletionCommand command)
        {
            if (command != RisingwaveCompletionCommand.UNKNOWN_COMMAND)
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

                String sql = parserBuffer.getStringWithoutLengthAscii(0, parserSlotOffset);
                splitStatements(sql)
                    .stream()
                    .findFirst()
                    .ifPresent(s ->
                    {
                        String statement = s;
                        String command = parser.parseCommand(statement);
                        final PgsqlTransform transform = clientTransforms.get(RisingwaveCommandType.valueOf(command.getBytes()));
                        transform.transform(this, traceId, authorizationId, statement);
                    });
            }
        }

        private void cleanup(
            long traceId,
            long authorization)
        {
            streamsByRouteIds.values().forEach(c -> c.doAppAbortAndReset(traceId, authorization));

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

    private final class PgsqlClient
    {
        private final PgsqlServer server;

        private MessageConsumer app;

        private final long originId;
        private final long routedId;

        private long initialId;
        private long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialBudgetId;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;
        private int messageOffset;

        private PgsqlFlushCommand typeCommand;
        private PgsqlDataCommand dataCommand;
        private PgsqlFlushCommand completionCommand;

        private PgsqlClient(
            PgsqlServer server,
            long originId,
            long routedId)
        {
            this.server = server;
            this.originId = originId;
            this.routedId = routedId;

            this.dataCommand = proxyDataCommand;
            this.typeCommand = proxyFlushCommand;
            this.completionCommand = ignoreFlushCommand;
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
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onAppFlush(flush);
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

            state = RisingwaveState.openingReply(state);

            doAppWindow(traceId, authorization);
        }

        private void onAppData(
            final DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final int flags = data.flags();

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
                final OctetsFW payload = data.payload();

                dataCommand.handle(server, routedId, traceId, authorization, flags,
                    payload.buffer(), payload.offset(), payload.limit(), extension);
            }
        }

        private void onAppFlush(
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
                onAppTypeFlush(traceId, authorization, pgsqlFlushEx);
                break;
            case PgsqlFlushExFW.KIND_COMPLETION:
                onAppCompletionFlush(traceId, authorization, pgsqlFlushEx);
                break;
            case PgsqlFlushExFW.KIND_ERROR:
                onAppErrorFlush(traceId, authorization, pgsqlFlushEx);
                break;
            case PgsqlFlushExFW.KIND_READY:
                onAppReadyFlush(traceId, authorization, pgsqlFlushEx);
                break;
            default:
                assert false;
                break;
            }
        }

        private void onAppEnd(
            final EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = RisingwaveState.closeReply(state);

            doAppEnd(traceId, authorization);
        }

        private void onAppAbort(
            final AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = RisingwaveState.closeReply(state);

            server.cleanup(traceId, authorization);
        }

        private void onAppReset(
            final ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = RisingwaveState.closeInitial(state);

            server.cleanup(traceId, authorization);
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
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            initialBudgetId = budgetId;
            initialAck = acknowledge;
            initialMax = maximum;
            initialPad = padding;

            assert initialAck <= initialSeq;

            state = RisingwaveState.openInitial(state);

            server.doParseQuery(traceId, authorization);
        }

        private void onAppTypeFlush(
            long traceId,
            long authorization,
            PgsqlFlushExFW pgsqlFlushEx)
        {
            typeCommand.handle(server, traceId, authorization, pgsqlFlushEx);
        }

        private void onAppCompletionFlush(
            long traceId,
            long authorization,
            PgsqlFlushExFW pgsqlFlushEx)
        {
            messageOffset = 0;
            completionCommand.handle(server, traceId, authorization, pgsqlFlushEx);
        }

        private void onAppErrorFlush(
            long traceId,
            long authorization,
            PgsqlFlushExFW pgsqlFlushEx)
        {
            messageOffset = 0;
            server.doAppFlush(traceId, authorization, pgsqlFlushEx);
            server.commandsProcessed = COMMAND_PROCESSED_ERRORED;
        }

        private void onAppReadyFlush(
            long traceId,
            long authorization,
            PgsqlFlushExFW pgsqlFlushEx)
        {
            dataCommand = proxyDataCommand;
            server.onQueryReady(traceId, authorization, pgsqlFlushEx);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            if (RisingwaveState.closed(state))
            {
                state = 0;
            }

            if (!RisingwaveState.initialOpening(state))
            {
                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);

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

                app = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(),
                        this::onAppMessage);

                app.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
            }
        }

        private void doAppData(
            long traceId,
            long authorization,
            int flags,
            DirectBuffer buffer,
            int offset,
            int length,
            Consumer<OctetsFW.Builder> extension)
        {
            final int reserved = length + initialPad;

            doData(app, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                flags, initialBudgetId, reserved, buffer, offset, length, extension);

            initialSeq += reserved;
            assert initialSeq <= initialAck + initialMax;
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (RisingwaveState.initialOpening(state))
            {
                state = RisingwaveState.closeInitial(state);

                doEnd(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (RisingwaveState.initialOpened(state))
            {
                state = RisingwaveState.closeInitial(state);

                doAbort(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (RisingwaveState.replyOpened(state))
            {
                state = RisingwaveState.closeReply(state);

                doReset(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
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
            if (RisingwaveState.replyOpening(state))
            {
                state = RisingwaveState.openReply(state);

                replyMax = server.replyMax;

                doWindow(app, originId, routedId, replyId, replySeq, replyAck, server.replyMax,
                    traceId, authorization, server.replyBudgetId, server.replyPadding);
            }
        }

        private void doPgsqlQuery(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            final int remaining = length - messageOffset;
            final int initialWin = Math.max(initialMax - (int)(initialSeq - initialAck), 0);
            final int lengthMax = Math.min(initialWin - initialPad, remaining);

            if (RisingwaveState.initialOpened(state) &&
                !RisingwaveState.initialClosing(state) &&
                lengthMax > 0 && remaining > 0)
            {
                final int deferred = remaining - length;

                int flags = 0x00;
                if (messageOffset == 0)
                {
                    flags |= FLAGS_INIT;
                }
                if (length == remaining)
                {
                    flags |= FLAGS_FIN;
                }

                Consumer<OctetsFW.Builder> queryEx = (flags & FLAGS_INIT) != 0x00
                    ? e -> e.set((b, o, l) -> dataExRW.wrap(b, o, l)
                        .typeId(pgsqlTypeId)
                        .query(q -> q.deferred(deferred))
                        .build().sizeof())
                    : EMPTY_EXTENSION;

                doAppData(traceId, authorization, flags, buffer, offset, lengthMax, queryEx);

                messageOffset += lengthMax;
            }
            else
            {
                doAppBegin(traceId, authorization, server.affinity);
            }
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

    private void decodeCreateTableCommand(
        PgsqlServer server,
        long traceId,
        long authorization,
        String statement)
    {
        if (server.commandsProcessed == 6 ||
            server.commandsProcessed == COMMAND_PROCESSED_ERRORED)
        {
            final int length = statement.length();
            server.onCommandCompleted(traceId, authorization, length, RisingwaveCompletionCommand.CREATE_TABLE_COMMAND);
        }
        else
        {
            final RisingwaveBindingConfig binding = server.binding;
            final TableInfo tableInfo = parser.parseCreateTable(statement);

            String newStatement = "";
            int progress = 0;

            if (server.commandsProcessed == 0)
            {
                newStatement = binding.createTopic.generate(tableInfo);
            }
            else if (server.commandsProcessed == 1)
            {
                newStatement = binding.createSource.generateTableSource(server.database, tableInfo);
            }
            else if (server.commandsProcessed == 2)
            {
                newStatement = binding.createView.generate(tableInfo);
            }
            else if (server.commandsProcessed == 3)
            {
                newStatement = binding.createTable.generate(tableInfo);
            }
            else if (server.commandsProcessed == 4)
            {
                newStatement = binding.createSink.generate(tableInfo);
            }
            else if (server.commandsProcessed == 5)
            {
                newStatement = binding.createSink.generate(server.database, tableInfo);
            }

            statementBuffer.putBytes(progress, newStatement.getBytes());
            progress += newStatement.length();

            final RisingwaveRouteConfig route =
                server.binding.resolve(authorization, statementBuffer, 0, progress);

            final PgsqlClient client = server.streamsByRouteIds.get(route.id);
            client.doPgsqlQuery(traceId, authorization, statementBuffer, 0, progress);
            client.typeCommand = ignoreFlushCommand;
        }
    }

    private void decodeCreateStreamCommand(
        PgsqlServer server,
        long traceId,
        long authorization,
        String statement)
    {
        if (server.commandsProcessed == 2 ||
            server.commandsProcessed == COMMAND_PROCESSED_ERRORED)
        {
            final int length = statement.length();
            server.onCommandCompleted(traceId, authorization, length, RisingwaveCompletionCommand.CREATE_STREAM_COMMAND);
        }
        else
        {
            final RisingwaveBindingConfig binding = server.binding;
            final StreamInfo streamInfo = parser.parseCreateStream(statement);

            String newStatement = "";
            int progress = 0;

            if (server.commandsProcessed == 0)
            {
                newStatement = binding.createTopic.generate(streamInfo);
            }
            else if (server.commandsProcessed == 1)
            {
                newStatement = binding.createSource.generateStreamSource(server.database, streamInfo);
            }

            statementBuffer.putBytes(progress, newStatement.getBytes());
            progress += newStatement.length();

            final RisingwaveRouteConfig route =
                server.binding.resolve(authorization, statementBuffer, 0, progress);

            final PgsqlClient client = server.streamsByRouteIds.get(route.id);
            client.doPgsqlQuery(traceId, authorization, statementBuffer, 0, progress);
            client.typeCommand = ignoreFlushCommand;
        }
    }

    private void decodeCreateMaterializedViewCommand(
        PgsqlServer server,
        long traceId,
        long authorization,
        String statement)
    {
        if (server.commandsProcessed == 4 ||
            server.commandsProcessed == COMMAND_PROCESSED_ERRORED)
        {
            final int length = statement.length();
            server.onCommandCompleted(traceId, authorization, length,
                RisingwaveCompletionCommand.CREATE_MATERIALIZED_VIEW_COMMAND);
        }
        else
        {
            final RisingwaveBindingConfig binding = server.binding;
            final ViewInfo viewInfo = parser.parseCreateMaterializedView(statement);
            PgsqlFlushCommand typeCommand = ignoreFlushCommand;
            PgsqlDataCommand dataCommand = proxyDataCommand;

            String newStatement = "";
            int progress = 0;

            if (server.commandsProcessed == 0)
            {
                newStatement = binding.createView.generate(viewInfo);
            }
            else if (server.commandsProcessed == 1)
            {
                newStatement = binding.describeView.generate(viewInfo);
                typeCommand = typeFlushCommand;
                dataCommand = rowDataCommand;
                server.columns.clear();
            }
            else if (server.commandsProcessed == 2)
            {
                newStatement = binding.createTopic.generate(viewInfo, server.columns);
            }
            else if (server.commandsProcessed == 3)
            {
                newStatement = binding.createSink.generate(server.database, server.columns, viewInfo);
            }

            statementBuffer.putBytes(progress, newStatement.getBytes());
            progress += newStatement.length();

            final RisingwaveRouteConfig route =
                server.binding.resolve(authorization, statementBuffer, 0, progress);

            final PgsqlClient client = server.streamsByRouteIds.get(route.id);
            client.doPgsqlQuery(traceId, authorization, statementBuffer, 0, progress);
            client.typeCommand = typeCommand;
            client.dataCommand = dataCommand;
            client.completionCommand = ignoreFlushCommand;
        }
    }

    private void decodeCreateFunctionCommand(
        PgsqlServer server,
        long traceId,
        long authorization,
        String statement)
    {
        if (server.commandsProcessed == 1 ||
            server.commandsProcessed == COMMAND_PROCESSED_ERRORED)
        {
            final int length = statement.length();
            server.onCommandCompleted(traceId, authorization, length, RisingwaveCompletionCommand.CREATE_FUNCTION_COMMAND);
        }
        else
        {
            final RisingwaveBindingConfig binding = server.binding;
            final FunctionInfo functionInfo = parser.parseCreateFunction(statement);

            String newStatement = "";
            int progress = 0;

            if (server.commandsProcessed == 0)
            {
                newStatement = binding.createFunction.generate(functionInfo);
            }

            statementBuffer.putBytes(progress, newStatement.getBytes());
            progress += newStatement.length();

            final RisingwaveRouteConfig route =
                server.binding.resolve(authorization, statementBuffer, 0, progress);

            final PgsqlClient client = server.streamsByRouteIds.get(route.id);
            client.doPgsqlQuery(traceId, authorization, statementBuffer, 0, progress);
            client.typeCommand = ignoreFlushCommand;
        }
    }

    private void decodeUnknownCommand(
        PgsqlServer server,
        long traceId,
        long authorization,
        String statement)
    {
        final int length = statement.length();

        if (server.commandsProcessed == 1 ||
            server.commandsProcessed == COMMAND_PROCESSED_ERRORED)
        {
            server.onCommandCompleted(traceId, authorization, length, RisingwaveCompletionCommand.UNKNOWN_COMMAND);
        }
        else
        {
            statementBuffer.putBytes(0, statement.getBytes());

            final RisingwaveRouteConfig route =
                server.binding.resolve(authorization, statementBuffer, 0, length);

            final PgsqlClient client = server.streamsByRouteIds.get(route.id);
            client.doPgsqlQuery(traceId, authorization, statementBuffer, 0, length);
            client.completionCommand = proxyFlushCommand;
        }
    }

    private void ignoreFlushCommand(
        PgsqlServer server,
        long traceId,
        long authorization,
        PgsqlFlushExFW flushEx)
    {
        //NOOP
    }

    private void proxyFlushCommand(
        PgsqlServer server,
        long traceId,
        long authorization,
        PgsqlFlushExFW flushEx)
    {
        server.doAppFlush(traceId, authorization, flushEx);
    }

    private void typeFlushCommand(
        PgsqlServer server,
        long traceId,
        long authorization,
        PgsqlFlushExFW flushEx)
    {
        server.columnTypes.clear();
        flushEx.type().columns()
            .forEach(c ->
            {
                String name = c.name().asString();
                name = name.substring(0, name.length() - 1);
                server.columnTypes.add(name);
            });
    }

    private void rowDataCommand(
        PgsqlServer server,
        long traceId,
        long authorization,
        long routedId,
        int flags,
        DirectBuffer buffer,
        int offset,
        int limit,
        OctetsFW extension)
    {
        int progress = offset;

        final List<String> columnDescriptions = server.columnDescriptions;

        if ((flags & FLAGS_INIT) != 0x00)
        {
            columnDescriptions.clear();
            progress += Short.BYTES;
        }

        column:
        while (progress < limit)
        {
            String32FW column = columnRO.tryWrap(buffer, progress, limit);

            if (column == null)
            {
                break column;
            }

            columnDescriptions.add(column.asString());

            progress = column.limit();
        }

        int nameIndex = server.columnTypes.indexOf("Name");
        int typeIndex = server.columnTypes.indexOf("Type");
        int isHiddenIndex = server.columnTypes.indexOf("Is Hidden");

        if ("false".equals(columnDescriptions.get(isHiddenIndex)))
        {
            server.columns.put(columnDescriptions.get(nameIndex), columnDescriptions.get(typeIndex));
        }

    }

    private void proxyDataCommand(
        PgsqlServer server,
        long traceId,
        long authorization,
        long routedId,
        int flags,
        DirectBuffer buffer,
        int offset,
        int limit,
        OctetsFW extension)
    {
        server.doAppData(routedId, traceId, authorization, flags, buffer, offset, limit, extension);
    }

    public List<String> splitStatements(
        String sql)
    {
        statements.clear();
        currentStatement.setLength(0);

        boolean inDollarQuotes = false;
        int length = sql.length();
        int start = 0;

        for (int i = 0; i < length; i++)
        {
            char c = sql.charAt(i);

            if (c == '$' && i + 1 < length && sql.charAt(i + 1) == '$')
            {
                inDollarQuotes = !inDollarQuotes;
                i++;
            }
            else if (c == ';' && !inDollarQuotes)
            {
                int j = i + 1;
                while (j < length && Character.isWhitespace(sql.charAt(j)))
                {
                    j++;
                }

                if (j < length && sql.charAt(j) == '\0')
                {
                    i = j;
                }

                // Add statement substring directly to the list
                statements.add(sql.substring(start, i + 1));
                start = j; // Start of the next statement
                i = j - 1; // Move index to after whitespaces
            }
        }

        // Add the last statement if there's any remaining
        if (start < length)
        {
            statements.add(sql.substring(start, length));
        }

        return statements;
    }

    @FunctionalInterface
    private interface PgsqlTransform
    {
        void transform(
            PgsqlServer server,
            long traceId,
            long authorization,
            String statement);
    }

    @FunctionalInterface
    private interface PgsqlFlushCommand
    {
        void handle(
            PgsqlServer server,
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx);
    }

    @FunctionalInterface
    private interface PgsqlDataCommand
    {
        void handle(
            PgsqlServer server,
            long traceId,
            long authorization,
            long routedId,
            int flags,
            DirectBuffer buffer,
            int offset,
            int limit,
            OctetsFW extension);
    }
}
