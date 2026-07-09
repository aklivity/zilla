/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheToolsSearchConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpDescribeToolCallScanner;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpJsonStringEscaper;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpSearchToolCallScanner;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpToolByteRange;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpToolNames;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpProxyLifecycleFactory.McpLifecycleClient;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpProxyLifecycleFactory.McpLifecycleServer;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpProxyLifecycleFactory.McpRouteRequest;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache.McpProxyCache;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpResetExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchMatch;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableDirectByteBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.util.function.LongIntToLongFunction;

abstract class McpProxyItemFactory implements BindingHandler
{
    private static final String MCP_TYPE_NAME = "mcp";

    private static final int DATA_FLAG_FIN = 0x01;
    private static final int DATA_FLAG_INIT = 0x02;
    private static final int DATA_FLAG_COMPLETE = 0x03;
    private static final int ERROR_CODE_INVALID_PARAMS = -32602;
    private static final String ERROR_MESSAGE_INVALID_PARAMS = "Invalid params";

    // literal byte constants the search response is assembled from -- each match's raw name/description
    // token bytes are copied verbatim out of the cache in between, never re-serialized or re-escaped
    private static final byte[] SEARCH_RESPONSE_PREFIX =
        "{\"content\":[{\"type\":\"text\",\"text\":\"".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SEARCH_RESPONSE_MIDDLE =
        "\"}],\"structuredContent\":".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SEARCH_RESPONSE_SUFFIX =
        ",\"isError\":false}".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SEARCH_TOOLS_OPEN = "{\"tools\":[".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SEARCH_TOOLS_CLOSE = "]}".getBytes(StandardCharsets.US_ASCII);
    private static final byte SEARCH_TOOLS_SEPARATOR = ',';
    private static final byte[] DIGEST_NAME_OPEN = "{\"name\":\"".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DIGEST_DESCRIPTION_KEY = "\",\"description\":\"".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DIGEST_CLOSE = "\"}".getBytes(StandardCharsets.US_ASCII);

    // describe_tool's not-found/unauthorized response is a fixed constant -- existence and
    // authorization failures are indistinguishable to the caller by design, so there is nothing
    // request-specific to assemble; the same immutable array is reused as the response body for
    // every denied lookup, with no per-request allocation or copy
    private static final byte[] DESCRIBE_NOT_FOUND_RESPONSE =
        "{\"content\":[{\"type\":\"text\",\"text\":\"Tool not found\"}],\"isError\":true}"
            .getBytes(StandardCharsets.US_ASCII);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(), 0, 0);

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();
    private final McpResetExFW.Builder mcpResetExRW = new McpResetExFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx codecBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final BindingHandler streamFactory;
    private final LongIntToLongFunction supplyInitialIdHash;
    private final LongUnaryOperator supplyReplyId;
    private final int mcpTypeId;
    private final LongFunction<McpBindingConfig> supplyBinding;
    private final int kind;

    // reusable, growable per-worker scratch arrays shared by both search-family tools' response
    // assembly (search_tools' digest and describe_tool's whole-object copy), reconfigured per
    // request and never reallocated; the final response returned to the caller is always a
    // dedicated trimmed copy, since it is cached and drained over multiple WINDOW credits per stream
    private byte[] searchStructuredArray = new byte[0];
    private byte[] searchResponseArray = new byte[0];

    McpProxyItemFactory(
        McpConfiguration config,
        EngineContext context,
        LongFunction<McpBindingConfig> supplyBinding,
        int kind)
    {
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialIdHash = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.supplyBinding = supplyBinding;
        this.kind = kind;
    }

    @Override
    public final MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();

        MessageConsumer newStream = null;

        final McpBindingConfig binding = supplyBinding.apply(routedId);
        final McpBeginExFW beginEx = extension.get(mcpBeginExRO::tryWrap);

        if (binding != null && beginEx != null && beginEx.kind() == kind)
        {
            final String sessionId = sessionId(beginEx);
            if (binding.sessions.get(sessionId) instanceof McpLifecycleServer lifecycle)
            {
                final McpProxyCache.McpListCache searchCache = searchCacheFor(binding, beginEx);
                final McpProxyCache.McpListCache describeCache = searchCache == null
                    ? describeCacheFor(binding, beginEx)
                    : null;
                if (searchCache != null)
                {
                    newStream = new McpToolSearchServer(
                        sender,
                        originId,
                        routedId,
                        initialId,
                        affinity,
                        authorization,
                        binding.filterGuard,
                        searchCache,
                        binding.options.cache.tools.search.limit)::onToolSearchMessage;
                }
                else if (describeCache != null)
                {
                    newStream = new McpDescribeToolServer(
                        sender,
                        originId,
                        routedId,
                        initialId,
                        affinity,
                        authorization,
                        binding.filterGuard,
                        describeCache)::onDescribeToolMessage;
                }
                else
                {
                    final McpRouteConfig route = binding.resolve(beginEx, authorization);
                    if (route != null)
                    {
                        final String identifier = route.strip(beginEx);
                        final int contentLength = contentLength(beginEx);
                        final String prefix = route.prefix(beginEx);

                        newStream = new McpServer(
                            binding,
                            lifecycle,
                            sender,
                            originId,
                            routedId,
                            initialId,
                            route.id,
                            affinity,
                            authorization,
                            identifier,
                            contentLength,
                            prefix)::onServerMessage;
                    }
                }
            }
        }

        return newStream;
    }

    private McpProxyCache.McpListCache searchCacheFor(
        McpBindingConfig binding,
        McpBeginExFW beginEx)
    {
        McpProxyCache.McpListCache searchCache = null;

        final McpCacheToolsSearchConfig search = binding.cache != null && binding.options.cache.tools != null
            ? binding.options.cache.tools.search
            : null;

        if (kind == McpBeginExFW.KIND_TOOLS_CALL &&
            search != null &&
            McpToolNames.effectiveName(search.toolkit, McpToolNames.SEARCH_TOOLS).equals(beginEx.toolsCall().name().asString()))
        {
            final McpProxyCache.McpListCache listCache = binding.cache.cacheOf(McpBeginExFW.KIND_TOOLS_LIST);
            if (listCache != null && listCache.searchIndex() != null)
            {
                searchCache = listCache;
            }
        }

        return searchCache;
    }

    private McpProxyCache.McpListCache describeCacheFor(
        McpBindingConfig binding,
        McpBeginExFW beginEx)
    {
        McpProxyCache.McpListCache describeCache = null;

        final McpCacheToolsSearchConfig search = binding.cache != null && binding.options.cache.tools != null
            ? binding.options.cache.tools.search
            : null;

        if (kind == McpBeginExFW.KIND_TOOLS_CALL &&
            search != null &&
            McpToolNames.effectiveName(search.toolkit, McpToolNames.DESCRIBE_TOOL).equals(beginEx.toolsCall().name().asString()))
        {
            final McpProxyCache.McpListCache listCache = binding.cache.cacheOf(McpBeginExFW.KIND_TOOLS_LIST);
            if (listCache != null && listCache.searchIndex() != null)
            {
                describeCache = listCache;
            }
        }

        return describeCache;
    }

    // assembles the full envelope into searchResponseArray: the literal prefix, an escaped copy of
    // the structuredContent bytes (searchStructuredArray[0, structuredLength)) for the "text" field,
    // the literal middle, the raw structuredContent bytes, then the literal suffix -- shared by both
    // search_tools' digest response and describe_tool's whole-object response, since both use the
    // identical {"content":[...],"structuredContent":<raw>,"isError":false} envelope shape
    private int writeSearchFamilyResponse(
        int structuredLength)
    {
        final int capacity = SEARCH_RESPONSE_PREFIX.length +
            McpJsonStringEscaper.maxEscapedLength(structuredLength) +
            SEARCH_RESPONSE_MIDDLE.length +
            structuredLength +
            SEARCH_RESPONSE_SUFFIX.length;
        if (searchResponseArray.length < capacity)
        {
            searchResponseArray = new byte[capacity];
        }

        int produced = 0;
        System.arraycopy(SEARCH_RESPONSE_PREFIX, 0, searchResponseArray, produced, SEARCH_RESPONSE_PREFIX.length);
        produced += SEARCH_RESPONSE_PREFIX.length;

        produced += McpJsonStringEscaper.escape(searchStructuredArray, 0, structuredLength, searchResponseArray, produced);

        System.arraycopy(SEARCH_RESPONSE_MIDDLE, 0, searchResponseArray, produced, SEARCH_RESPONSE_MIDDLE.length);
        produced += SEARCH_RESPONSE_MIDDLE.length;

        System.arraycopy(searchStructuredArray, 0, searchResponseArray, produced, structuredLength);
        produced += structuredLength;

        System.arraycopy(SEARCH_RESPONSE_SUFFIX, 0, searchResponseArray, produced, SEARCH_RESPONSE_SUFFIX.length);
        produced += SEARCH_RESPONSE_SUFFIX.length;

        return produced;
    }

    protected abstract void injectInitialBeginEx(
        McpBeginExFW.Builder builder,
        String sessionId,
        String identifier,
        int contentLength);

    protected abstract void injectReplyBeginEx(
        McpBeginExFW.Builder builder,
        String sessionId,
        McpBeginExFW upstream);

    protected abstract String sessionId(
        McpBeginExFW beginEx);

    protected abstract int contentLength(
        McpBeginExFW beginEx);

    private final class McpServer
    {
        private final McpBindingConfig binding;
        private final McpLifecycleServer lifecycle;
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final String identifier;
        private final int contentLength;
        private final String prefix;
        private boolean prefixStripped;
        private ExpandableDirectByteBufferEx prefixCarryBuffer;
        private int prefixCarryLength;
        private boolean forwardedAny;
        private final McpClient client;

        private final int toolSchemaId;
        private ExpandableDirectByteBufferEx argsBuffer;
        private int argsProgress;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private McpServer(
            McpBindingConfig binding,
            McpLifecycleServer lifecycle,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String identifier,
            int contentLength,
            String prefix)
        {
            this.binding = binding;
            this.lifecycle = lifecycle;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.identifier = identifier;
            this.contentLength = contentLength;
            this.prefix = prefix;
            this.toolSchemaId = kind == McpBeginExFW.KIND_TOOLS_CALL && binding.validatesTools()
                ? binding.toolSchemaId(prefix + identifier)
                : NO_SCHEMA_ID;
            this.client = new McpClient(this, resolvedId);
        }

        private void onServerMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onServerBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onServerData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onServerEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onServerAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onServerFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onServerWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onServerReset(reset);
                break;
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onServerChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onServerChallenge(
            ChallengeFW challenge)
        {
            client.doClientChallenge(challenge.traceId(), challenge.authorization(), challenge.extension());
        }

        private void onServerBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();

            initialSeq = sequence;
            initialAck = acknowledge;

            state = McpState.openingInitial(state);

            client.doClientBegin(traceId);

            final int minInitialMax = toolSchemaId != NO_SCHEMA_ID ? codecBuffer.capacity() : 0;
            flushServerWindow(traceId, 0L, 0, 0L, minInitialMax);
        }

        private void onServerData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            final int prefixLen = prefix.length();
            if (prefixLen > 0 && !prefixStripped)
            {
                onServerDataWithPrefix(traceId, budgetId, flags, prefixLen, payload);
            }
            else
            {
                forwardServerData(traceId, budgetId, flags, reserved,
                    payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void onServerDataWithPrefix(
            long traceId,
            long budgetId,
            int flags,
            int prefixLen,
            OctetsFW payload)
        {
            final int strippedLength = resolvePrefixStrip(prefixLen, flags, payload);

            if (strippedLength > 0 || prefixStripped)
            {
                forwardServerData(traceId, budgetId, flags, strippedLength,
                    codecBuffer, 0, strippedLength);
            }
        }

        private void forwardServerData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final int forwardFlags = !forwardedAny && length > 0 ? flags | DATA_FLAG_INIT : flags;
            forwardedAny = forwardedAny || length > 0;

            if (toolSchemaId != NO_SCHEMA_ID)
            {
                bufferArgs(traceId, buffer, offset, length);
            }
            else
            {
                client.doClientData(traceId, budgetId, forwardFlags, reserved, buffer, offset, length);
            }
        }

        private int resolvePrefixStrip(
            int prefixLen,
            int flags,
            OctetsFW payload)
        {
            final DirectBufferEx buf = payload.buffer();
            final int offset = payload.offset();
            final int length = payload.sizeof();

            final int carryLen = prefixCarryLength;
            final int combinedLen = carryLen + length;

            if (carryLen > 0)
            {
                extBuffer.putBytes(0, prefixCarryBuffer, 0, carryLen);
            }
            extBuffer.putBytes(carryLen, buf, offset, length);

            final int prefixAt = indexOfQuotedPrefix(extBuffer, 0, combinedLen);
            final boolean lastFrame = (flags & DATA_FLAG_FIN) != 0x00;

            final int strippedLength;
            if (prefixAt >= 0)
            {
                final int tailFrom = prefixAt + prefixLen;
                final int tailLen = combinedLen - tailFrom;
                codecBuffer.putBytes(0, extBuffer, 0, prefixAt);
                codecBuffer.putBytes(prefixAt, extBuffer, tailFrom, tailLen);
                strippedLength = combinedLen - prefixLen;
                prefixStripped = true;
                prefixCarryLength = 0;
            }
            else if (lastFrame)
            {
                codecBuffer.putBytes(0, extBuffer, 0, combinedLen);
                strippedLength = combinedLen;
                prefixStripped = true;
                prefixCarryLength = 0;
            }
            else
            {
                final int retain = Math.min(combinedLen, prefixLen);
                strippedLength = combinedLen - retain;
                codecBuffer.putBytes(0, extBuffer, 0, strippedLength);
                if (prefixCarryBuffer == null)
                {
                    prefixCarryBuffer = new ExpandableDirectByteBufferEx();
                }
                prefixCarryBuffer.putBytes(0, extBuffer, strippedLength, retain);
                prefixCarryLength = retain;
            }

            return strippedLength;
        }

        private void bufferArgs(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (argsBuffer == null)
            {
                argsBuffer = new ExpandableDirectByteBufferEx();
            }
            argsBuffer.putBytes(argsProgress, buffer, offset, length);
            argsProgress += length;

            final int argsExpected = contentLength - prefix.length();
            if (argsProgress >= argsExpected)
            {
                validateArgs(traceId);
            }
        }

        private void validateArgs(
            long traceId)
        {
            final boolean valid =
                binding.validateToolCall(toolSchemaId, traceId, routedId, argsBuffer, 0, argsProgress);
            if (valid)
            {
                client.doClientData(traceId, 0L, DATA_FLAG_COMPLETE, argsProgress,
                    argsBuffer, 0, argsProgress);
            }
            else
            {
                final McpResetExFW resetEx = mcpResetExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .error(e -> e.code(ERROR_CODE_INVALID_PARAMS).message(ERROR_MESSAGE_INVALID_PARAMS))
                    .build();
                doServerReset(traceId, resetEx);
                client.doClientAbort(traceId);
            }
        }

        private int indexOfQuotedPrefix(
            DirectBufferEx buf,
            int offset,
            int limit)
        {
            final int prefixLen = prefix.length();
            int result = -1;

            for (int at = offset; result < 0 && at + prefixLen < limit; at++)
            {
                if (buf.getByte(at) == (byte) '"')
                {
                    boolean match = true;
                    for (int index = 0; match && index < prefixLen; index++)
                    {
                        match = buf.getByte(at + 1 + index) == (byte) prefix.charAt(index);
                    }
                    if (match)
                    {
                        result = at + 1;
                    }
                }
            }

            return result;
        }

        private void onServerEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            state = McpState.closedInitial(state);

            client.doClientEnd(traceId);
        }

        private void onServerAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            state = McpState.closedInitial(state);

            client.doClientAbort(traceId);
        }

        private void onServerFlush(
            FlushFW flush)
        {
            client.doClientFlush(flush.traceId(), flush.authorization(),
                flush.budgetId(), flush.reserved(), flush.extension());
        }

        private void onServerWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum + acknowledge >= replyMax + replyAck;

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;

            assert replyAck <= replySeq;

            client.flushClientWindow(traceId, budgetId, padding, replySeq - replyAck, replyMax);
        }

        private void onServerReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;

            replyAck = acknowledge;

            assert replyAck <= replySeq;

            state = McpState.closedReply(state);

            client.doClientReset(traceId);
        }

        private void doServerBegin(
            long traceId,
            Flyweight extension)
        {
            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                affinity, extension);
            state = McpState.openedReply(state);
        }

        private void doServerData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBufferEx payload,
            int offset,
            int length)
        {
            doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                flags, budgetId, reserved, payload, offset, length);
            replySeq += reserved;
        }

        private void doServerEnd(
            long traceId,
            OctetsFW extension)
        {
            if (!McpState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, extension);
                state = McpState.closedReply(state);
            }
        }

        private void doServerAbort(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, reserved, extension);
        }

        private void doServerChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            doChallenge(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, extension);
        }

        private void doServerWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            state = McpState.openedInitial(state);
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                budgetId, padding);
        }

        private void flushServerWindow(
            long traceId,
            long budgetId,
            int padding,
            long minInitialNoAck,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialAck, initialSeq - minInitialNoAck);
            final int newInitialMax = Math.max(initialMax, minInitialMax);

            if (newInitialAck > initialAck || newInitialMax > initialMax || !McpState.initialOpened(state))
            {
                initialAck = newInitialAck;
                initialMax = newInitialMax;
                doServerWindow(traceId, budgetId, padding);
            }
        }

        private void doServerReset(
            long traceId)
        {
            doServerReset(traceId, emptyRO);
        }

        private void doServerReset(
            long traceId,
            Flyweight extension)
        {
            if (!McpState.initialClosed(state))
            {
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                    extension);
                state = McpState.closedInitial(state);
            }
        }
    }

    private final class McpToolSearchServer
    {
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final GuardHandler filterGuard;
        private final McpProxyCache.McpListCache cache;
        private final int limitDefault;
        private final McpSearchToolCallScanner scanner;

        private int state;
        private boolean ready;
        private DirectBufferEx cachedBuf;
        private int cachedLen;
        private int emitOffset;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private McpToolSearchServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            GuardHandler filterGuard,
            McpProxyCache.McpListCache cache,
            int limitDefault)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.filterGuard = filterGuard;
            this.cache = cache;
            this.limitDefault = limitDefault;
            this.scanner = new McpSearchToolCallScanner();
        }

        private void onToolSearchMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onServerBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onServerData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onServerEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onServerAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onServerWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onServerReset(resetRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onServerBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            state = McpState.openingInitial(state);

            doServerBegin(traceId);
            flushServerWindow(traceId, 0L, 0, 0L, codecBuffer.capacity());
        }

        private void onServerData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            final boolean last = (flags & DATA_FLAG_FIN) != 0x00;
            scanner.feed(payload.buffer(), payload.offset(), payload.sizeof(), last);

            if (last)
            {
                onQueryReady(traceId);
            }
        }

        private void onQueryReady(
            long traceId)
        {
            final String query = scanner.query;
            if (scanner.malformed || query == null || query.isEmpty())
            {
                final McpResetExFW resetEx = mcpResetExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .error(e -> e.code(ERROR_CODE_INVALID_PARAMS).message(ERROR_MESSAGE_INVALID_PARAMS))
                    .build();
                doServerReset(traceId, resetEx);
            }
            else
            {
                final int limit = scanner.maxResults > 0 ? Math.min(scanner.maxResults, limitDefault) : limitDefault;
                final byte[] bytes = buildResponse(query, limit);
                cachedBuf = new UnsafeBufferEx(bytes);
                cachedLen = bytes.length;
                ready = true;
                emitIfReady(traceId);
            }
        }

        // MCP's CallToolResult.content only accepts text/image/audio/resource_link/resource blocks --
        // there is no "tool reference" content type, so the matches are carried in structuredContent
        // instead, with a serialized-JSON text block alongside for clients that predate
        // structuredContent (the pattern the spec itself recommends). search_tools is a cheap,
        // schema-free digest by design -- only each matched tool's name and description are copied,
        // as raw verbatim bytes straight out of cache.toolsBytes(), never a DOM rebuild; the full
        // definition (schema included) is resolved separately via describe_tool.
        private byte[] buildResponse(
            String query,
            int limit)
        {
            final Map<CharSequence, List<String>> scopesByName = cache.scopesByName();
            final Map<CharSequence, McpToolByteRange> rangesByName = cache.toolRangesByName();
            final byte[] toolsBytes = cache.toolsBytes();
            final List<McpToolSearchMatch> matches = cache.searchIndex().query(query);

            final int structuredLength = writeStructuredContent(matches, scopesByName, rangesByName, toolsBytes, limit);
            final int responseLength = writeSearchFamilyResponse(structuredLength);

            return Arrays.copyOf(searchResponseArray, responseLength);
        }

        // assembles {"tools":[<digest>,<digest>,...]} into searchStructuredArray, growing it to a safe
        // upper bound first -- each digest's copied name/description bytes are a subset of toolsBytes,
        // so toolsBytes.length plus the fixed per-match digest scaffolding is always enough, regardless
        // of which candidates are admitted
        private int writeStructuredContent(
            List<McpToolSearchMatch> matches,
            Map<CharSequence, List<String>> scopesByName,
            Map<CharSequence, McpToolByteRange> rangesByName,
            byte[] toolsBytes,
            int limit)
        {
            final int perMatchOverhead =
                DIGEST_NAME_OPEN.length + DIGEST_DESCRIPTION_KEY.length + DIGEST_CLOSE.length + 1;
            final int capacity = toolsBytes.length + SEARCH_TOOLS_OPEN.length + SEARCH_TOOLS_CLOSE.length +
                matches.size() * perMatchOverhead;
            if (searchStructuredArray.length < capacity)
            {
                searchStructuredArray = new byte[capacity];
            }

            int produced = 0;
            System.arraycopy(SEARCH_TOOLS_OPEN, 0, searchStructuredArray, produced, SEARCH_TOOLS_OPEN.length);
            produced += SEARCH_TOOLS_OPEN.length;

            int admitted = 0;
            for (int i = 0; i < matches.size() && admitted < limit; i++)
            {
                final McpToolSearchMatch match = matches.get(i);
                final List<String> roles = scopesByName.get(match.name);
                final McpToolByteRange range = rangesByName.get(match.name);
                // a null roles entry means no security scheme at all (or one that declares no
                // authorization), matching McpScopeFilter's own admit-without-checking convention
                // for the same scopesByName map -- only a non-null roles list is worth verifying
                if (range != null && (roles == null || filterGuard == null || filterGuard.verify(authorization, roles)))
                {
                    if (admitted > 0)
                    {
                        searchStructuredArray[produced++] = SEARCH_TOOLS_SEPARATOR;
                    }
                    produced += writeDigest(range, toolsBytes, produced);
                    admitted++;
                }
            }

            System.arraycopy(SEARCH_TOOLS_CLOSE, 0, searchStructuredArray, produced, SEARCH_TOOLS_CLOSE.length);
            produced += SEARCH_TOOLS_CLOSE.length;

            return produced;
        }

        // {"name":"<raw name bytes>"[,"description":"<raw description bytes>"]} -- literal byte
        // constants plus the two raw verbatim splices; no escaping needed since the source bytes are
        // already valid escaped JSON string content
        private int writeDigest(
            McpToolByteRange range,
            byte[] toolsBytes,
            int offset)
        {
            int produced = offset;

            System.arraycopy(DIGEST_NAME_OPEN, 0, searchStructuredArray, produced, DIGEST_NAME_OPEN.length);
            produced += DIGEST_NAME_OPEN.length;

            System.arraycopy(toolsBytes, range.nameOffset(), searchStructuredArray, produced, range.nameLength());
            produced += range.nameLength();

            if (range.hasDescription())
            {
                System.arraycopy(DIGEST_DESCRIPTION_KEY, 0, searchStructuredArray, produced, DIGEST_DESCRIPTION_KEY.length);
                produced += DIGEST_DESCRIPTION_KEY.length;

                System.arraycopy(toolsBytes, range.descriptionOffset(), searchStructuredArray, produced,
                    range.descriptionLength());
                produced += range.descriptionLength();
            }

            System.arraycopy(DIGEST_CLOSE, 0, searchStructuredArray, produced, DIGEST_CLOSE.length);
            produced += DIGEST_CLOSE.length;

            return produced - offset;
        }

        private void onServerEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            initialSeq = end.sequence();
            if (!ready)
            {
                onQueryReady(traceId);
            }
            state = McpState.closedInitial(state);
        }

        private void onServerAbort(
            AbortFW abort)
        {
            initialSeq = abort.sequence();
            state = McpState.closedInitial(state);
            doServerAbort(abort.traceId());
        }

        private void onServerWindow(
            WindowFW window)
        {
            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyPad = window.padding();
            state = McpState.openedReply(state);
            emitIfReady(window.traceId());
        }

        private void onServerReset(
            ResetFW reset)
        {
            replyAck = reset.acknowledge();
            state = McpState.closedReply(state);
        }

        private void emitIfReady(
            long traceId)
        {
            if (!ready || McpState.replyClosed(state))
            {
                return;
            }

            while (emitOffset < cachedLen)
            {
                final int replyWin = replyMax - (int) (replySeq - replyAck) - replyPad;
                if (replyWin <= 0)
                {
                    return;
                }
                final int chunkLen = Math.min(replyWin, cachedLen - emitOffset);
                doServerData(traceId, 0L, DATA_FLAG_COMPLETE, chunkLen, cachedBuf, emitOffset, chunkLen);
                emitOffset += chunkLen;
            }

            doServerEnd(traceId);
        }

        private void doServerBegin(
            long traceId)
        {
            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, emptyRO);
            state = McpState.openingReply(state);
        }

        private void doServerData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBufferEx payload,
            int offset,
            int length)
        {
            doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, flags, budgetId, reserved, payload, offset, length);
            replySeq += reserved;
        }

        private void doServerEnd(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerAbort(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            state = McpState.openedInitial(state);
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding);
        }

        private void flushServerWindow(
            long traceId,
            long budgetId,
            int padding,
            long minInitialNoAck,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialAck, initialSeq - minInitialNoAck);
            final int newInitialMax = Math.max(initialMax, minInitialMax);

            if (newInitialAck > initialAck || newInitialMax > initialMax || !McpState.initialOpened(state))
            {
                initialAck = newInitialAck;
                initialMax = newInitialMax;
                doServerWindow(traceId, budgetId, padding);
            }
        }

        private void doServerReset(
            long traceId,
            Flyweight extension)
        {
            if (!McpState.initialClosed(state))
            {
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                    extension);
                state = McpState.closedInitial(state);
            }
        }
    }

    private final class McpDescribeToolServer
    {
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final GuardHandler filterGuard;
        private final McpProxyCache.McpListCache cache;
        private final McpDescribeToolCallScanner scanner;

        private int state;
        private boolean ready;
        private DirectBufferEx cachedBuf;
        private int cachedLen;
        private int emitOffset;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private McpDescribeToolServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            GuardHandler filterGuard,
            McpProxyCache.McpListCache cache)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.filterGuard = filterGuard;
            this.cache = cache;
            this.scanner = new McpDescribeToolCallScanner();
        }

        private void onDescribeToolMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onServerBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onServerData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onServerEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onServerAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onServerWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onServerReset(resetRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onServerBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            state = McpState.openingInitial(state);

            doServerBegin(traceId);
            flushServerWindow(traceId, 0L, 0, 0L, codecBuffer.capacity());
        }

        private void onServerData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            final boolean last = (flags & DATA_FLAG_FIN) != 0x00;
            scanner.feed(payload.buffer(), payload.offset(), payload.sizeof(), last);

            if (last)
            {
                onNameReady(traceId);
            }
        }

        private void onNameReady(
            long traceId)
        {
            final String name = scanner.name;
            if (scanner.malformed || name == null || name.isEmpty())
            {
                final McpResetExFW resetEx = mcpResetExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .error(e -> e.code(ERROR_CODE_INVALID_PARAMS).message(ERROR_MESSAGE_INVALID_PARAMS))
                    .build();
                doServerReset(traceId, resetEx);
            }
            else
            {
                final byte[] bytes = buildResponse(name);
                cachedBuf = new UnsafeBufferEx(bytes);
                cachedLen = bytes.length;
                ready = true;
                emitIfReady(traceId);
            }
        }

        // exact name lookup against the same byte-range index search_tools uses, enforcing the same
        // per-tool scope admission rule (a null roles entry means no security scheme at all, or one
        // that declares no authorization). Unlike search_tools, describe_tool returns the tool's
        // entire cached JSON object verbatim -- the caller is resolving the full schema here, not
        // browsing a digest -- wrapped in the same {"tools":[...]} envelope shape. Not found and
        // not admitted are indistinguishable to the caller by design, both answered with the same
        // fixed isError:true response, so existence of a scope-guarded tool is never leaked
        private byte[] buildResponse(
            String name)
        {
            final Map<CharSequence, List<String>> scopesByName = cache.scopesByName();
            final Map<CharSequence, McpToolByteRange> rangesByName = cache.toolRangesByName();
            final McpToolByteRange range = rangesByName.get(name);
            final List<String> roles = scopesByName.get(name);
            final boolean admitted =
                range != null && (roles == null || filterGuard == null || filterGuard.verify(authorization, roles));

            final byte[] response;
            if (admitted)
            {
                final byte[] toolsBytes = cache.toolsBytes();
                final int structuredLength = writeDescribeStructuredContent(range, toolsBytes);
                final int responseLength = writeSearchFamilyResponse(structuredLength);
                response = Arrays.copyOf(searchResponseArray, responseLength);
            }
            else
            {
                response = DESCRIBE_NOT_FOUND_RESPONSE;
            }

            return response;
        }

        // assembles {"tools":[<verbatim whole-object bytes>]} into searchStructuredArray -- unlike
        // search_tools' per-match digest, this copies the tool's entire cached JSON object verbatim,
        // never re-serialized or re-escaped
        private int writeDescribeStructuredContent(
            McpToolByteRange range,
            byte[] toolsBytes)
        {
            final int capacity = SEARCH_TOOLS_OPEN.length + range.length() + SEARCH_TOOLS_CLOSE.length;
            if (searchStructuredArray.length < capacity)
            {
                searchStructuredArray = new byte[capacity];
            }

            int produced = 0;
            System.arraycopy(SEARCH_TOOLS_OPEN, 0, searchStructuredArray, produced, SEARCH_TOOLS_OPEN.length);
            produced += SEARCH_TOOLS_OPEN.length;

            System.arraycopy(toolsBytes, range.offset(), searchStructuredArray, produced, range.length());
            produced += range.length();

            System.arraycopy(SEARCH_TOOLS_CLOSE, 0, searchStructuredArray, produced, SEARCH_TOOLS_CLOSE.length);
            produced += SEARCH_TOOLS_CLOSE.length;

            return produced;
        }

        private void onServerEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            initialSeq = end.sequence();
            if (!ready)
            {
                onNameReady(traceId);
            }
            state = McpState.closedInitial(state);
        }

        private void onServerAbort(
            AbortFW abort)
        {
            initialSeq = abort.sequence();
            state = McpState.closedInitial(state);
            doServerAbort(abort.traceId());
        }

        private void onServerWindow(
            WindowFW window)
        {
            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyPad = window.padding();
            state = McpState.openedReply(state);
            emitIfReady(window.traceId());
        }

        private void onServerReset(
            ResetFW reset)
        {
            replyAck = reset.acknowledge();
            state = McpState.closedReply(state);
        }

        private void emitIfReady(
            long traceId)
        {
            if (!ready || McpState.replyClosed(state))
            {
                return;
            }

            while (emitOffset < cachedLen)
            {
                final int replyWin = replyMax - (int) (replySeq - replyAck) - replyPad;
                if (replyWin <= 0)
                {
                    return;
                }
                final int chunkLen = Math.min(replyWin, cachedLen - emitOffset);
                doServerData(traceId, 0L, DATA_FLAG_COMPLETE, chunkLen, cachedBuf, emitOffset, chunkLen);
                emitOffset += chunkLen;
            }

            doServerEnd(traceId);
        }

        private void doServerBegin(
            long traceId)
        {
            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, emptyRO);
            state = McpState.openingReply(state);
        }

        private void doServerData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBufferEx payload,
            int offset,
            int length)
        {
            doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, flags, budgetId, reserved, payload, offset, length);
            replySeq += reserved;
        }

        private void doServerEnd(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerAbort(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
                state = McpState.closedReply(state);
            }
        }

        private void doServerWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            state = McpState.openedInitial(state);
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding);
        }

        private void flushServerWindow(
            long traceId,
            long budgetId,
            int padding,
            long minInitialNoAck,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialAck, initialSeq - minInitialNoAck);
            final int newInitialMax = Math.max(initialMax, minInitialMax);

            if (newInitialAck > initialAck || newInitialMax > initialMax || !McpState.initialOpened(state))
            {
                initialAck = newInitialAck;
                initialMax = newInitialMax;
                doServerWindow(traceId, budgetId, padding);
            }
        }

        private void doServerReset(
            long traceId,
            Flyweight extension)
        {
            if (!McpState.initialClosed(state))
            {
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                    extension);
                state = McpState.closedInitial(state);
            }
        }
    }

    private final class McpClient implements McpRouteRequest
    {
        private final McpServer server;
        private final long originId;
        private final long routedId;
        private final McpLifecycleClient lifecycle;

        private long initialId;
        private long replyId;

        private MessageConsumer sender;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private McpClient(
            McpServer server,
            long routedId)
        {
            this.server = server;
            this.originId = server.lifecycle.originId;
            this.routedId = routedId;
            this.lifecycle = server.lifecycle.supplyClient(routedId);
        }

        private void doClientBegin(
            long traceId)
        {
            lifecycle.doClientBegin(traceId);
            lifecycle.register(traceId, this);
        }

        @Override
        public void onLifecycleSettled(
            long traceId)
        {
            if (McpState.initialClosed(state) || McpState.replyClosed(state))
            {
                return;
            }

            final String sid = lifecycle.sessionId;
            if (sid != null)
            {
                initialId = supplyInitialIdHash.apply(routedId, sid.hashCode());
                replyId = supplyReplyId.applyAsLong(initialId);

                final McpBeginExFW beginEx = mcpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(mcpTypeId)
                    .inject(b -> injectInitialBeginEx(b, sid, server.identifier, server.contentLength - server.prefix.length()))
                    .build();

                sender = newStream(this::onClientMessage, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, server.authorization, server.affinity, beginEx);
                state = McpState.openingInitial(state);
            }
            else
            {
                server.doServerReset(traceId);
            }
        }

        private void doClientData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBufferEx payload,
            int offset,
            int length)
        {
            if (!McpState.closed(state))
            {
                doData(sender, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, server.authorization,
                    flags, budgetId, reserved, payload, offset, length);
                initialSeq += reserved;
            }
        }

        private void doClientFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(sender, originId, routedId, initialId,
                initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, reserved, extension);
        }

        private void doClientEnd(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                if (McpState.initialOpening(state))
                {
                    doEnd(sender, originId, routedId, initialId,
                        initialSeq, initialAck, initialMax, traceId, server.authorization);
                }
                state = McpState.closedInitial(state);
            }
        }

        private void doClientAbort(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                if (McpState.initialOpening(state))
                {
                    doAbort(sender, originId, routedId, initialId,
                        initialSeq, initialAck, initialMax, traceId, server.authorization);
                }
                state = McpState.closedInitial(state);
            }
        }

        private void doClientWindow(
            long traceId,
            long budgetId,
            int padding)
        {
            if (McpState.initialOpening(state))
            {
                state = McpState.openedReply(state);
                doWindow(sender, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, server.authorization, budgetId, padding);
            }
        }

        private void flushClientWindow(
            long traceId,
            long budgetId,
            int padding,
            long minReplyNoAck,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(replyAck, replySeq - minReplyNoAck);
            final int newReplyMax = Math.max(replyMax, minReplyMax);

            if (newReplyAck > replyAck || newReplyMax > replyMax || !McpState.replyOpened(state))
            {
                replyAck = newReplyAck;
                replyMax = newReplyMax;
                doClientWindow(traceId, budgetId, padding);
            }
        }

        private void doClientReset(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                if (McpState.initialOpening(state))
                {
                    doReset(sender, originId, routedId, replyId,
                        replySeq, replyAck, replyMax, traceId, server.authorization, emptyRO);
                }
                state = McpState.closedReply(state);
            }
        }

        private void doClientChallenge(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            doChallenge(sender, originId, routedId, replyId,
                replySeq, replyAck, replyMax, traceId, authorization, extension);
        }

        private void onClientMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onClientBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onClientData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onClientEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onClientAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onClientFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onClientWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onClientReset(reset);
                break;
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onClientChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onClientFlush(
            FlushFW flush)
        {
            server.doServerFlush(flush.traceId(), flush.authorization(),
                flush.budgetId(), flush.reserved(), flush.extension());
        }

        private void onClientChallenge(
            ChallengeFW challenge)
        {
            server.doServerChallenge(challenge.traceId(), challenge.authorization(), challenge.extension());
        }

        private void onClientBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            replySeq = sequence;
            replyAck = acknowledge;

            state = McpState.openedInitial(state);

            final McpBeginExFW beginEx = extension.get(mcpBeginExRO::tryWrap);
            final Flyweight replyExtension = beginEx != null
                ? rewriteReplyBeginEx(beginEx)
                : emptyRO;
            server.doServerBegin(traceId, replyExtension);

            flushClientWindow(traceId, 0L, 0, 0L, 0);
        }

        private Flyweight rewriteReplyBeginEx(
            McpBeginExFW beginEx)
        {
            final String sid = server.lifecycle.sessionId;
            return mcpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .inject(b -> injectReplyBeginEx(b, sid, beginEx))
                .build();
        }

        private void onClientData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            server.doServerData(traceId, budgetId, flags, reserved,
                payload.buffer(), payload.offset(), payload.sizeof());
        }

        private void onClientEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;

            assert replyAck <= replySeq;

            state = McpState.closedReply(state);
            server.doServerEnd(traceId, end.extension());
        }

        private void onClientAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;

            assert replyAck <= replySeq;

            state = McpState.closedReply(state);
            server.doServerAbort(traceId);
        }

        private void onClientWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            initialAck = acknowledge;
            initialMax = maximum;
            initialPad = padding;

            assert initialAck <= initialSeq;
            server.flushServerWindow(traceId, budgetId, padding, initialSeq - initialAck, initialMax);
        }

        private void onClientReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;

            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            state = McpState.closedInitial(state);

            server.doServerReset(traceId, extension);
        }
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        final MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);
        assert receiver != null;

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
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
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        DirectBufferEx payload,
        int offset,
        int length)
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
            .payload(payload, offset, length)
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
        long authorization)
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
            .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        long authorization)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
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
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
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
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
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
        int padding)
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

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }
}
