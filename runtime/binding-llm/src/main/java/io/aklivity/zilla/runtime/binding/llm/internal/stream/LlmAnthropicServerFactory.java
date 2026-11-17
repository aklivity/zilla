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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAnthropicDialectFactorySpi.NAME;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Map;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;

import jakarta.json.Json;
import jakarta.json.JsonException;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.llm.config.LlmAuthorizationResult;
import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmRouteConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmBinding;
import io.aklivity.zilla.runtime.binding.llm.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.queue.LlmReplyFrameFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.Capability;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmAbortExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmChallengeExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmEndExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmErrorFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmFlushExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmResetExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmUsageFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.StringEventPositions;
import io.aklivity.zilla.runtime.common.json.view.JsonStringBody;
import io.aklivity.zilla.runtime.common.json.view.JsonStringView;
import io.aklivity.zilla.runtime.common.lang.SegmentTransferTarget;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebit;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.util.Flags;

public final class LlmAnthropicServerFactory implements LlmDialectHandler
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final int EVENT_ATTRIBUTES_CAPACITY = 2048;
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_PATH = ":path";
    private static final String HEADER_STATUS = ":status";
    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_API_KEY = "x-api-key";
    private static final String HEADER_ANTHROPIC_VERSION = "anthropic-version";
    private static final String HEADER_ANTHROPIC_BETA = "anthropic-beta";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String HEADER_AUTHORIZATION = "authorization";
    private static final String METHOD_POST = "POST";
    private static final String PATH_MESSAGES = "/v1/messages";
    private static final String VENDOR_PREFIX = NAME + ":";
    private static final String STATUS_OK = "200";
    private static final String STATUS_UNAUTHORIZED = "401";
    private static final int NO_MAX_OUTPUT_TOKENS = -1;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_UNSUPPORTED_MEDIA_TYPE = 415;
    private static final int STATUS_INTERNAL_SERVER_ERROR = 500;
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String UNAUTHORIZED_BODY = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"," +
        "\"message\":\"invalid x-api-key\"}}";

    private static final String MESSAGE_PARSE_ERROR = "There was an error parsing the body";
    private static final String MESSAGE_MODEL_MISSING = "model: Field required";
    private static final String MESSAGE_MODEL_NOT_FOUND = "model: %s";
    private static final String MESSAGE_MODEL_ORDER =
        "model: must precede messages, system and tools in a request that does not fit the decode buffer";
    private static final String MESSAGE_ROLE_MISSING = "messages.%d.role: Field required";
    private static final String MESSAGE_ROLE_ORDER = "role must precede content in each message";
    private static final String MESSAGE_TOOL_USE_ID_ORDER = "tool_use_id must precede content in a tool_result block";
    private static final String MESSAGE_PART_TYPE_ORDER = "type must be the first member of each content block";
    private static final String MESSAGE_ATTRIBUTE_TOO_LARGE = "a content block attribute value is too large";
    private static final String MESSAGE_BLOCK_INVALID = "Invalid content block";
    private static final String BLOCK_TOOL_USE = "tool_use";
    private static final Map<String, StringEventPositions> PARSER_CONFIG =
        Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED);

    private static final int REPLY_PADDING_FRACTION = 8;
    private static final int RESPONSE_ESCAPE_EXPANSION = 6;
    private static final int REPLY_FRAME_OVERHEAD = 64;
    private static final int REPLY_TAIL_MAX = 64;
    private static final int ENTRY_HEADER = 22;
    private static final int ENTRY_FLAGS = 4;
    private static final int ENTRY_EVENT = 5;
    private static final int ENTRY_TRACE = 6;
    private static final int ENTRY_AUTHORIZATION = 14;
    private static final int REPLY_FLAG_END = 0x40;
    private static final int REPLY_FLAG_ERROR = 0x20;
    private static final int ARGS_HEADROOM = 16;
    private static final int KEY_FRAMING = 4;
    private static final Map<String, Object> ESCAPED_OPTIONS = Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true);
    private static final int ARGS_IDLE = 0;
    private static final int ARGS_FULL = 1;
    private static final int ARGS_DONE = 2;
    private static final int ARGS_HELD = 3;
    private static final int ARGS_WAIT = 4;
    private static final String MEMBER_ARGUMENTS = "arguments";
    private static final String MEMBER_INPUT = "input";
    private static final String MEMBER_SIGNATURE = "x:signature";
    private static final String MEMBER_REDACTED_DATA = "x:redacted_data";
    private static final String MEMBER_SIGNATURE_FIELD = "signature";
    private static final String MEMBER_DATA_FIELD = "data";
    private static final DirectBufferEx QUOTE = new UnsafeBufferEx("\"".getBytes(UTF_8));

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);
    private static final DirectBufferEx EMPTY_BUFFER = new UnsafeBufferEx(new byte[0]);
    private static final MutableDirectBufferEx NO_BUFFER = new UnsafeBufferEx(new byte[0]);

    private static final DirectBufferEx STREAM_OPTIONS_SECTION =
        new UnsafeBufferEx("{\"stream_options\":{\"include_usage\":true}}".getBytes(UTF_8));
    private static final DirectBufferEx CONTROL_CLOSE = new UnsafeBufferEx("}".getBytes(UTF_8));
    private static final String SHARED_PREFIX = "x:";

    private static final int MIN_WINDOW = 64;
    private static final int CONTROL_GROWTH = 3;


    private static final int HELD_TAIL_MAX = LlmBreakpointStash.CAPACITY + 2;
    private static final String MEMBER_CACHE_CONTROL = "cache_control";
    private static final String MEMBER_CACHE_CONTROL_TYPE = "type";
    private static final String MEMBER_CACHE_CONTROL_TTL = "ttl";
    private static final String CACHE_CONTROL_EPHEMERAL = "ephemeral";
    private static final int BREAKPOINT_KEY_NONE = 0;
    private static final int BREAKPOINT_KEY_TYPE = 1;
    private static final int BREAKPOINT_KEY_TTL = 2;
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_STREAM = "stream";
    private static final String MEMBER_TOOL_CHOICE = "tool_choice";
    private static final String MEMBER_OUTPUT_CONFIG = "output_config";
    private static final String MEMBER_THINKING = "thinking";
    private static final String MEMBER_METADATA = "metadata";
    private static final String MEMBER_SERVICE_TIER = "service_tier";
    private static final DirectBufferEx TEXT_OPEN = new UnsafeBufferEx("{".getBytes(UTF_8));
    private static final DirectBufferEx TEXT_KEY = new UnsafeBufferEx("\"text\":\"".getBytes(UTF_8));
    private static final DirectBufferEx TEXT_SUFFIX = new UnsafeBufferEx("\"}".getBytes(UTF_8));
    private static final int TEXT_FRAMING = "{\"text\":\"".length() + "\"}".length();
    private static final int MIN_TEXT_INPUT = "\"system\":\"\"".length();

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_TOOL_RESULT = "tool_result";
    private static final String SUFFIX_TOOL_RESULT = "_tool_result";

    private static final int CONTEXT_MESSAGE = 0;
    private static final int CONTEXT_SYSTEM = 1;
    private static final int CONTEXT_TOOL_RESULT = 2;


    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final LlmRequestHeaders headersRO = new LlmRequestHeaders();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final HttpDataExFW.Builder httpDataExRW = new HttpDataExFW.Builder();
    private final LlmBeginExFW llmBeginExRO = new LlmBeginExFW();
    private final LlmChallengeExFW llmChallengeExRO = new LlmChallengeExFW();
    private final LlmBeginExFW.Builder llmBeginExRW = new LlmBeginExFW.Builder();
    private final LlmDataExFW llmDataExRO = new LlmDataExFW();
    private final LlmSectionAttributes replyAttributes = new LlmSectionAttributes();
    private final LlmDataExFW.Builder llmDataExRW = new LlmDataExFW.Builder();
    private final LlmFlushExFW.Builder llmFlushExRW = new LlmFlushExFW.Builder();
    private final LlmResetExFW llmResetExRO = new LlmResetExFW();
    private final LlmEndExFW llmEndExRO = new LlmEndExFW();
    private final LlmAbortExFW llmAbortExRO = new LlmAbortExFW();
    private final Array32FW<HttpHeaderFW> eventHeadersRO = new Array32FW<>(new HttpHeaderFW());
    private final LlmReplyFrameFW.Builder replyFrameRW = new LlmReplyFrameFW.Builder();
    private final LlmReplyFrameFW replyFrameRO = new LlmReplyFrameFW();
    private final LlmMemberKeys memberKeys = new LlmMemberKeys(NAME);
    private final LlmMemberKeys foreignKeys = new LlmMemberKeys(null);

    private final EngineContext context;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;
    private final BufferPool decodePool;
    private final BufferPool replyEncodePool;
    private final BufferPool replyPool;
    private final BufferPool blockPool;
    private final Long2ObjectHashMap<LlmBindingConfig> bindings;
    private final int decodeMax;
    private final int replyPadding;
    private final int llmTypeId;
    private final int httpTypeId;

    private final LlmAnthropicServerDecoder decodeStart = this::decodeStart;
    private final LlmAnthropicServerDecoder decodeRootStart = this::decodeRootStart;
    private final LlmAnthropicServerDecoder decodeLookahead = this::decodeLookahead;
    private final LlmAnthropicServerDecoder decodeRoot = this::decodeRoot;
    private final LlmAnthropicServerDecoder decodeRootControl = this::decodeRootControl;
    private final LlmAnthropicServerDecoder decodeMessagesStart = this::decodeMessagesStart;
    private final LlmAnthropicServerDecoder decodeMessages = this::decodeMessages;
    private final LlmAnthropicServerDecoder decodeMessage = this::decodeMessage;
    private final LlmAnthropicServerDecoder decodeRole = this::decodeRole;
    private final LlmAnthropicServerDecoder decodeSystemStart = this::decodeSystemStart;
    private final LlmAnthropicServerDecoder decodeToolResult = this::decodeToolResult;
    private final LlmAnthropicServerDecoder decodeToolUseId = this::decodeToolUseId;
    private final LlmAnthropicServerDecoder decodeToolResultContent = this::decodeToolResultContent;
    private final LlmAnthropicServerDecoder decodeCitationsStart = this::decodeCitationsStart;
    private final LlmAnthropicServerDecoder decodeContent = this::decodeContent;
    private final LlmAnthropicServerDecoder decodeToolsStart = this::decodeToolsStart;
    private final LlmAnthropicServerDecoder decodeTools = this::decodeTools;
    private final LlmAnthropicServerDecoder decodeParts = this::decodeParts;
    private final LlmAnthropicServerDecoder decodePart = this::decodePart;
    private final LlmAnthropicServerDecoder decodeToolIsError = this::decodeToolIsError;
    private final LlmAnthropicServerDecoder decodeServerToolBlock = this::decodeServerToolBlock;
    private final LlmAnthropicServerDecoder decodeTool = this::decodeTool;
    private final LlmAnthropicServerDecoder decodeToolStart = this::decodeToolStart;
    private final LlmAnthropicServerDecoder decodeBlockStart = this::decodeBlockStart;
    private final LlmAnthropicServerDecoder decodeText = this::decodeText;
    private final LlmAnthropicServerDecoder decodeTextPiece = this::decodeText;
    private final LlmAnthropicServerDecoder decodeSkip = this::decodeSkip;
    private final LlmAnthropicServerDecoder decodeControlValue = this::decodeControlValue;
    private final LlmAnthropicServerDecoder decodeControlSeparator = this::decodeControlSeparator;
    private final LlmAnthropicServerDecoder decodeBreakpointValue = this::decodeBreakpointValue;
    private final LlmAnthropicServerDecoder decodeMaxOutputTokensValue = this::decodeMaxOutputTokensValue;
    private final LlmAnthropicServerDecoder decodeEnd = this::decodeEnd;
    private final LlmAnthropicServerDecoder decodeIgnore = this::decodeIgnore;

    private final LlmAnthropicServerDecoder decodeBlockKey = this::decodeBlockKey;
    private final LlmAnthropicServerDecoder decodeBlockDrop = this::decodeBlockDrop;
    private final LlmAnthropicServerDecoder decodeBlockCopy = this::decodeBlockCopy;
    private final LlmAnthropicServerDecoder decodeBlockString = this::decodeBlockString;
    private final LlmAnthropicServerDecoder decodeBlockObject = this::decodeBlockObject;
    private final LlmAnthropicServerDecoder decodeBlockOptional = this::decodeBlockOptional;
    private final LlmAnthropicServerDecoder decodeBlockHold = this::decodeBlockHold;
    private final LlmAnthropicServerDecoder decodeBlockBreakpoint = this::decodeBlockBreakpoint;
    private final LlmAnthropicServerDecoder decodeBlockSource = this::decodeBlockSource;
    private final LlmAnthropicServerDecoder decodeBlockSourceKey = this::decodeBlockSourceKey;
    private final LlmAnthropicServerDecoder decodeBlockElement = this::decodeBlockElement;
    private final LlmAnthropicServerDecoder decodeBlockData = this::decodeBlockData;
    private final LlmAnthropicServerDecoder decodeBlockCitations = this::decodeBlockCitations;
    private final LlmAnthropicServerDecoder decodeBlockRange = this::decodeBlockRange;
    private final LlmAnthropicServerDecoder decodeControlStream = this::decodeControlStream;
    private final LlmAnthropicServerDecoder decodeControlToolChoice = this::decodeControlToolChoice;
    private final LlmAnthropicServerDecoder decodeControlTier = this::decodeControlTier;
    private final LlmAnthropicServerDecoder decodeControlContainer = this::decodeControlContainer;
    private final LlmAnthropicServerDecoder decodeControlResidueNone = this::decodeControlResidueNone;
    private final LlmAnthropicServerDecoder decodeControlResidueOpen = this::decodeControlResidueOpen;
    private final LlmAnthropicServerDecoder decodeControlResidueLifted = this::decodeControlResidueLifted;
    private final LlmAnthropicServerDecoder decodeControlType = this::decodeControlType;
    private final LlmAnthropicServerDecoder decodeControlFormat = this::decodeControlFormat;
    private final LlmAnthropicServerDecoder decodeControlFormatKey = this::decodeControlFormatKey;
    private final LlmAnthropicServerDecoder decodeControlFormatType = this::decodeControlFormatType;
    private final LlmAnthropicServerDecoder decodeControlMember = this::decodeControlMember;
    private final LlmBlockHandler blockKey = this::onBlockKey;
    private final LlmBlockHandler blockDrop = this::onBlockDrop;
    private final LlmBlockHandler blockCopy = this::onBlockCopy;
    private final LlmBlockHandler blockString = this::onBlockString;
    private final LlmBlockHandler blockObject = this::onBlockObject;
    private final LlmBlockHandler blockOptional = this::onBlockOptional;
    private final LlmBlockHandler blockHold = this::onBlockHold;
    private final LlmBlockHandler blockBreakpoint = this::onBlockBreakpoint;
    private final LlmBlockHandler blockSource = this::onBlockSource;
    private final LlmBlockHandler blockSourceKey = this::onBlockSourceKey;
    private final LlmBlockHandler blockElement = this::onBlockElement;
    private final LlmBlockHandler blockData = this::onBlockData;
    private final LlmBlockHandler blockCitations = this::onBlockCitations;
    private final LlmBlockHandler blockRange = this::onBlockRange;
    private final LlmBlockHandler controlStream = this::onControlStream;
    private final LlmBlockHandler controlToolChoice = this::onControlToolChoice;
    private final LlmBlockHandler controlTier = this::onControlTier;
    private final LlmBlockHandler controlContainer = this::onControlContainer;
    private final LlmBlockHandler controlResidueNone = this::onControlResidueNone;
    private final LlmBlockHandler controlResidueOpen = this::onControlResidueOpen;
    private final LlmBlockHandler controlResidueLifted = this::onControlResidueLifted;
    private final LlmBlockHandler controlType = this::onControlType;
    private final LlmBlockHandler controlFormat = this::onControlFormat;
    private final LlmBlockHandler controlFormatKey = this::onControlFormatKey;
    private final LlmBlockHandler controlFormatType = this::onControlFormatType;

    private final LlmAnthropicReplyDecoder decodeReplyNext = this::decodeReplyNext;
    private final LlmAnthropicReplyDecoder decodeReplyResume = this::decodeReplyResume;
    private final LlmAnthropicReplyDecoder decodeReplyEnd = this::decodeReplyEnd;
    private final LlmAnthropicReplyDecoder decodeReplyFinishDelta = this::decodeReplyFinishDelta;
    private final LlmAnthropicReplyDecoder decodeReplyFinishStop = this::decodeReplyFinishStop;
    private final LlmAnthropicReplyDecoder decodeReplyFinishBody = this::decodeReplyFinishBody;
    private final LlmAnthropicReplyDecoder decodeReplyFinishEnd = this::decodeReplyFinishEnd;
    private final LlmAnthropicReplyDecoder decodeReplyTextRoot = this::decodeReplyTextRoot;
    private final LlmAnthropicReplyDecoder decodeReplyTextObject = this::decodeReplyTextObject;
    private final LlmAnthropicReplyDecoder decodeReplyTextKey = this::decodeReplyTextKey;
    private final LlmAnthropicReplyDecoder decodeReplyTextValue = this::decodeReplyTextValue;
    private final LlmAnthropicReplyDecoder decodeReplyTextHeld = this::decodeReplyTextHeld;
    private final LlmAnthropicReplyDecoder decodeReplyTextContinue = this::decodeReplyTextContinue;
    private final LlmAnthropicReplyDecoder decodeReplyTextSkip = this::decodeReplyTextSkip;
    private final LlmAnthropicReplyDecoder decodeReplyArgs = this::decodeReplyArgs;
    private final LlmAnthropicReplyDecoder decodeReplyArgsHeld = this::decodeReplyArgsHeld;
    private final LlmAnthropicReplyDecoder decodeReplyControlValue = this::decodeReplyControlValue;
    private final LlmAnthropicReplyDecoder decodeReplyControlHeld = this::decodeReplyControlHeld;
    private final LlmAnthropicReplyDecoder decodeReplyControlJudge = this::decodeReplyControlJudge;
    private final LlmAnthropicReplyDecoder decodeReplyTextDone = this::decodeReplyTextDone;

    private final UnsafeBufferEx pieceBuffer;
    private final SegmentTransferTarget pieceTarget = new SegmentTransferTarget();
    private final JsonStringBody body = new JsonStringBody();

    public LlmAnthropicServerFactory(
        EngineContext context)
    {
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.decodePool = context.bufferPool();
        this.decodeMax = decodePool.slotCapacity();
        this.replyEncodePool = context.bufferPool().duplicate();
        this.replyPool = context.bufferPool().duplicate();
        this.blockPool = context.bufferPool().duplicate();
        this.replyPadding = replyPool.slotCapacity() / REPLY_PADDING_FRACTION;
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.bindings = new Long2ObjectHashMap<>();
        this.pieceBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.llmTypeId = context.supplyTypeId(LlmBinding.NAME);
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
    }

    public void attach(
        LlmBindingConfig binding)
    {
        bindings.put(binding.id, binding);
    }

    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public boolean detect(
        JsonEnvelope headers)
    {
        final boolean pathMatched = LlmHeaderBytes.equalToIgnoreCase(headers.get(HEADER_METHOD, 0), METHOD_POST) &&
            LlmHeaderBytes.equalTo(headers.get(HEADER_PATH, 0), PATH_MESSAGES);
        final boolean versionPresent = headers.get(HEADER_ANTHROPIC_VERSION, 0) != null;
        final boolean apiKeyWithoutAuthorization = headers.get(HEADER_API_KEY, 0) != null &&
            headers.get(HEADER_AUTHORIZATION, 0) == null;

        return pathMatched || versionPresent || apiKeyWithoutAuthorization;
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
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final HttpBeginExFW httpBeginEx = begin.extension().get(httpBeginExRO::tryWrap);

        final LlmBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null && httpBeginEx != null)
        {
            final LlmRequestHeaders headers = headersRO.wrap(httpBeginEx);
            final String contentType = header(headers, HEADER_CONTENT_TYPE);
            final String version = header(headers, HEADER_ANTHROPIC_VERSION);
            final String beta = header(headers, HEADER_ANTHROPIC_BETA);

            if (!LlmHeaderBytes.isMediaType(headers.get(HEADER_CONTENT_TYPE, 0), CONTENT_TYPE_JSON))
            {
                newStream = new LlmAnthropicRejectHandler(network, originId, routedId, initialId,
                    Integer.toString(STATUS_UNSUPPORTED_MEDIA_TYPE),
                    errorBody(STATUS_UNSUPPORTED_MEDIA_TYPE, null, null))::onNetMessage;
            }
            else
            {
                final LlmAuthorizationResult authResult = binding.authorize(
                    begin.traceId(), routedId, initialId, authorization, headers, HEADER_API_KEY);

                if (!authResult.authorized())
                {
                    newStream = new LlmAnthropicRejectHandler(network, originId, routedId, initialId,
                        STATUS_UNAUTHORIZED, UNAUTHORIZED_BODY)::onNetMessage;
                }
                else if (version == null || !ANTHROPIC_VERSION.equals(version))
                {
                    newStream = new LlmAnthropicRejectHandler(network, originId, routedId, initialId,
                        Integer.toString(STATUS_BAD_REQUEST),
                        errorBody(STATUS_BAD_REQUEST, null, version == null
                            ? "anthropic-version: header is required"
                            : "anthropic-version: unsupported version " + version))::onNetMessage;
                }
                else
                {
                    final LlmRouteConfig route = binding.resolve(authResult.authorization());

                    if (route != null)
                    {
                        newStream = new LlmAnthropicServer(
                                network,
                                originId,
                                routedId,
                                initialId,
                                binding,
                                authResult.authorization(),
                                contentType,
                                beta,
                                authResult.deauthorize())::onNetMessage;
                    }
                    else
                    {
                        authResult.deauthorize().run();
                    }
                }
            }
        }

        return newStream;
    }

    private static String reason(
        int status)
    {
        return switch (status)
        {
        case 400 -> "Bad Request";
        case 401 -> "Unauthorized";
        case 403 -> "Forbidden";
        case 404 -> "Not Found";
        case 408 -> "Request Timeout";
        case 413 -> "Payload Too Large";
        case 415 -> "Unsupported Media Type";
        case 422 -> "Unprocessable Entity";
        case 429 -> "Too Many Requests";
        case 500 -> "Internal Server Error";
        case 502 -> "Bad Gateway";
        case 503 -> "Service Unavailable";
        case 504 -> "Gateway Timeout";
        default -> "Error";
        };
    }

    private static String header(
        JsonEnvelope headers,
        String name)
    {
        final DirectBufferEx value = headers.get(name, 0);
        return value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
    }

    private static DirectBufferEx asBuffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }

    private static String errorBody(
        int status,
        String type,
        String message)
    {
        return Json.createObjectBuilder()
            .add("type", "error")
            .add("error", Json.createObjectBuilder()
                .add("type", type != null ? type : errorType(status))
                .add("message", message != null ? message : reason(status)))
            .build()
            .toString();
    }

    private static String errorType(
        int status)
    {
        return switch (status)
        {
        case 401 -> "authentication_error";
        case 403 -> "permission_error";
        case 404 -> "not_found_error";
        case 413 -> "request_too_large";
        case 429 -> "rate_limit_error";
        case 529 -> "overloaded_error";
        default -> status >= 500 ? "api_error" : "invalid_request_error";
        };
    }

    @FunctionalInterface
    private interface LlmBlockHandler
    {
        int handle(
            LlmAnthropicServer server,
            JsonEvent event,
            JsonParserEx parser);
    }

    private int decodeBlock(
        LlmAnthropicServer server,
        long traceId,
        LlmAnthropicServerDecoder state,
        LlmBlockHandler handler)
    {
        final JsonParserEx parser = server.parser;

        while (server.decoder == state && parser.hasNextEvent())
        {
            final int outcome = server.onBlockEvent(parser.nextEvent(), parser, handler);

            if (outcome == LlmAnthropicBlockWriter.REJECT)
            {
                server.onDecodeInvalidRequest(traceId, MESSAGE_BLOCK_INVALID);
            }
        }

        return server.position();
    }

    private int decodeBlockKey(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockKey, blockKey);
    }

    private int decodeBlockDrop(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockDrop, blockDrop);
    }

    private int decodeBlockCopy(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockCopy, blockCopy);
    }

    private int decodeBlockString(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockString, blockString);
    }

    private int decodeBlockObject(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockObject, blockObject);
    }

    private int decodeBlockOptional(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockOptional, blockOptional);
    }

    private int decodeBlockHold(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockHold, blockHold);
    }

    private int decodeBlockBreakpoint(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockBreakpoint, blockBreakpoint);
    }

    private int decodeBlockSource(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockSource, blockSource);
    }

    private int decodeBlockSourceKey(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockSourceKey, blockSourceKey);
    }

    private int decodeBlockElement(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockElement, blockElement);
    }

    private int decodeBlockData(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockData, blockData);
    }

    private int decodeBlockCitations(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockCitations, blockCitations);
    }

    private int decodeBlockRange(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockRange, blockRange);
    }

    private int decodeControlStream(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeControlStream, controlStream);
    }

    private int decodeControlToolChoice(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeControlToolChoice, controlToolChoice);
    }

    private int decodeControlTier(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeControlTier, controlTier);
    }

    private int decodeControlContainer(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeControlContainer, controlContainer);
    }

    private int decodeControlResidueNone(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeControlResidueNone, controlResidueNone);
    }

    private int decodeControlResidueOpen(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeControlResidueOpen, controlResidueOpen);
    }

    private int decodeControlResidueLifted(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeControlResidueLifted, controlResidueLifted);
    }

    private int decodeControlType(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeControlType, controlType);
    }

    private int decodeControlFormat(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeControlFormat, controlFormat);
    }

    private int decodeControlFormatKey(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeControlFormatKey, controlFormatKey);
    }

    private int decodeControlFormatType(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeControlFormatType, controlFormatType);
    }

    private int decodeControlMember(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        server.endControlMember(traceId, authorization);

        return server.position();
    }

    private int onBlockRange(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.rangeValue(event, parser));
    }

    private int onControlStream(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        server.streaming = event == JsonEvent.VALUE_TRUE;

        return onBlockValue(server, server.blockWriter.streamValue(event));
    }

    private int onControlToolChoice(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.toolChoiceValue(event, parser));
    }

    private int onControlTier(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        final int outcome = server.blockWriter.tierValue(event, parser);

        if (outcome == LlmAnthropicBlockWriter.MORE)
        {
            server.decoder = decodeBlockCopy;
        }

        return onBlockValue(server, outcome);
    }

    private int onControlContainer(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = server.blockWriter.containerValue(event, parser);

        if (outcome == LlmAnthropicBlockWriter.DESCEND)
        {
            server.decoder = decodeControlResidueNone;
            outcome = LlmAnthropicBlockWriter.MORE;
        }
        else if (outcome == LlmAnthropicBlockWriter.MORE)
        {
            server.decoder = decodeBlockCopy;
        }

        return onBlockValue(server, outcome);
    }

    private int onControlResidueNone(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onControlKey(server, event, parser, LlmAnthropicBlockWriter.RESIDUE_NONE);
    }

    private int onControlResidueOpen(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onControlKey(server, event, parser, LlmAnthropicBlockWriter.RESIDUE_OPEN);
    }

    private int onControlResidueLifted(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onControlKey(server, event, parser, LlmAnthropicBlockWriter.RESIDUE_LIFTED);
    }

    private int onControlKey(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser,
        int residue)
    {
        int outcome = LlmAnthropicBlockWriter.MORE;

        if (event == JsonEvent.KEY_NAME)
        {
            final int action = server.blockWriter.containerAction(parser.getStringView(), residue);

            outcome = switch (action)
            {
            case LlmAnthropicBlockWriter.ACTION_COPY ->
                server.onBlockAction(action, decodeControlResidueOpen);
            case LlmAnthropicBlockWriter.ACTION_TYPE ->
            {
                server.valueThen = residueDecoder(residue);
                server.decoder = decodeControlType;
                yield LlmAnthropicBlockWriter.MORE;
            }
            default ->
                server.onBlockAction(action, decodeControlResidueLifted);
            };
        }
        else if (event == JsonEvent.END_OBJECT)
        {
            server.blockWriter.closeContainer(residue);
            server.decoder = decodeControlMember;
        }
        else
        {
            outcome = LlmAnthropicBlockWriter.REJECT;
        }

        return outcome;
    }

    private int onControlType(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        final int outcome = server.blockWriter.typeValue(event, parser, residueOf(server.valueThen));

        server.valueThen = residueDecoder(server.blockWriter.typeResidue());

        if (outcome == LlmAnthropicBlockWriter.MORE)
        {
            server.decoder = decodeBlockCopy;
        }

        return onBlockValue(server, outcome);
    }

    private int onControlFormat(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = server.blockWriter.formatValue(event);

        if (outcome == LlmAnthropicBlockWriter.DESCEND)
        {
            server.decoder = decodeControlFormatKey;
            outcome = LlmAnthropicBlockWriter.MORE;
        }

        return outcome;
    }

    private int onControlFormatKey(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = LlmAnthropicBlockWriter.MORE;

        if (event == JsonEvent.KEY_NAME)
        {
            outcome = server.onBlockAction(server.blockWriter.formatAction(parser.getStringView()), decodeControlFormatKey);
        }
        else if (event == JsonEvent.END_OBJECT)
        {
            server.blockWriter.closeFormat();
            server.decoder = decodeControlResidueLifted;
        }
        else
        {
            outcome = LlmAnthropicBlockWriter.REJECT;
        }

        return outcome;
    }

    private int onControlFormatType(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.formatType(event, parser));
    }

    private LlmAnthropicServerDecoder residueDecoder(
        int residue)
    {
        return switch (residue)
        {
        case LlmAnthropicBlockWriter.RESIDUE_OPEN -> decodeControlResidueOpen;
        case LlmAnthropicBlockWriter.RESIDUE_LIFTED -> decodeControlResidueLifted;
        default -> decodeControlResidueNone;
        };
    }

    private int residueOf(
        LlmAnthropicServerDecoder decoder)
    {
        return decoder == decodeControlResidueOpen
            ? LlmAnthropicBlockWriter.RESIDUE_OPEN
            : decoder == decodeControlResidueLifted
                ? LlmAnthropicBlockWriter.RESIDUE_LIFTED
                : LlmAnthropicBlockWriter.RESIDUE_NONE;
    }

    private int onBlockSource(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = LlmAnthropicBlockWriter.MORE;

        if (event == JsonEvent.START_OBJECT)
        {
            server.decoder = decodeBlockSourceKey;
        }
        else
        {
            outcome = LlmAnthropicBlockWriter.REJECT;
        }

        return outcome;
    }

    private int onBlockSourceKey(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = LlmAnthropicBlockWriter.MORE;

        if (event == JsonEvent.KEY_NAME)
        {
            outcome = server.onBlockAction(server.blockWriter.sourceAction(parser.getStringView()), decodeBlockSourceKey);
        }
        else if (event == JsonEvent.END_OBJECT)
        {
            server.blockWriter.closeSource();
            server.decoder = decodeBlockKey;
        }
        else
        {
            outcome = LlmAnthropicBlockWriter.REJECT;
        }

        return outcome;
    }

    private int onBlockElement(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = LlmAnthropicBlockWriter.MORE;

        if (event == JsonEvent.START_OBJECT)
        {
            server.blockWriter.beginElement();
            server.decoder = decodeBlockKey;
        }
        else if (event == JsonEvent.END_ARRAY)
        {
            outcome = server.endBlock() ? outcome : LlmAnthropicBlockWriter.REJECT;
        }
        else
        {
            outcome = LlmAnthropicBlockWriter.REJECT;
        }

        return outcome;
    }

    private int onBlockData(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.dataValue(event, parser));
    }

    private int onBlockCitations(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.citationsValue(event, parser));
    }

    private int onBlockKey(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = LlmAnthropicBlockWriter.MORE;

        if (event == JsonEvent.KEY_NAME)
        {
            outcome = server.onBlockAction(server.blockWriter.action(parser.getStringView()), decodeBlockKey);
        }
        else if (event == JsonEvent.END_OBJECT && server.blockWriter.elements())
        {
            server.blockWriter.endElement();
            server.decoder = decodeBlockElement;
        }
        else if (event == JsonEvent.END_OBJECT)
        {
            outcome = server.endBlock() ? outcome : LlmAnthropicBlockWriter.REJECT;
        }
        else
        {
            outcome = LlmAnthropicBlockWriter.REJECT;
        }

        return outcome;
    }

    private int onBlockDrop(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.drop(event, parser));
    }

    private int onBlockCopy(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.copyValue(event, parser));
    }

    private int onBlockString(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.stringValue(event, parser));
    }

    private int onBlockObject(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.objectValue(event, parser));
    }

    private int onBlockOptional(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.optionalValue(event, parser));
    }

    private int onBlockHold(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.holdValue(event, parser));
    }

    private int onBlockBreakpoint(
        LlmAnthropicServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.breakpointValue(event, parser));
    }

    private int onBlockValue(
        LlmAnthropicServer server,
        int outcome)
    {
        int result = outcome;

        if (outcome == LlmAnthropicBlockWriter.VALUE)
        {
            server.decoder = server.valueThen;
            result = LlmAnthropicBlockWriter.MORE;
        }
        else if (outcome == LlmAnthropicBlockWriter.SKIP)
        {
            server.decoder = decodeBlockDrop;
            result = LlmAnthropicBlockWriter.MORE;
        }

        return result;
    }

    @FunctionalInterface
    private interface LlmAnthropicReplyDecoder
    {
        int decode(
            LlmAnthropicStream stream,
            DirectBufferEx buffer,
            int offset,
            int limit);
    }

    private int decodeReplyNext(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return offset;
    }

    private int decodeReplyResume(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return offset;
    }

    private int decodeReplyEnd(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.roomEnd())
        {
            stream.doEncodeEnd();
            stream.dataDecoder = decodeReplyTextDone;
            stream.decoder = decodeReplyNext;
        }
        else if (stream.decoder == decodeReplyEnd)
        {
            stream.pauseEnd(buffer, offset);
        }

        return offset;
    }

    private int decodeReplyFinishDelta(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.finishDelta();

        return offset;
    }

    private int decodeReplyFinishStop(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.finishStop();

        return offset;
    }

    private int decodeReplyFinishBody(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.finishBody();

        return offset;
    }

    private int decodeReplyFinishEnd(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return offset;
    }

    private int decodeReplyTextRoot(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final JsonParserEx parser = stream.wrapReply(buffer, offset);

        try
        {
            while (stream.decoder == decodeReplyTextRoot && parser.hasNextEvent())
            {
                stream.decoder = parser.nextEvent() == JsonEvent.START_DOCUMENT ? decodeReplyTextObject : decodeReplyTextDone;
            }
        }
        catch (JsonException ex)
        {
            stream.rejectReply();
        }

        return stream.positionReply(buffer, offset, decodeReplyTextRoot);
    }

    private int decodeReplyTextObject(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final JsonParserEx parser = stream.wrapReply(buffer, offset);

        try
        {
            while (stream.decoder == decodeReplyTextObject && parser.hasNextEvent())
            {
                stream.decoder = parser.nextEvent() == JsonEvent.START_OBJECT ? decodeReplyTextKey : decodeReplyTextDone;
            }
        }
        catch (JsonException ex)
        {
            stream.rejectReply();
        }

        return stream.positionReply(buffer, offset, decodeReplyTextObject);
    }

    private int decodeReplyTextKey(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final JsonParserEx parser = stream.wrapReply(buffer, offset);

        try
        {
            while (stream.decoder == decodeReplyTextKey && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.KEY_NAME)
                {
                    stream.replySkipDepth = 0;
                    stream.decoder = stream.memberState(parser.getStringView());
                }
                else
                {
                    stream.decoder = decodeReplyTextDone;
                }
            }
        }
        catch (JsonException ex)
        {
            stream.rejectReply();
        }

        return stream.positionReply(buffer, offset, decodeReplyTextKey);
    }

    private int decodeReplyTextValue(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyTextPiece(stream, buffer, offset, decodeReplyTextValue, true, false);
    }

    private int decodeReplyTextHeld(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyTextPiece(stream, buffer, offset, decodeReplyTextHeld, false, true);
    }

    private int decodeReplyTextContinue(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyTextPiece(stream, buffer, offset, decodeReplyTextContinue, false, false);
    }

    private int decodeReplyTextPiece(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        LlmAnthropicReplyDecoder state,
        boolean first,
        boolean held)
    {
        final JsonParserEx parser = held ? stream.replyParser() : stream.wrapReply(buffer, offset);

        boolean pending = held;
        boolean paused = false;
        boolean more = true;

        try
        {
            while (more && !paused && stream.decoder == state)
            {
                if (first)
                {
                    parser.mark();
                }

                final JsonEvent event;

                if (pending)
                {
                    pending = false;
                    event = JsonEvent.VALUE_STRING;
                }
                else
                {
                    more = parser.hasNextEvent();
                    event = more ? parser.nextEvent() : null;
                }

                if (more)
                {
                    if (event == JsonEvent.VALUE_STRING)
                    {
                        final boolean deferred = parser.deferredBytes();
                        final JsonStringView chars = parser.getStringView();
                        final int length = chars.length();
                        final boolean ready = stream.readyValue();
                        final int taken = ready ? stream.onEncodeText(chars, length, !deferred) : 0;

                        if (!ready || taken < length)
                        {
                            if (taken > 0)
                            {
                                parser.consumed(taken);
                                stream.decoder = decodeReplyTextHeld;
                            }
                            else if (first)
                            {
                                parser.rewind();
                            }
                            else
                            {
                                stream.decoder = decodeReplyTextHeld;
                            }

                            paused = true;
                        }
                        else if (deferred)
                        {
                            parser.consumed(length);
                            stream.decoder = decodeReplyTextContinue;
                        }
                        else
                        {
                            stream.decoder = stream.afterValue();
                        }
                    }
                    else if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
                    {
                        stream.replySkipDepth = 1;
                        stream.decoder = decodeReplyTextSkip;
                    }
                    else
                    {
                        stream.decoder = decodeReplyTextKey;
                    }
                }
            }
        }
        catch (JsonException ex)
        {
            stream.rejectReply();
        }

        return paused ? stream.pauseReply(buffer, offset) : stream.positionReply(buffer, offset, state);
    }

    private int decodeReplyArgs(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyArgsPass(stream, buffer, offset, decodeReplyArgs, false);
    }

    private int decodeReplyArgsHeld(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyArgsPass(stream, buffer, offset, decodeReplyArgsHeld, true);
    }

    private int decodeReplyArgsPass(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        LlmAnthropicReplyDecoder state,
        boolean held)
    {
        final JsonParserEx parser = held ? stream.replyParser() : stream.wrapReply(buffer, offset);

        boolean resumed = held;
        boolean paused = false;
        boolean idle = false;

        try
        {
            while (!paused && !idle && (stream.decoder == decodeReplyArgs || stream.decoder == decodeReplyArgsHeld))
            {
                final int outcome = stream.pullArgs(parser, resumed);

                resumed = false;

                switch (outcome)
                {
                case ARGS_IDLE:
                    idle = true;
                    break;
                case ARGS_WAIT:
                    paused = true;
                    break;
                case ARGS_DONE:
                    stream.decoder = decodeReplyTextKey;
                    break;
                case ARGS_HELD:
                    stream.decoder = decodeReplyArgsHeld;
                    resumed = true;
                    paused = !stream.writable();
                    break;
                default:
                    stream.decoder = decodeReplyArgs;
                    paused = !stream.writable();
                    break;
                }
            }
        }
        catch (JsonException ex)
        {
            stream.rejectReply();
        }

        return paused ? stream.pauseReply(buffer, offset) : stream.positionReply(buffer, offset, state);
    }

    private int decodeReplyControlValue(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyControlPass(stream, buffer, offset, decodeReplyControlValue, false);
    }

    private int decodeReplyControlHeld(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyControlPass(stream, buffer, offset, decodeReplyControlHeld, true);
    }

    private int decodeReplyControlPass(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        LlmAnthropicReplyDecoder state,
        boolean held)
    {
        final JsonParserEx parser = held ? stream.replyParser() : stream.wrapReply(buffer, offset);

        boolean resumed = held;
        boolean paused = false;
        boolean idle = false;

        try
        {
            while (!paused && !idle &&
                (stream.decoder == decodeReplyControlValue || stream.decoder == decodeReplyControlHeld))
            {
                final int outcome = stream.pullControl(parser, resumed);

                resumed = false;

                switch (outcome)
                {
                case ARGS_IDLE:
                    idle = true;
                    break;
                case ARGS_WAIT:
                    paused = stream.pausedControl() || stream.generatorTight();
                    break;
                case ARGS_DONE:
                    stream.onEncodeControlEnd();
                    stream.decoder = decodeReplyTextKey;
                    break;
                case ARGS_HELD:
                    stream.decoder = decodeReplyControlHeld;
                    resumed = true;
                    paused = stream.pausedControl();
                    break;
                default:
                    stream.decoder = decodeReplyControlValue;
                    paused = stream.pausedControl();
                    break;
                }
            }
        }
        catch (JsonException ex)
        {
            stream.rejectReply();
        }

        return paused ? stream.pauseReply(buffer, offset) : stream.positionReply(buffer, offset, state);
    }

    private int decodeReplyControlJudge(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final JsonParserEx parser = stream.wrapReply(buffer, offset);

        try
        {
            if (parser.hasNextEvent())
            {
                stream.judgeMember(parser);
            }
        }
        catch (JsonException ex)
        {
            stream.rejectReply();
        }

        return stream.positionReply(buffer, offset, decodeReplyControlJudge);
    }

    private int decodeReplyTextSkip(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final JsonParserEx parser = stream.wrapReply(buffer, offset);

        try
        {
            while (stream.decoder == decodeReplyTextSkip && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING && parser.deferredBytes())
                {
                    parser.consumed(parser.getStringView().length());
                }
                else if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
                {
                    stream.replySkipDepth++;
                }
                else if (event == JsonEvent.END_OBJECT || event == JsonEvent.END_ARRAY)
                {
                    stream.replySkipDepth--;
                }

                if (stream.replySkipDepth == 0 && event != JsonEvent.KEY_NAME)
                {
                    stream.decoder = decodeReplyTextKey;
                }
            }
        }
        catch (JsonException ex)
        {
            stream.rejectReply();
        }

        return stream.positionReply(buffer, offset, decodeReplyTextSkip);
    }

    private int decodeReplyTextDone(
        LlmAnthropicStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.decoder = decodeReplyNext;
        stream.replyTailLength = 0;
        stream.dataDecoder = decodeReplyTextDone;

        return offset;
    }

    @FunctionalInterface
    private interface LlmAnthropicServerDecoder
    {
        int decode(
            LlmAnthropicServer server,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int progress,
            int limit);
    }

    private int decodeLookahead(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        parser.mark();

        int depth = 0;
        boolean modelValue = false;

        while (server.decoder == decodeLookahead && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (depth == 0 && event != JsonEvent.START_DOCUMENT && event != JsonEvent.START_OBJECT ||
                modelValue && event != JsonEvent.VALUE_STRING)
            {
                server.onDecodeParseError(traceId);
            }
            else
            {
                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    if (depth == 0)
                    {
                        server.onDecodeInvalidRequest(traceId, MESSAGE_MODEL_MISSING);
                    }
                    break;
                case KEY_NAME:
                    modelValue = depth == 1 && matches(parser.getStringView(), "model");
                    break;
                case VALUE_STRING:
                    if (parser.deferredBytes())
                    {
                        parser.consumed(parser.getStringView().length());
                    }
                    else if (modelValue)
                    {
                        server.onDecodeModel(traceId, authorization, parser.getString());
                    }
                    break;
                default:
                    break;
                }
            }
        }

        if (server.decoder != decodeIgnore)
        {
            parser.rewind();

            if (server.decoder == decodeLookahead && limit - offset >= decodeMax)
            {
                server.onDecodeInvalidRequest(traceId, MESSAGE_MODEL_ORDER);
            }
        }

        return progress;
    }

    private int decodeStart(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.START_DOCUMENT)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            server.decoder = decodeRootStart;
        }

        return server.position();
    }

    private int decodeRootStart(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.START_OBJECT)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            server.decoder = decodeRoot;
        }

        return server.position();
    }

    private int decodeRoot(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                server.onDecodeRootKey(parser.getStringView());
                break;
            case END_OBJECT:
                server.onDecodeRootEnd(traceId);
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeRootControl(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                final CharSequence key = parser.getStringView();
                if (isRootControl(key))
                {
                    server.controlMore(key, decodeRootControl);
                }
                else
                {
                    server.closeControl(traceId, authorization);
                    server.decoder = decodeRoot;
                    server.onDecodeRootKey(key);
                }
                break;
            case END_OBJECT:
                server.closeControl(traceId, authorization);
                server.onDecodeRootEnd(traceId);
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private boolean isConverted(
        CharSequence key)
    {
        return matches(key, MEMBER_STREAM) ||
            matches(key, MEMBER_TOOL_CHOICE) ||
            matches(key, MEMBER_OUTPUT_CONFIG) ||
            matches(key, MEMBER_THINKING) ||
            matches(key, MEMBER_METADATA) ||
            matches(key, MEMBER_SERVICE_TIER);
    }

    private boolean isRootControl(
        CharSequence key)
    {
        return !matches(key, "messages") &&
            !matches(key, "system") &&
            !matches(key, "tools") &&
            !matches(key, "max_tokens") &&
            !matches(key, "model") &&
            !matches(key, MEMBER_STREAM);
    }

    private int decodeSystemStart(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;
        final int eventFrom = server.position();

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_STRING:
                server.context = CONTEXT_SYSTEM;
                server.text(textBlockType(server), textToolCallId(server), textIsError(server), textMessage(server), decodeRoot);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
                break decode;
            case START_ARRAY:
                server.context = CONTEXT_SYSTEM;
                server.partsThen = decodeRoot;
                server.decoder = decodeParts;
                break;
            case VALUE_NULL:
                server.decoder = decodeRoot;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeMessagesStart(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.START_ARRAY)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            server.message = -1;
            server.decoder = decodeMessages;
        }

        return server.position();
    }

    private int decodeMessages(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                server.message++;
                server.role = null;
                server.toolUseId = null;
                server.toolIsError = null;
                server.context = CONTEXT_MESSAGE;
                server.decoder = decodeMessage;
                break;
            case END_ARRAY:
                server.decoder = decodeRoot;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeMessage(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                final CharSequence key = parser.getStringView();
                if (matches(key, "role"))
                {
                    server.decoder = decodeRole;
                }
                else if (matches(key, "content"))
                {
                    if (server.role == null)
                    {
                        server.onDecodeInvalidRequest(traceId, MESSAGE_ROLE_ORDER);
                        break decode;
                    }
                    server.decoder = decodeContent;
                }
                else
                {
                    server.control(key, server.message, decodeMessage);
                }
                break;
            case END_OBJECT:
                if (server.role == null)
                {
                    server.onDecodeInvalidRequest(traceId, String.format(MESSAGE_ROLE_MISSING, server.message));
                    break decode;
                }
                server.decoder = decodeMessages;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeRole(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.VALUE_STRING)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            if (!parser.deferredBytes())
            {
                final String role = parser.getString();

                if (ROLE_USER.equals(role) || ROLE_ASSISTANT.equals(role))
                {
                    server.role = role;
                    server.decoder = decodeMessage;
                }
                else
                {
                    server.skipDepth = 1;
                    server.skipThen = decodeMessages;
                    server.decoder = decodeSkip;
                }
            }
        }

        return server.position();
    }

    private int decodeContent(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;
        final int eventFrom = server.position();

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_STRING:
                server.text(textBlockType(server), textToolCallId(server), textIsError(server), textMessage(server),
                    decodeMessage);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
                break decode;
            case START_ARRAY:
                server.partsThen = decodeMessage;
                server.decoder = decodeParts;
                break;
            case VALUE_NULL:
                server.decoder = decodeMessage;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeToolsStart(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_ARRAY:
                server.decoder = decodeTools;
                break;
            case VALUE_NULL:
                server.decoder = decodeRoot;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeTools(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        parser.mark();

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                server.probe.begin(LlmSectionType.TOOL_DEFINITION);

                if (server.probed(traceId, limit))
                {
                    server.decoder = decodeToolStart;
                }
                break;
            case END_ARRAY:
                server.decoder = decodeRoot;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeToolStart(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.START_OBJECT)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            if (server.probe.type() == LlmSectionType.TOOL_DEFINITION)
            {
                server.beginToolDefinition();
            }
            else
            {
                server.capture(server.probe.type(), -1, null, decodeTools);
                server.decoder = decodeTool;
            }
        }

        return server.position();
    }

    private int decodeParts(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        parser.mark();

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                lookaheadBlock(server, traceId, offset, limit);
                break;
            case END_ARRAY:
                server.decoder = server.partsThen;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private void lookaheadBlock(
        LlmAnthropicServer server,
        long traceId,
        int offset,
        int limit)
    {
        final JsonParserEx parser = server.parser;
        final int from = server.position() - 1;

        lookahead:
        {
            if (!parser.hasNextEvent())
            {
                break lookahead;
            }

            if (parser.nextEvent() != JsonEvent.KEY_NAME)
            {
                server.onDecodeInvalidRequest(traceId, MESSAGE_PART_TYPE_ORDER);
                break lookahead;
            }

            if (!matches(parser.getStringView(), "type"))
            {
                server.onDecodeInvalidRequest(traceId, MESSAGE_PART_TYPE_ORDER);
                break lookahead;
            }

            if (!parser.hasNextEvent())
            {
                break lookahead;
            }

            if (parser.nextEvent() != JsonEvent.VALUE_STRING)
            {
                server.onDecodeParseError(traceId);
                break lookahead;
            }

            if (parser.deferredBytes())
            {
                break lookahead;
            }

            final String type = parser.getString();

            server.flushHeld();
            server.partBreakpoint.clear();

            if (TYPE_TEXT.equals(type))
            {
                server.decoder = decodePart;
            }
            else if (TYPE_TOOL_RESULT.equals(type) && server.context == CONTEXT_MESSAGE)
            {
                server.resultBreakpoint.clear();
                server.toolUseId = null;
                server.toolIsError = null;
                server.toolContentSeen = false;
                server.decoder = decodeToolResult;
            }
            else
            {
                final LlmSectionType blockType = partBlockType(server, type);

                if (blockType != null)
                {
                    final String extension = blockType == LlmSectionType.SERVER_TOOL_RESULT ? type : null;

                    if (LlmAnthropicAttributeProbe.carries(blockType))
                    {
                        server.probeExtension = extension;
                        server.probe.begin(blockType);

                        if (server.probed(traceId, limit))
                        {
                            server.decoder = decodeBlockStart;
                        }
                    }
                    else if (blockType == LlmSectionType.ASSISTANT_REASONING)
                    {
                        server.beginReasoning(textMessage(server));
                    }
                    else
                    {
                        server.captureAt(from, blockType, textMessage(server), extension, decodeParts);
                        server.decoder = decodeServerToolBlock;
                    }
                }
                else
                {
                    server.skipDepth = 1;
                    server.skipThen = decodeParts;
                    server.decoder = decodeSkip;
                }
            }
        }

        if (server.decoder == decodeParts || server.decoder == decodeBlockStart)
        {
            parser.rewind();

            if (server.decoder == decodeParts && limit - offset >= decodeMax)
            {
                server.onDecodeInvalidRequest(traceId, MESSAGE_PART_TYPE_ORDER);
            }
        }
    }

    private int decodeBlockStart(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.START_OBJECT)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            final LlmSectionType blockType = server.probe.type();

            if (blockType == LlmSectionType.TOOL_CALL || blockType == LlmSectionType.SERVER_TOOL_CALL)
            {
                server.beginToolCall(blockType, textMessage(server), server.probeExtension);
            }
            else if (blockType == LlmSectionType.USER_IMAGE || blockType == LlmSectionType.USER_DOCUMENT)
            {
                server.beginMedia(blockType, textMessage(server));
            }
            else
            {
                server.capture(blockType, textMessage(server), server.probeExtension, decodeParts);
                server.decoder = decodeServerToolBlock;
            }
        }

        return server.position();
    }

    private int decodePart(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                final CharSequence key = parser.getStringView();
                if (matches(key, TYPE_TEXT))
                {
                    server.text(textBlockType(server), textToolCallId(server), textIsError(server), textMessage(server),
                        decodePart);
                    server.decoder = decodeText;
                }
                else if (matches(key, "citations"))
                {
                    server.decoder = decodeCitationsStart;
                }
                else if (matches(key, MEMBER_CACHE_CONTROL))
                {
                    server.beginBreakpoint(server.partBreakpoint, decodePart);
                }
                else
                {
                    server.control(key, textMessage(server), decodePart);
                }
                break;
            case END_OBJECT:
                server.decoder = decodeParts;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeCitationsStart(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_ARRAY:
                server.beginCitations(textMessage(server));
                break;
            case VALUE_NULL:
                server.decoder = decodePart;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeToolResult(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                final CharSequence key = parser.getStringView();
                if (matches(key, "tool_use_id"))
                {
                    server.decoder = decodeToolUseId;
                }
                else if (matches(key, "is_error"))
                {
                    server.decoder = decodeToolIsError;
                }
                else if (matches(key, "content"))
                {
                    if (server.toolUseId == null)
                    {
                        server.onDecodeInvalidRequest(traceId, MESSAGE_TOOL_USE_ID_ORDER);
                        break decode;
                    }
                    server.toolContentSeen = true;
                    server.decoder = decodeToolResultContent;
                }
                else if (!server.toolContentSeen && server.toolUseId == null)
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_TOOL_USE_ID_ORDER);
                    break decode;
                }
                else if (matches(key, MEMBER_CACHE_CONTROL))
                {
                    server.beginBreakpoint(server.resultBreakpoint, decodeToolResult);
                }
                else
                {
                    server.control(key, textMessage(server), decodeToolResult);
                }
                break;
            case END_OBJECT:
                server.attachResultBreakpoint();
                server.flushHeld();
                server.context = CONTEXT_MESSAGE;
                server.partsThen = decodeMessage;
                server.decoder = decodeParts;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeToolUseId(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.VALUE_STRING)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            if (!parser.deferredBytes())
            {
                server.toolUseId = parser.getString();
                server.decoder = decodeToolResult;
            }
        }

        return server.position();
    }

    private int decodeToolIsError(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_TRUE:
                server.onDecodeToolIsError(LlmSectionAttributes.TRUE);
                server.decoder = decodeToolResult;
                break;
            case VALUE_FALSE:
                server.onDecodeToolIsError(LlmSectionAttributes.FALSE);
                server.decoder = decodeToolResult;
                break;
            case VALUE_NULL:
                server.decoder = decodeToolResult;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodeToolResultContent(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;
        final int eventFrom = server.position();

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_STRING:
                server.context = CONTEXT_TOOL_RESULT;
                server.text(textBlockType(server), textToolCallId(server), textIsError(server), textMessage(server),
                    decodeToolResult);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
                break decode;
            case START_ARRAY:
                server.context = CONTEXT_TOOL_RESULT;
                server.partsThen = decodeToolResult;
                server.decoder = decodeParts;
                break;
            case VALUE_NULL:
                server.decoder = decodeToolResult;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeServerToolBlock(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        while (server.decoder == decodeServerToolBlock && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

            server.probe.onEvent(event, parser);
            server.captureDepth = server.probe.depth();

            if (server.captureDepth == 0 && !deferred)
            {
                server.endCapture(traceId, authorization);
            }
        }

        return server.position();
    }

    private int decodeTool(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        while (server.decoder == decodeTool && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

            server.probe.onEvent(event, parser);
            server.captureDepth = server.probe.depth();

            if (server.captureDepth == 0 && !deferred)
            {
                server.endCapture(traceId, authorization);
            }
        }

        return server.position();
    }

    private int decodeText(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        int eventFrom = server.position();

        while ((server.decoder == decodeText || server.decoder == decodeTextPiece) && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
            eventFrom = server.position();
        }

        return server.position();
    }

    private int decodeTextValue(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int progress,
        int limit,
        JsonEvent event,
        int eventFrom)
    {
        final JsonParserEx parser = server.parser;
        final boolean opened = server.decoder == decodeTextPiece;

        if (event == JsonEvent.VALUE_STRING)
        {
            if (!opened)
            {
                server.onDecodeBlock(server.textType, server.textMessage, null);
                server.blockAttributes.toolCallId(server.textToolCallId);
                server.blockAttributes.isError(server.textIsError);
            }

            final boolean fin = !parser.deferredBytes();
            final boolean tail = fin && (server.textThen == decodePart || server.textThen == decodeToolResult);
            final JsonStringView chars = parser.getStringView();
            final int length = chars.length();
            final int taken = !fin && length > 1 && Character.isHighSurrogate(chars.charAt(length - 1)) ? length - 1 : length;
            final int piece = encodePiece(server, chars, taken, fin, tail);

            if (piece < 0)
            {
                server.onDecodeParseError(traceId);
            }
            else
            {
                if (tail)
                {
                    server.hold(traceId, authorization, pieceBuffer, piece);
                }
                else
                {
                    server.onDecodeData(traceId, authorization, pieceBuffer, 0, piece, fin);
                }

                if (fin)
                {
                    server.decoder = server.textThen;
                }
                else
                {
                    parser.consumed(taken);
                    server.decoder = decodeTextPiece;
                }
            }
        }
        else if (event == JsonEvent.VALUE_NULL && !opened)
        {
            server.decoder = server.textThen;
        }
        else
        {
            server.onDecodeParseError(traceId);
        }

        return server.decoder == decodeIgnore ? limit : server.position();
    }

    private int decodeControlSeparator(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        server.onDecodeControlSeparator();

        return server.position();
    }

    private int decodeControlValue(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        while (server.decoder == decodeControlValue && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                server.captureDepth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                server.captureDepth--;
                break;
            case VALUE_STRING:
                if (deferred)
                {
                    parser.consumed(parser.getStringView().length());
                }
                break;
            default:
                break;
            }

            if (server.captureDepth == 0 && !deferred)
            {
                server.endCapture(traceId, authorization);
            }
        }

        return server.position();
    }

    private int decodeBreakpointValue(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        while (server.decoder == decodeBreakpointValue && parser.hasNextEvent())
        {
            server.onDecodeBreakpoint(parser.nextEvent());
        }

        return server.position();
    }

    private int decodeMaxOutputTokensValue(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_NUMBER:
                server.maxOutputTokens = parser.getInt();
                server.decoder = decodeRoot;
                break;
            case START_OBJECT:
            case START_ARRAY:
                server.skipThen = decodeRoot;
                server.skipDepth = 1;
                server.decoder = decodeSkip;
                break;
            case VALUE_STRING:
                if (parser.deferredBytes())
                {
                    parser.consumed(parser.getStringView().length());
                }
                else
                {
                    server.decoder = decodeRoot;
                }
                break;
            default:
                server.decoder = decodeRoot;
                break;
            }
        }

        return server.position();
    }

    private int decodeSkip(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        while (server.decoder == decodeSkip && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                server.skipDepth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                server.skipDepth--;
                break;
            default:
                break;
            }

            final boolean fragment = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

            if (fragment)
            {
                parser.consumed(parser.getStringView().length());
            }
            else if (event != JsonEvent.KEY_NAME && server.skipDepth == 0)
            {
                server.decoder = server.skipThen;
            }

        }

        return server.position();
    }

    private static LlmSectionType textBlockType(
        LlmAnthropicServer server)
    {
        final LlmSectionType type;

        if (server.context == CONTEXT_SYSTEM)
        {
            type = LlmSectionType.SYSTEM_INSTRUCTION;
        }
        else if (server.context == CONTEXT_TOOL_RESULT)
        {
            type = LlmSectionType.TOOL_RESULT;
        }
        else if (ROLE_USER.equals(server.role))
        {
            type = LlmSectionType.USER_TEXT;
        }
        else
        {
            type = LlmSectionType.ASSISTANT_TEXT;
        }

        return type;
    }

    private static String textToolCallId(
        LlmAnthropicServer server)
    {
        return server.context == CONTEXT_TOOL_RESULT ? server.toolUseId : null;
    }

    private static String textIsError(
        LlmAnthropicServer server)
    {
        return server.context == CONTEXT_TOOL_RESULT ? server.toolIsError : null;
    }

    private static int textMessage(
        LlmAnthropicServer server)
    {
        return server.context == CONTEXT_SYSTEM ? -1 : server.message;
    }

    private static DirectBufferEx eventType(
        String type)
    {
        final HttpDataExFW dataEx = new HttpDataExFW.Builder()
            .wrap(new UnsafeBufferEx(new byte[EVENT_ATTRIBUTES_CAPACITY]), 0, EVENT_ATTRIBUTES_CAPACITY)
            .typeId(0)
            .headersItem(h -> h.name("type").value(type))
            .build();
        final Array32FW<HttpHeaderFW> headers = dataEx.headers();
        final byte[] bytes = new byte[headers.sizeof()];

        headers.buffer().getBytes(headers.offset(), bytes);

        return new UnsafeBufferEx(bytes);
    }

    private static LlmSectionType partBlockType(
        LlmAnthropicServer server,
        String type)
    {
        LlmSectionType blockType = null;

        if (server.context == CONTEXT_MESSAGE)
        {
            blockType = switch (type)
            {
            case "thinking", "redacted_thinking" -> LlmSectionType.ASSISTANT_REASONING;
            case "tool_use" -> LlmSectionType.TOOL_CALL;
            case "image" -> LlmSectionType.USER_IMAGE;
            case "document" -> LlmSectionType.USER_DOCUMENT;
            case "server_tool_use" -> LlmSectionType.SERVER_TOOL_CALL;
            default -> type.endsWith(SUFFIX_TOOL_RESULT) ? LlmSectionType.SERVER_TOOL_RESULT : null;
            };
        }

        return blockType;
    }

    private int encodePiece(
        LlmAnthropicServer server,
        JsonStringView chars,
        int taken,
        boolean fin,
        boolean tail)
    {
        final boolean init = Flags.hasInit(server.blockFlags);
        final boolean marked = init && server.partBreakpoint.present();
        final int marker = marked ? server.partBreakpoint.length() + 1 : 0;
        final int prefix = init ? TEXT_OPEN.capacity() + marker + TEXT_KEY.capacity() : 0;
        final int suffix = !fin ? 0 : tail ? 1 : TEXT_SUFFIX.capacity();
        final int width = body.length(chars, 0, taken);

        int length = -1;

        if (prefix + width + suffix <= pieceBuffer.capacity())
        {
            int position = 0;

            if (init)
            {
                pieceBuffer.putBytes(position, TEXT_OPEN, 0, TEXT_OPEN.capacity());
                position += TEXT_OPEN.capacity();

                if (marked)
                {
                    pieceBuffer.putBytes(position, server.partBreakpoint.buffer(), 0, server.partBreakpoint.length());
                    position += server.partBreakpoint.length();
                    pieceBuffer.putByte(position++, (byte) ',');
                    server.partBreakpoint.clear();
                }

                pieceBuffer.putBytes(position, TEXT_KEY, 0, TEXT_KEY.capacity());
                position += TEXT_KEY.capacity();
            }

            position += body.transfer(chars, 0, taken,
                pieceTarget.wrap(pieceBuffer.segment(), pieceBuffer.wrapAdjustment() + position));

            if (fin)
            {
                pieceBuffer.putBytes(position, TEXT_SUFFIX, 0, suffix);
                position += suffix;
            }

            length = position;
        }

        return length;
    }

    private static boolean isWhitespace(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private static boolean matches(
        CharSequence chars,
        String value)
    {
        boolean matches = chars.length() == value.length();

        for (int i = 0; matches && i < value.length(); i++)
        {
            matches = chars.charAt(i) == value.charAt(i);
        }

        return matches;
    }

    private int decodeEnd(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        int index = progress;

        while (index < limit && isWhitespace(buffer.getByte(index)))
        {
            index++;
        }

        if (index < limit)
        {
            server.onDecodeParseError(traceId);
        }

        return limit;
    }

    private int decodeIgnore(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return limit;
    }

    private final class LlmAnthropicServer
    {
        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final LlmBindingConfig binding;
        private long exitId;
        private final long initialAuthorization;
        private final String contentType;
        private final String beta;
        private final Runnable deauthorize;
        private final JsonParserEx parser;
        private LlmAnthropicStream stream;
        private LlmAnthropicServerDecoder decoder;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBudgetId;
        private BudgetDebit replyDebit;

        private int state;
        private boolean deauthorized;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;


        private DirectBufferEx decodedInput;
        private int decodedWindowLimit;

        private DirectBufferEx replyBody;
        private int replyBodySent;

        private long pendingReplyEndTraceId;
        private long pendingReplyEndAuthorization;

        private int message;
        private String role;
        private String toolUseId;
        private String toolIsError;
        private int context;
        private LlmAnthropicServerDecoder partsThen;

        private LlmAnthropicServerDecoder skipThen;
        private int skipDepth;

        private boolean streaming;
        private int maxOutputTokens = NO_MAX_OUTPUT_TOKENS;

        private LlmAnthropicServerDecoder textThen;
        private LlmSectionType textType;
        private String textToolCallId;
        private String textIsError;
        private int textMessage;

        private String probeExtension;

        private LlmAnthropicServerDecoder captureThen;
        private int captureDepth;
        private int captureFrom;

        private boolean toolContentSeen;

        private final LlmBreakpointStash partBreakpoint;
        private final LlmBreakpointStash resultBreakpoint;
        private StringBuilder breakpointTtl;
        private LlmBreakpointStash breakpointStash;
        private LlmAnthropicServerDecoder breakpointThen;
        private int breakpointDepth;
        private int breakpointKey;
        private boolean breakpointEphemeral;

        private boolean heldPending;
        private MutableDirectBufferEx heldBuffer;
        private int heldLength;
        private long heldTraceId;
        private long heldAuthorization;
        private String lateIsError;

        private final LlmAnthropicAttributeProbe probe;
        private final LlmSectionAttributes blockAttributes;
        private final LlmSectionAttributes sentAttributes;
        private final LlmSectionAttributes finAttributes;

        private long decodeTraceId;
        private long decodeAuthorization;
        private LlmSectionType blockSection;
        private int blockMessage;
        private String blockExtension;
        private int blockFlags;

        private LlmAnthropicBlockWriter blockWriter;
        private LlmAnthropicBlockWriter writing;
        private LlmAnthropicServerDecoder blockThen;
        private LlmAnthropicServerDecoder valueThen;
        private int blockSlot = NO_SLOT;
        private final LlmOutputSink blockSink = (buffer, offset, length, last) ->
            onDecodeSectionData(decodeTraceId, decodeAuthorization, buffer, offset, length, last);
        private final StringBuilder controlKey = new StringBuilder();

        private LlmAnthropicServer(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            LlmBindingConfig binding,
            long authorization,
            String contentType,
            String beta,
            Runnable deauthorize)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.binding = binding;
            this.initialAuthorization = authorization;
            this.contentType = contentType;
            this.beta = beta;
            this.deauthorize = deauthorize;
            this.parser = JsonEx.createParser(PARSER_CONFIG);
            this.probe = new LlmAnthropicAttributeProbe();
            this.partBreakpoint = new LlmBreakpointStash();
            this.resultBreakpoint = new LlmBreakpointStash();
            this.blockAttributes = new LlmSectionAttributes();
            this.sentAttributes = new LlmSectionAttributes();
            this.finAttributes = new LlmSectionAttributes();
            this.decoder = decodeLookahead;
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            initialMax = decodeMax;
            state = StreamState.openingInitial(state);

            doNetWindow(traceId);
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            initialSeq = data.sequence() + reserved;

            if (decodeSlot == NO_SLOT)
            {
                decodeNet(traceId, initialAuthorization, budgetId, reserved, payload.buffer(), payload.offset(), payload.limit());
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                decodeSlotOffset += payload.sizeof();

                decodeNet(traceId, initialAuthorization, budgetId);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            initialSeq = end.sequence();
            state = StreamState.closingInitial(state);

            if (decodeSlot != NO_SLOT)
            {
                decodeNet(traceId, initialAuthorization, 0L);
            }
            else
            {
                flushNetEnd(traceId);
            }
        }

        private void decodeNet(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx buffer = decodePool.buffer(decodeSlot);
                final int limit = decodeSlotOffset;

                decodeNet(traceId, authorization, budgetId, limit, buffer, 0, limit);
            }
        }

        private void decodeNet(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            int progress = offset;

            if (offset < limit)
            {
                captureFrom = offset;

                resumeWriter();

                int previous;
                LlmAnthropicServerDecoder previousDecoder;
                do
                {
                    previous = progress;
                    previousDecoder = decoder;
                    progress = decodeInput(traceId, authorization, budgetId, reserved, buffer, progress, limit);
                }
                while (progress != previous && progress < limit || decoder != previousDecoder);

                suspendWriter();
            }

            if (progress < limit)
            {
                final int length = limit - progress;

                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanup(traceId);
                }
                else
                {
                    final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                    slot.putBytes(0, buffer, progress, length);
                    decodeSlotOffset = length;
                }
            }
            else
            {
                cleanupDecodeSlot();
            }

            flushNetWindow(traceId);
            flushNetEnd(traceId);
        }

        private int decodeInput(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            int progress = offset;

            decodedInput = buffer;

            if (decoder == decodeEnd || decoder == decodeIgnore)
            {
                decodedWindowLimit = limit;
                progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, offset, progress, limit);
            }
            else
            {
                int window = windowFor(offset, limit);

                if (heldPending && windowFor(offset, limit, capacity()) < Math.min(MIN_WINDOW, limit - offset) &&
                    stream.initialAvailable() >= heldTail())
                {
                    flushHeld();
                    window = windowFor(offset, limit);
                }

                if (window >= Math.min(MIN_WINDOW, limit - offset))
                {
                    final int windowLimit = offset + window;

                    final boolean windowLast = StreamState.initialClosing(state) && windowLimit == limit;

                    decodedWindowLimit = windowLimit;
                    decodeTraceId = traceId;
                    decodeAuthorization = authorization;
                    parser.wrap(buffer, offset, windowLimit, windowLast);

                    final LlmAnthropicServerDecoder previous = decoder;

                    try
                    {
                        progress = decoder.decode(this, traceId, authorization, budgetId, reserved,
                            buffer, offset, progress, limit);
                    }
                    catch (JsonException ex)
                    {
                        onDecodeParseError(traceId);
                    }

                    final boolean stalled = decoder == previous;

                    if (stalled)
                    {
                        flushHeldPiece();
                    }

                    if (stalled && capturing())
                    {
                        flushCapture(traceId, authorization, false);
                        progress = position();
                    }

                    if (stalled && windowLast && decoder != decodeEnd && decoder != decodeIgnore)
                    {
                        onDecodeParseError(traceId);
                    }
                }
            }

            return progress;
        }

        private void flushNetWindow(
            long traceId)
        {
            final long initialAckMax = initialSeq - decodeSlotOffset;

            if (initialAckMax > initialAck)
            {
                initialAck = initialAckMax;
                doNetWindow(traceId);
            }
        }

        private void flushNetEnd(
            long traceId)
        {
            if (StreamState.initialClosing(state) && !StreamState.initialClosed(state) &&
                decodeSlot == NO_SLOT && decoder != decodeIgnore)
            {
                state = StreamState.closeInitial(state);
                onDecodeEnd(traceId);
            }
        }

        private int windowFor(
            int offset,
            int limit)
        {
            return windowFor(offset, limit, available());
        }

        private int windowFor(
            int offset,
            int limit,
            int credit)
        {
            final int reserved = heldTail();
            final int framing = TEXT_FRAMING;
            final int reserve = decoder == decodeTextPiece ? TEXT_SUFFIX.capacity() : framing;
            final int textGrowth = 1 + (framing + MIN_TEXT_INPUT - 1) / MIN_TEXT_INPUT;
            final int growth = decoder == decodeText || decoder == decodeTextPiece
                ? textGrowth
                : Math.max(textGrowth, CONTROL_GROWTH);

            return stream != null
                ? (int) Math.min(limit - offset, Math.max(credit - reserved - reserve, 0) / growth)
                : limit - offset;
        }

        private int available()
        {
            final int pending = writing != null ? writing.pending() : 0;

            return stream != null
                ? appendable((int) Math.max(stream.initialAvailable() - pending, 0L))
                : 0;
        }

        private int capacity()
        {
            return stream != null
                ? appendable(stream.initialMax)
                : 0;
        }

        private int appendable(
            int credit)
        {
            final int available = Math.min(credit, pieceBuffer.capacity());
            final int appendRoom = streaming ? Math.min(STREAM_OPTIONS_SECTION.capacity(), available >> 1) : 0;

            return available - appendRoom;
        }

        private boolean capturing()
        {
            return decoder == decodeControlSeparator || decoder == decodeControlValue ||
                decoder == decodeServerToolBlock || decoder == decodeTool;
        }

        private boolean probing()
        {
            return blockSection != null &&
                (blockSection == LlmSectionType.SERVER_TOOL_DEFINITION || LlmAnthropicAttributeProbe.carries(blockSection));
        }

        private int position()
        {
            return decodedWindowLimit - parser.remaining();
        }

        private void skip(
            LlmAnthropicServerDecoder then)
        {
            skipThen = then;
            skipDepth = 0;
        }

        private void text(
            LlmSectionType type,
            String toolCallId,
            String isError,
            int message,
            LlmAnthropicServerDecoder then)
        {
            textType = type;
            textToolCallId = toolCallId;
            textIsError = isError;
            textMessage = message;
            textThen = then;
        }

        private void capture(
            LlmSectionType type,
            int blockMessage,
            String extension,
            LlmAnthropicServerDecoder then)
        {
            captureAt(position() - 1, type, blockMessage, extension, then);
        }

        private void hold(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int length)
        {
            reserveHeld();

            heldBuffer.putBytes(0, buffer, 0, length);
            heldLength = length;
            heldTraceId = traceId;
            heldAuthorization = authorization;
            heldPending = true;
        }

        private void reserveHeld()
        {
            if (heldBuffer == null)
            {
                heldBuffer = new UnsafeBufferEx(new byte[pieceBuffer.capacity() + HELD_TAIL_MAX]);
            }
        }

        private int heldTail()
        {
            return heldPending ? heldLength + 1 + (partBreakpoint.present() ? partBreakpoint.length() + 1 : 0) : 0;
        }

        private void flushHeldPiece()
        {
            if (heldPending && heldLength > 0)
            {
                final int length = heldLength;

                heldLength = 0;
                onDecodeData(heldTraceId, heldAuthorization, heldBuffer, 0, length, false);
            }
        }

        private void flushHeld()
        {
            if (heldPending)
            {
                heldPending = false;

                int length = heldLength;

                if (partBreakpoint.present())
                {
                    heldBuffer.putByte(length++, (byte) ',');
                    heldBuffer.putBytes(length, partBreakpoint.buffer(), 0, partBreakpoint.length());
                    length += partBreakpoint.length();
                    partBreakpoint.clear();
                }

                heldBuffer.putByte(length++, (byte) '}');

                onDecodeData(heldTraceId, heldAuthorization, heldBuffer, 0, length, true);
                lateIsError = null;
                heldLength = 0;
            }
        }

        private void attachResultBreakpoint()
        {
            if (heldPending && resultBreakpoint.present() && !partBreakpoint.present())
            {
                partBreakpoint.copyFrom(resultBreakpoint);
            }

            resultBreakpoint.clear();
        }

        private void beginBreakpoint(
            LlmBreakpointStash stash,
            LlmAnthropicServerDecoder then)
        {
            breakpointStash = stash;
            breakpointThen = then;
            breakpointDepth = 0;
            breakpointKey = BREAKPOINT_KEY_NONE;
            breakpointEphemeral = false;

            if (breakpointTtl == null)
            {
                breakpointTtl = new StringBuilder();
            }

            breakpointTtl.setLength(0);
            decoder = decodeBreakpointValue;
        }

        private void onDecodeBreakpoint(
            JsonEvent event)
        {
            switch (event)
            {
            case START_OBJECT, START_ARRAY:
                breakpointDepth++;
                break;
            case END_OBJECT, END_ARRAY:
                breakpointDepth--;
                break;
            case KEY_NAME:
                if (breakpointDepth == 1)
                {
                    final CharSequence key = parser.getStringView();

                    breakpointKey = matches(key, MEMBER_CACHE_CONTROL_TYPE) ? BREAKPOINT_KEY_TYPE :
                        matches(key, MEMBER_CACHE_CONTROL_TTL) ? BREAKPOINT_KEY_TTL : BREAKPOINT_KEY_NONE;
                }
                break;
            case VALUE_STRING:
                if (!parser.deferredBytes() && breakpointDepth == 1 && breakpointKey == BREAKPOINT_KEY_TYPE)
                {
                    breakpointEphemeral = matches(parser.getStringView(), CACHE_CONTROL_EPHEMERAL);
                }
                else if (!parser.deferredBytes() && breakpointDepth == 1 && breakpointKey == BREAKPOINT_KEY_TTL)
                {
                    breakpointTtl.append(parser.getStringView());
                }
                else if (parser.deferredBytes() && (breakpointDepth != 1 || breakpointKey == BREAKPOINT_KEY_NONE))
                {
                    parser.consumed(parser.getStringView().length());
                }
                break;
            default:
                break;
            }

            if (breakpointDepth == 0)
            {
                if (breakpointEphemeral)
                {
                    breakpointStash.set(breakpointTtl);
                }
                else
                {
                    breakpointStash.clear();
                }

                decoder = breakpointThen;
            }
        }

        private void onDecodeToolIsError(
            String isError)
        {
            toolIsError = isError;

            if (heldPending)
            {
                lateIsError = isError;
                blockAttributes.isError(isError);
            }
        }

        private boolean probed(
            long traceId,
            int limit)
        {
            boolean pending = true;
            boolean accepted = true;

            parser.rewind();
            parser.hasNextEvent();
            parser.nextEvent();

            while (accepted && pending && parser.hasNextEvent())
            {
                switch (probe.onEvent(parser.nextEvent(), parser))
                {
                case PENDING:
                    break;
                case REJECTED:
                    onDecodeInvalidRequest(traceId, MESSAGE_ATTRIBUTE_TOO_LARGE);
                    accepted = false;
                    break;
                default:
                    pending = false;
                    break;
                }
            }

            parser.rewind();

            final boolean ready = accepted && (!pending || decodedWindowLimit == limit);

            if (ready)
            {
                probe.restart();
            }

            return ready;
        }

        private void captureAt(
            int from,
            LlmSectionType type,
            int blockMessage,
            String extension,
            LlmAnthropicServerDecoder then)
        {
            onDecodeBlock(type, blockMessage, extension);

            captureThen = then;
            captureDepth = 1;
            captureFrom = from;
        }

        private void onDecodeRootKey(
            CharSequence key)
        {
            if (matches(key, "messages"))
            {
                decoder = decodeMessagesStart;
            }
            else if (matches(key, "system"))
            {
                decoder = decodeSystemStart;
            }
            else if (matches(key, "tools"))
            {
                decoder = decodeToolsStart;
            }
            else if (matches(key, "max_tokens"))
            {
                decoder = decodeMaxOutputTokensValue;
            }
            else if (matches(key, "model"))
            {
                skip(decodeRoot);
                decoder = decodeSkip;
            }
            else
            {
                control(key, -1, isRootControl(key) ? decodeRootControl : decodeRoot);
            }
        }

        private void onDecodeControlSeparator()
        {
            final int start = LlmValueStart.of(decodedInput, captureFrom, decodedWindowLimit);

            if (start < decodedWindowLimit)
            {
                captureFrom = start;
                decoder = decodeControlValue;
            }
        }

        private void controlMore(
            CharSequence key,
            LlmAnthropicServerDecoder then)
        {
            captureThen = then;

            if (isConverted(key))
            {
                beginControlMember(key, ',');
            }
            else
            {
                onDecodeControlKey(key, ',');
                captureDepth = 0;
                captureFrom = position();
                decoder = decodeControlSeparator;
            }
        }

        private void closeControl(
            long traceId,
            long authorization)
        {
            onDecodeData(traceId, authorization, EMPTY_BUFFER, 0, 0, true);
        }

        private void control(
            CharSequence key,
            int message,
            LlmAnthropicServerDecoder then)
        {
            onDecodeBlock(LlmSectionType.CONTROL, message, null);

            captureThen = then;

            if (isConverted(key))
            {
                beginControlMember(key, '{');
            }
            else
            {
                onDecodeControlKey(key, '{');
                captureDepth = 0;
                captureFrom = position();
                decoder = decodeControlSeparator;
            }
        }

        private void beginControlMember(
            CharSequence key,
            char separator)
        {
            writing = writer();
            writing.beginMember(blockBuffer(), 0, separator);
            valueThen = decodeControlMember;

            if (matches(key, MEMBER_STREAM))
            {
                decoder = decodeControlStream;
            }
            else if (matches(key, MEMBER_TOOL_CHOICE))
            {
                decoder = decodeControlToolChoice;
            }
            else if (matches(key, MEMBER_SERVICE_TIER))
            {
                decoder = decodeControlTier;
            }
            else
            {
                writing.container(matches(key, MEMBER_OUTPUT_CONFIG)
                    ? LlmAnthropicBlockWriter.CONTAINER_OUTPUT_CONFIG
                    : matches(key, MEMBER_THINKING)
                        ? LlmAnthropicBlockWriter.CONTAINER_THINKING
                        : LlmAnthropicBlockWriter.CONTAINER_METADATA);
                decoder = decodeControlContainer;
            }
        }

        private void endControlMember(
            long traceId,
            long authorization)
        {
            writing.endMember();
            writing = null;

            if (captureThen != decodeRootControl)
            {
                closeControl(traceId, authorization);
            }

            decoder = decoder == decodeIgnore ? decoder : captureThen;
        }

        private void endCapture(
            long traceId,
            long authorization)
        {
            flushCapture(traceId, authorization, captureThen != decodeRootControl);

            decoder = decoder == decodeIgnore ? decoder : captureThen;
        }

        private void flushCapture(
            long traceId,
            long authorization,
            boolean fin)
        {
            final int end = position();

            if (end > captureFrom || fin)
            {
                onDecodeData(traceId, authorization, decodedInput, captureFrom, end - captureFrom, fin);
            }

            captureFrom = end;
        }

        private void onDecodeModel(
            long traceId,
            long authorization,
            String model)
        {
            final LlmRouteConfig route = binding.resolve(initialAuthorization, NAME, model);

            if (route != null)
            {
                exitId = route.id;
                decoder = decodeRoot;
                stream = new LlmAnthropicStream(this);
                stream.doAppBegin(traceId, authorization, model);

                decoder = decodeStart;
            }
            else
            {
                onDecodeModelNotFound(traceId, model);
            }
        }

        private void onDecodeBlock(
            LlmSectionType type,
            int message,
            String extension)
        {
            flushHeld();

            blockSection = type;
            blockMessage = message;
            blockExtension = extension;
            blockFlags = Flags.INIT;

            blockAttributes.clear();
        }

        private void onDecodeData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            if (decoder != decodeIgnore)
            {
                final boolean closing = blockSection == LlmSectionType.CONTROL && last;

                onDecodeSectionData(traceId, authorization, buffer, offset, length, last && !closing);

                if (closing)
                {
                    onDecodeSectionData(traceId, authorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), true);
                }
            }
        }

        private void onDecodeControlKey(
            CharSequence key,
            char open)
        {
            controlKey.setLength(0);

            final String shared = LlmSharedMembers.anthropic(key);

            if (shared != null)
            {
                controlKey.append(SHARED_PREFIX).append(shared);
            }
            else
            {
                controlKey.append(NAME).append(':').append(key);
            }

            writer().memberKey(blockBuffer(), 0, open, controlKey);
        }

        private LlmAnthropicBlockWriter writer()
        {
            if (blockWriter == null)
            {
                blockWriter = new LlmAnthropicBlockWriter(blockSink, JsonEx.createGenerator());
            }

            return blockWriter;
        }

        private void beginToolCall(
            LlmSectionType type,
            int blockMessage,
            String extension)
        {
            onDecodeBlock(type, blockMessage, extension);

            writing = writer();
            writing.beginToolCall(blockBuffer(), 0);
            blockThen = decodeParts;
            decoder = decodeBlockKey;
        }

        private void beginToolDefinition()
        {
            onDecodeBlock(LlmSectionType.TOOL_DEFINITION, -1, null);

            writing = writer();
            writing.beginToolDefinition(blockBuffer(), 0);
            blockThen = decodeTools;
            decoder = decodeBlockKey;
        }

        private int onBlockAction(
            int action,
            LlmAnthropicServerDecoder then)
        {
            int outcome = LlmAnthropicBlockWriter.MORE;

            valueThen = then;

            decoder = switch (action)
            {
            case LlmAnthropicBlockWriter.ACTION_COPY -> decodeBlockCopy;
            case LlmAnthropicBlockWriter.ACTION_STRING -> decodeBlockString;
            case LlmAnthropicBlockWriter.ACTION_OBJECT -> decodeBlockObject;
            case LlmAnthropicBlockWriter.ACTION_OPTIONAL -> decodeBlockOptional;
            case LlmAnthropicBlockWriter.ACTION_HOLD -> decodeBlockHold;
            case LlmAnthropicBlockWriter.ACTION_BREAKPOINT -> decodeBlockBreakpoint;
            case LlmAnthropicBlockWriter.ACTION_SOURCE -> decodeBlockSource;
            case LlmAnthropicBlockWriter.ACTION_DATA -> decodeBlockData;
            case LlmAnthropicBlockWriter.ACTION_CITATIONS -> decodeBlockCitations;
            case LlmAnthropicBlockWriter.ACTION_RANGE -> decodeBlockRange;
            case LlmAnthropicBlockWriter.ACTION_FORMAT -> decodeControlFormat;
            case LlmAnthropicBlockWriter.ACTION_FORMAT_TYPE -> decodeControlFormatType;
            default -> decodeBlockDrop;
            };

            if (action == LlmAnthropicBlockWriter.ACTION_REJECT)
            {
                outcome = LlmAnthropicBlockWriter.REJECT;
            }

            return outcome;
        }

        private void beginReasoning(
            int blockMessage)
        {
            onDecodeBlock(LlmSectionType.ASSISTANT_REASONING, blockMessage, null);

            writing = writer();
            writing.beginReasoning(blockBuffer(), 0);
            blockThen = decodeParts;
            decoder = decodeBlockKey;
        }

        private void beginMedia(
            LlmSectionType type,
            int blockMessage)
        {
            final String mediaType = probe.attributes().mediaType();

            onDecodeBlock(type, blockMessage, probeExtension);

            writing = writer();
            writing.beginMedia(blockBuffer(), 0, type == LlmSectionType.USER_IMAGE, mediaType);
            blockThen = decodeParts;
            decoder = decodeBlockKey;
        }

        private void beginCitations(
            int blockMessage)
        {
            onDecodeBlock(LlmSectionType.CITATIONS, blockMessage, null);

            writing = writer();
            writing.beginCitations(blockBuffer(), 0);
            blockThen = decodePart;
            decoder = decodeBlockElement;
        }

        private boolean endBlock()
        {
            final boolean accepted = writing.finish();

            writing = null;
            decoder = blockThen;

            return accepted;
        }

        private int onBlockEvent(
            JsonEvent event,
            JsonParserEx parser,
            LlmBlockHandler handler)
        {
            int outcome;

            final boolean fragment = event == JsonEvent.VALUE_STRING && parser.deferredBytes();
            final int length = fragment ? parser.getStringView().length() : 0;

            if (probing() && !fragment)
            {
                probe.onEvent(event, parser);
            }

            try
            {
                if (writing.reserve(event))
                {
                    outcome = handler.handle(this, event, parser);

                    if (fragment && outcome != LlmAnthropicBlockWriter.REJECT)
                    {
                        parser.consumed(length);
                    }
                }
                else
                {
                    outcome = LlmAnthropicBlockWriter.REJECT;
                }
            }
            catch (JsonException ex)
            {
                outcome = LlmAnthropicBlockWriter.REJECT;
            }

            return outcome;
        }

        private MutableDirectBufferEx blockBuffer()
        {
            if (blockSlot == NO_SLOT)
            {
                blockSlot = blockPool.acquire(initialId);

                if (blockSlot == NO_SLOT)
                {
                    cleanup(decodeTraceId);
                }
            }

            return blockSlot != NO_SLOT ? blockPool.buffer(blockSlot) : NO_BUFFER;
        }

        private void resumeWriter()
        {
            if (writing != null)
            {
                writing.resume(blockBuffer(), 0);
            }
        }

        private void suspendWriter()
        {
            if (writing != null)
            {
                if (decoder != decodeIgnore)
                {
                    writing.flush(false);
                    writing.resume(NO_BUFFER, 0);
                }
                else
                {
                    writing = null;
                }
            }

            cleanupBlockSlot();
        }

        private void cleanupBlockSlot()
        {
            if (blockSlot != NO_SLOT)
            {
                blockPool.release(blockSlot);
                blockSlot = NO_SLOT;
            }
        }

        private void onDecodeSectionData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            final int flags = last ? Flags.fin(blockFlags) : blockFlags;

            final boolean init = Flags.hasInit(flags);
            final LlmSectionAttributes attributes;

            if (init)
            {
                if (probing())
                {
                    blockAttributes.copyFrom(probe.attributes());
                    sentAttributes.copyFrom(probe.attributes());
                }

                attributes = blockAttributes;
            }
            else if (probing() && last)
            {
                finAttributes.difference(probe.attributes(), sentAttributes);
                attributes = finAttributes;
            }
            else if (lateIsError != null && last)
            {
                finAttributes.clear();
                finAttributes.isError(lateIsError);
                attributes = finAttributes;
            }
            else
            {
                attributes = null;
            }

            stream.doAppData(traceId, authorization, flags, init ? blockSection : null, blockMessage,
                init ? blockExtension : null, attributes, buffer, offset, length);

            blockFlags = Flags.NONE;
        }

        private void onDecodeRootEnd(
            long traceId)
        {
            flushHeld();

            if (streaming)
            {
                stream.doAppData(decodeTraceId, decodeAuthorization, Flags.COMPLETE, LlmSectionType.CONTROL, -1, null, null,
                    STREAM_OPTIONS_SECTION, 0, STREAM_OPTIONS_SECTION.capacity());
            }

            decoder = decodeEnd;
        }

        private void onDecodeEnd(
            long traceId)
        {
            if (decoder != decodeEnd)
            {
                onDecodeParseError(traceId);
            }
            else if (stream == null)
            {
                cleanup(traceId);
            }
            else
            {
                stream.doAppFlush(traceId, initialAuthorization, maxOutputTokens);

                if (StreamState.replyClosed(stream.state))
                {
                    stream.doAppEnd(traceId);
                }
                else
                {
                    stream.state = StreamState.closingInitial(stream.state);
                }
            }
        }

        private void onDecodeParseError(
            long traceId)
        {
            onDecodeInvalidRequest(traceId, MESSAGE_PARSE_ERROR);
        }

        private void onDecodeInvalidRequest(
            long traceId,
            String message)
        {
            onDecodeRejected(traceId, STATUS_BAD_REQUEST, message);
        }

        private void onDecodeModelNotFound(
            long traceId,
            String model)
        {
            onDecodeRejected(traceId, STATUS_NOT_FOUND, MESSAGE_MODEL_NOT_FOUND.formatted(model));
        }

        private void onDecodeRejected(
            long traceId,
            int status,
            String message)
        {
            decoder = decodeIgnore;
            heldPending = false;

            initialAck = initialSeq;
            doNetWindow(traceId);
            doNetError(traceId, initialAuthorization, status, message);
            if (stream != null)
            {
                stream.doAppAbort(traceId);
                stream.doAppReset(traceId);
            }
        }

        private void doNetError(
            long traceId,
            long authorization,
            LlmErrorFW error)
        {
            final int status = error.status() != -1 ? error.status() : STATUS_INTERNAL_SERVER_ERROR;

            doNetError(traceId, authorization, status, error.type().asString(), error.message().asString());
        }

        private void doNetError(
            long traceId,
            long authorization,
            int status,
            String message)
        {
            doNetError(traceId, authorization, status, null, message);
        }

        private void doNetError(
            long traceId,
            long authorization,
            int status,
            String type,
            String message)
        {
            final DirectBufferEx body = asBuffer(errorBody(status, type, message));

            cleanupDecodeSlot();

            doNetBegin(traceId, authorization, 0L, Integer.toString(status), CONTENT_TYPE_JSON);
            replyBody = body;
            replyBodySent = 0;

            flushReplyBody(traceId);

            if (replyBody != null)
            {
                state = StreamState.closingReply(state);
                pendingReplyEndTraceId = traceId;
                pendingReplyEndAuthorization = authorization;
            }
            else
            {
                doNetEnd(traceId, authorization, EMPTY_OCTETS);
            }

            doNetReset(traceId);
        }

        private void cleanup(
            long traceId)
        {
            decoder = decodeIgnore;
            doNetReset(traceId);
            doNetAbort(traceId, initialAuthorization, EMPTY_OCTETS);
            if (stream != null)
            {
                stream.cleanup(traceId);
            }
            cleanupDecodeSlot();
            cleanupReplyBody();
            cleanupBlockSlot();
            if (!deauthorized)
            {
                deauthorized = true;
                deauthorize.run();
            }
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
            }
            decodeSlotOffset = 0;
        }

        private void cleanupReplyBody()
        {
            replyBody = null;
            replyBodySent = 0;
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
            case WindowFW.TYPE_ID:
                onNetWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onNetReset(resetRO.wrap(buffer, index, index + length));
                break;
            case ChallengeFW.TYPE_ID:
                onNetChallenge(challengeRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            initialSeq = abort.sequence();
            state = StreamState.closeInitial(state);

            cleanup(traceId);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = window.padding();
            replyBudgetId = window.budgetId();
            state = StreamState.openReply(state);

            if (replyBudgetId != 0L)
            {
                if (replyDebit == null)
                {
                    replyDebit = LlmAnthropicServerFactory.this.context.supplyDebit(replyId, replyBudgetId,
                        this::onReplyCredit);
                }
                replyDebit.declare(traceId, replyPad, 0);
            }

            onReplyCredit(traceId);
        }

        private void onReplyCredit(
            long traceId)
        {
            flushReplyBody(traceId);

            if (stream != null)
            {
                stream.flushEncodeSlot(traceId);
                stream.decodeReply();
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = StreamState.closeReply(state);

            cleanup(traceId);
        }

        private void onNetChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            if (stream != null)
            {
                stream.doAppChallenge(traceId, authorization, extension);
            }
        }

        private void doNetData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW attributes,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final DataFW.Builder dataBuilder = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .budgetId(replyBudgetId)
                .reserved(reserved)
                .payload(buffer, offset, length);

            if (attributes.sizeof() > 0)
            {
                final HttpDataExFW dataEx = httpDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(httpTypeId)
                    .headers(eventHeadersRO.wrap(attributes.buffer(), attributes.offset(), attributes.limit()))
                    .build();

                dataBuilder.extension(dataEx.buffer(), dataEx.offset(), dataEx.sizeof());
            }
            else
            {
                dataBuilder.extension(EMPTY_OCTETS);
            }

            final DataFW data = dataBuilder.build();

            network.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
            replySeq += reserved;
        }

        private int claimReply(
            long traceId,
            int minimum,
            int maximum)
        {
            return replyDebit != null ? replyDebit.claim(traceId, minimum, maximum) : maximum;
        }

        private void flushReplyBody(
            long traceId)
        {
            if (replyBody != null)
            {
                boolean blocked = false;

                while (!blocked && replyBodySent < replyBody.capacity())
                {
                    final int remaining = replyBody.capacity() - replyBodySent;
                    final long available = replyMax - (replySeq - replyAck) - replyPad;
                    final int wanted = (int) Math.min(remaining, Math.max(available, 0L));
                    final int slice = wanted > 0 ? claimReply(traceId, 1, wanted) : 0;

                    blocked = slice <= 0;

                    if (!blocked)
                    {
                        final int flags = (replyBodySent == 0 ? Flags.INIT : Flags.NONE) |
                            (replyBodySent + slice == replyBody.capacity() ? Flags.FIN : Flags.NONE);

                        doNetData(traceId, initialAuthorization, flags, 0L, slice + replyPad, EMPTY_OCTETS, replyBody,
                            replyBodySent, slice);

                        replyBodySent += slice;
                    }
                }

                if (!blocked)
                {
                    cleanupReplyBody();
                }
            }

            completeReplyEnd();
        }

        private void completeReplyEnd()
        {
            if (StreamState.replyClosing(state) && !StreamState.replyClosed(state) && replyBody == null &&
                (stream == null || stream.writable()))
            {
                doNetEnd(pendingReplyEndTraceId, pendingReplyEndAuthorization, EMPTY_OCTETS);
            }
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long affinity,
            String status,
            String responseContentType)
        {
            final HttpBeginExFW.Builder httpBeginExBuilder = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HEADER_STATUS).value(status));

            if (responseContentType != null)
            {
                httpBeginExBuilder.headersItem(h -> h.name(HEADER_CONTENT_TYPE).value(responseContentType));
            }

            final HttpBeginExFW httpBeginEx = httpBeginExBuilder.build();

            state = StreamState.openingReply(state);
            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof())
                .build();

            network.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        private void doNetEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!StreamState.replyClosed(state))
            {
                state = StreamState.closeReply(state);
                final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .state(state)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .extension(extension)
                    .build();

                network.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());

                if (!deauthorized)
                {
                    deauthorized = true;
                    deauthorize.run();
                }
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!StreamState.replyOpening(state) && !StreamState.replyClosed(state))
            {
                doNetBegin(traceId, authorization, 0L, Integer.toString(STATUS_INTERNAL_SERVER_ERROR), null);
            }

            if (!StreamState.replyClosed(state))
            {
                state = StreamState.abortingReply(state);
                state = StreamState.closeReply(state);
                final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .state(state)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .extension(extension)
                    .build();

                network.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());

                if (!deauthorized)
                {
                    deauthorized = true;
                    deauthorize.run();
                }
            }
        }

        private void doNetWindow(
            long traceId)
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .state(state)
                .maximum(initialMax)
                .traceId(traceId)
                .budgetId(0L)
                .padding(0)
                .capabilities(FRAMING_CAPABILITIES_MASK)
                .build();

            network.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doNetReset(
            long traceId)
        {
            if (!StreamState.initialClosing(state))
            {
                state = StreamState.closeInitial(state);
                final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .state(state)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .build();

                network.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
            }
        }

        private void doNetChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .state(state)
                .maximum(initialMax)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension)
                .build();

            network.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
        }
    }

    private final class LlmAnthropicStream
    {
        private final LlmAnthropicServer server;

        private MessageConsumer app;
        private long initialId;
        private long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBud;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private LlmAnthropicReplyDecoder decoder;
        private LlmAnthropicReplyDecoder dataDecoder;
        private JsonParserEx replyParser;
        private byte[] replyTail;
        private int replyTailLength;
        private int replyFlags;
        private int replyReserved;
        private int replyFrameLength;
        private int replyPayloadFrom;
        private int replyWindowFrom;
        private int replySkipDepth;
        private final LlmEventCopier replyCopier = new LlmEventCopier();
        private String judgedKey;
        private JsonGeneratorEx replyArgs;
        private JsonGeneratorEx argsGenerator;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotFrom;
        private long stagedReserved;

        private boolean flushed;

        private LlmAnthropicStream(
            LlmAnthropicServer server)
        {
            this.server = server;
            this.generator = JsonEx.createGenerator();
            this.eventStart = NO_EVENT;
            this.eventPhase = EVENT_NONE;
            this.decoder = decodeReplyNext;
            this.dataDecoder = decodeReplyNext;
        }

        private long initialAvailable()
        {
            return initialMax - (initialSeq - initialAck);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final OctetsFW extension = reset.extension();
            final LlmResetExFW resetEx = extension.get(llmResetExRO::tryWrap);

            state = StreamState.closeInitial(state);

            if (resetEx != null && !StreamState.replyOpening(server.state))
            {
                server.doNetError(traceId, authorization, resetEx.error());
                doAppReset(traceId);
            }
            else
            {
                server.cleanup(traceId);
            }
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();

            initialAck = window.acknowledge();
            initialMax = window.maximum();
            initialBud = window.budgetId();
            state = StreamState.openInitial(state);

            server.decodeNet(traceId, server.initialAuthorization, 0L);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            String model)
        {
            this.initialId = supplyInitialId.applyAsLong(server.exitId);
            this.replyId = supplyReplyId.applyAsLong(initialId);

            final LlmBeginExFW.Builder builder = llmBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .dialect(NAME);

            if (server.contentType != null)
            {
                builder.contentType(server.contentType);
            }

            builder.model(model);

            if (server.beta != null)
            {
                final byte[] beta = server.beta.getBytes(UTF_8);
                builder.extension(e -> e.set(beta));
            }

            final LlmBeginExFW llmBeginEx = builder.build();

            state = StreamState.openingInitial(state);
            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .state(state)
                .maximum(initialMax)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(0L)
                .extension(llmBeginEx.buffer(), llmBeginEx.offset(), llmBeginEx.sizeof())
                .build();

            app = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(),
                this::onAppMessage);
            app.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

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
            case WindowFW.TYPE_ID:
                onAppWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onAppReset(resetRO.wrap(buffer, index, index + length));
                break;
            case ChallengeFW.TYPE_ID:
                onAppChallenge(challengeRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            final OctetsFW extension = begin.extension();
            final LlmBeginExFW llmBeginEx = extension.get(llmBeginExRO::tryWrap);

            replySeq = sequence;
            replyAck = acknowledge;
            state = StreamState.openingReply(state);

            replyMax = replyPool.slotCapacity();

            responseId = llmBeginEx != null ? llmBeginEx.id().asString() : null;
            responseModel = llmBeginEx != null ? llmBeginEx.model().asString() : null;
            responseDialect = llmBeginEx != null ? llmBeginEx.dialect().asString() : null;
            replyKeys = NAME.equals(responseDialect) ? memberKeys : foreignKeys;
            measureFraming();

            server.doNetBegin(traceId, authorization, affinity, STATUS_OK,
                server.streaming ? CONTENT_TYPE_EVENT_STREAM : CONTENT_TYPE_JSON);

            beginEncode(traceId, authorization);
            doEncodeBegin();
            doEncodeFlush();

            doAppWindow(traceId);
        }

        private void onAppData(
            DataFW data)
        {
            replySeq = data.sequence() + data.reserved();
            stagedReserved += data.reserved();

            stageReply(data.traceId(), data.authorization(), data.reserved(), data.flags(),
                data.extension(), data.payload() != null ? data.payload() : EMPTY_OCTETS);
            decodeReply();
        }

        private void stageReply(
            long traceId,
            long authorization,
            int reserved,
            int flags,
            OctetsFW extension,
            OctetsFW payload)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = replyPool.acquire(replyId);
            }

            final int required = REPLY_FRAME_OVERHEAD + extension.sizeof() + payload.sizeof();

            if (decodeSlot == NO_SLOT || decodeSlotOffset + required > replyPool.slotCapacity())
            {
                server.cleanup(traceId);
            }
            else
            {
                final MutableDirectBufferEx buffer = replyPool.buffer(decodeSlot);

                decodeSlotOffset = replyFrameRW.wrap(buffer, decodeSlotOffset, replyPool.slotCapacity())
                    .traceId(traceId)
                    .authorization(authorization)
                    .budgetId(0L)
                    .reserved(reserved)
                    .flags(flags)
                    .extension(extension.buffer(), extension.offset(), extension.sizeof())
                    .payload(payload.buffer(), payload.offset(), payload.sizeof())
                    .build()
                    .limit();
            }
        }

        private void decodeReply()
        {
            while ((decodeSlot != NO_SLOT && (decodeSlotFrom < decodeSlotOffset || decoder == decodeReplyResume) ||
                finishing()) && writable())
            {
                final MutableDirectBufferEx buffer = decodeSlot != NO_SLOT ? replyPool.buffer(decodeSlot) : null;
                final int from = decodeSlotFrom;
                final int heldBefore = hold.held();

                if (finishing())
                {
                    decodeFinish();
                }
                else if (decoder == decodeReplyResume)
                {
                    onAppResume(buffer);
                }
                else
                {
                    final LlmReplyFrameFW frame = replyFrameRO.wrap(buffer, decodeSlotFrom, decodeSlotOffset);

                    decodeSlotFrom = frame.limit();

                    if ((frame.flags() & REPLY_FLAG_END) != 0)
                    {
                        onReplyEnd(frame);
                    }
                    else if ((frame.flags() & REPLY_FLAG_ERROR) != 0)
                    {
                        onReplyError(frame);
                    }
                    else
                    {
                        onAppSection(buffer, frame);
                    }
                }

                if (decodeSlot != NO_SLOT)
                {
                    decodeSlotFrom = Math.min(Math.max(decodeSlotFrom, from + hold.held() - heldBefore), decodeSlotOffset);
                }

                if (decodeSlot != NO_SLOT && hold.held() > 0 && decoder != decodeReplyResume)
                {
                    final int base = hold.held();
                    final int unread = decodeSlotOffset - decodeSlotFrom;

                    buffer.putBytes(base, buffer, decodeSlotFrom, unread);
                    decodeSlotOffset = base + unread;
                    decodeSlotFrom = base;
                }
            }

            if (decodeSlot != NO_SLOT && decodeSlotFrom == decodeSlotOffset && hold.held() == 0 &&
                decoder != decodeReplyResume)
            {
                cleanupDecodeSlot();
            }
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                replyPool.release(decodeSlot);
                decodeSlot = NO_SLOT;
            }

            decodeSlotOffset = 0;
            decodeSlotFrom = 0;
            stagedReserved = 0;
        }

        private void onAppSection(
            DirectBufferEx buffer,
            LlmReplyFrameFW frame)
        {
            final long traceId = frame.traceId();
            final long authorization = frame.authorization();
            final int flags = frame.flags();
            final OctetsFW payload = frame.payload();

            replyFlags = flags;
            replyReserved = frame.reserved();
            replyFrameLength = frame.limit() - frame.offset();
            replyPayloadFrom = payload != null ? payload.offset() - frame.offset() : replyFrameLength;

            beginEncode(traceId, authorization);

            if (Flags.hasInit(flags))
            {
                final LlmDataExFW llmDataEx = frame.extension().get(llmDataExRO::tryWrap);
                final LlmSectionDataExFW section = llmDataEx != null ? llmDataEx.section() : null;
                final LlmSectionType type = section != null ? section.type().get() : LlmSectionType.NONE;

                replyAttributes.readFrom(section);
                replyTailLength = 0;
                replyParser().reset();

                doEncodeSection(
                    type,
                    section != null ? section.message() : -1,
                    replyAttributes.toolCallId(),
                    replyAttributes.toolName());

                if (kind == KIND_CONTROL)
                {
                    replyArgs().reset();
                }

                if (kind == KIND_TOOL_CALL)
                {
                    replyCopier.reset();
                    argsGenerator().reset();
                }

                dataDecoder = switch (kind)
                {
                case KIND_TEXT, KIND_REASONING, KIND_TOOL_CALL, KIND_CONTROL -> decodeReplyTextRoot;
                default -> decodeReplyTextDone;
                };
            }

            replyWindowFrom = replyPayloadFrom;

            if (replyTailLength > 0)
            {
                if (replyTailLength > replyPayloadFrom)
                {
                    rejectReply();
                }
                else
                {
                    final int gapFrom = frame.offset() + replyPayloadFrom - replyTailLength;

                    ((MutableDirectBufferEx) buffer).putBytes(gapFrom, replyTail, 0, replyTailLength);
                    replyWindowFrom -= replyTailLength;
                    replyTailLength = 0;
                }
            }

            decoder = dataDecoder;

            decodeFrame(buffer, frame.offset());
        }

        private void onAppResume(
            DirectBufferEx buffer)
        {
            beginEncode(encodeTraceId, encodeAuthorization);

            decoder = dataDecoder;

            final int offset = decodeSlotFrom;

            decodeSlotFrom = offset + replyFrameLength;

            decodeFrame(buffer, offset);
        }

        private void decodeFrame(
            DirectBufferEx buffer,
            int offset)
        {
            LlmAnthropicReplyDecoder previous = null;

            while (previous != decoder && decoder != decodeReplyNext && decoder != decodeReplyResume)
            {
                previous = decoder;
                decoder.decode(this, buffer, offset, offset + replyFrameLength);

                if (decoder == decodeReplyNext && previous != decodeReplyEnd && Flags.hasFin(replyFlags))
                {
                    decoder = decodeReplyEnd;
                }
            }

            if (decoder == decodeReplyResume)
            {
                decodeSlotFrom = offset + replyWindowFrom;
                replyFrameLength -= replyWindowFrom;
                replyWindowFrom = 0;
                replyPayloadFrom = 0;

                doEncodeFlush();
            }
            else
            {
                decoder = decodeReplyNext;

                stagedReserved -= replyReserved;

                doEncodeFlush();
            }
        }

        private JsonParserEx replyParser()
        {
            if (replyParser == null)
            {
                replyParser = JsonEx.createParser();
            }

            return replyParser;
        }

        private JsonParserEx wrapReply(
            DirectBufferEx buffer,
            int offset)
        {
            replyParser().wrap(buffer, offset + replyWindowFrom, offset + replyFrameLength, Flags.hasFin(replyFlags));

            return replyParser;
        }

        private int positionReply(
            DirectBufferEx buffer,
            int offset,
            LlmAnthropicReplyDecoder state)
        {
            final int windowLimit = offset + replyFrameLength;

            if (decoder == state)
            {
                final int tailLength = Flags.hasFin(replyFlags) ? 0 : replyParser.remaining();

                if (tailLength > REPLY_TAIL_MAX || tailLength > replyPayloadFrom)
                {
                    rejectReply();
                }
                else
                {
                    if (tailLength > 0)
                    {
                        if (replyTail == null)
                        {
                            replyTail = new byte[REPLY_TAIL_MAX];
                        }

                        buffer.getBytes(windowLimit - tailLength, replyTail, 0, tailLength);
                    }

                    replyTailLength = tailLength;
                    dataDecoder = state;
                    decoder = decodeReplyNext;
                }
            }
            else if (decoder != decodeReplyNext && decoder != decodeReplyResume)
            {
                replyWindowFrom = windowLimit - replyParser.remaining() - offset;
            }

            return offset;
        }

        private int pauseReply(
            DirectBufferEx buffer,
            int offset)
        {
            replyWindowFrom = replyFrameLength - replyParser.remaining();
            dataDecoder = decoder;
            decoder = decodeReplyResume;

            return offset;
        }

        private int pauseEnd(
            DirectBufferEx buffer,
            int offset)
        {
            replyWindowFrom = replyFrameLength;
            wrapReply(buffer, offset);

            return pauseReply(buffer, offset);
        }

        private LlmAnthropicReplyDecoder memberState(
            CharSequence key)
        {
            LlmAnthropicReplyDecoder state = decodeReplyTextSkip;

            switch (kind)
            {
            case KIND_CONTROL:
                state = controlMember(key);
                break;
            case KIND_TEXT:
                if (MEMBER_TEXT.contentEquals(key))
                {
                    valueMember = VALUE_TEXT;
                    state = decodeReplyTextValue;
                }
                break;
            case KIND_REASONING:
                if (MEMBER_TEXT.contentEquals(key))
                {
                    valueMember = VALUE_TEXT;
                    state = decodeReplyTextValue;
                }
                else if (MEMBER_SIGNATURE.contentEquals(key))
                {
                    valueMember = VALUE_SIGNATURE;
                    state = decodeReplyTextValue;
                }
                else if (MEMBER_REDACTED_DATA.contentEquals(key))
                {
                    valueMember = VALUE_REDACTED;
                    state = decodeReplyTextValue;
                }
                break;
            case KIND_TOOL_CALL:
                if (MEMBER_ARGUMENTS.contentEquals(key))
                {
                    state = decodeReplyArgs;
                }
                break;
            default:
                break;
            }

            return state;
        }

        private LlmAnthropicReplyDecoder afterValue()
        {
            return kind == KIND_REASONING ? decodeReplyTextKey : decodeReplyTextDone;
        }

        private JsonGeneratorEx replyArgs()
        {
            if (replyArgs == null)
            {
                replyArgs = JsonEx.createGenerator();
            }

            return replyArgs;
        }

        private int pullArgs(
            JsonParserEx parser,
            boolean resumed)
        {
            int outcome = ARGS_IDLE;

            boolean more = true;
            boolean stop = false;
            boolean opened = false;
            boolean complete = false;
            boolean holding = false;
            boolean pending = resumed;
            int consumed = 0;
            int base = 0;
            MutableDirectBufferEx buffer = null;
            int limit = 0;

            while (more && !stop)
            {
                more = pending || parser.hasNextEvent();

                if (more && !opened && !roomArgs())
                {
                    outcome = ARGS_WAIT;
                    stop = true;
                }
                else if (more)
                {
                    final JsonEvent event = pending ? JsonEvent.VALUE_STRING : parser.nextEvent();

                    pending = false;

                    if (replyCopier.depth() == 0 && event != JsonEvent.START_OBJECT)
                    {
                        replySkipDepth = event == JsonEvent.START_ARRAY ? 1 : 0;
                        decoder = event == JsonEvent.START_ARRAY ? decodeReplyTextSkip : decodeReplyTextKey;
                        outcome = ARGS_FULL;
                        stop = true;
                    }
                    else
                    {
                        if (!opened)
                        {
                            opened = true;
                            buffer = encodeBuffer();
                            limit = encodeLimit();

                            if (server.streaming)
                            {
                                eventBegin(encodeLength(), EVENT_CODE_BLOCK_DELTA);
                                writeArgsOpen(generator, blocks - 1);
                            }
                            else if (replyCopier.depth() == 0)
                            {
                                generator.writeKey(MEMBER_INPUT);
                                generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.INCOMPLETE);
                                toolInputs++;
                            }

                            base = encodeLength();
                            argsGenerator().wrap(buffer, encodeBase + base, server.streaming ? limit - chunkTail : limit);
                        }

                        final JsonGeneratorEx args = argsGenerator();
                        final int taken = replyCopier.copy(args, event, parser);

                        if (event == JsonEvent.END_OBJECT || event == JsonEvent.END_ARRAY)
                        {
                            complete = replyCopier.depth() == 0;
                            stop = complete;
                        }

                        if (taken != LlmEventCopier.NO_TAKEN)
                        {
                            consumed = taken;
                            holding = !parser.deferredBytes();
                            stop = true;
                        }

                        stop |= args.remaining() < ARGS_HEADROOM;
                    }
                }
            }

            if (opened)
            {
                if (consumed > 0)
                {
                    parser.consumed(consumed);
                }

                final boolean full = !complete && more;

                endArgs(base, buffer, limit);

                if (full && !server.streaming)
                {
                    doEncodeFlush();
                    beginEncode();
                }

                outcome = complete ? ARGS_DONE : holding ? ARGS_HELD : more ? ARGS_FULL : ARGS_IDLE;
            }

            return outcome;
        }

        private boolean roomArgs()
        {
            final int need = ARGS_HEADROOM + (server.streaming ? chunkHead + chunkTail : MEMBER_INPUT.length() + KEY_FRAMING);

            return roomEvent(need);
        }

        private boolean roomEvent(
            int need)
        {
            if (generator.remaining() < need && encodeLength() > 0)
            {
                doEncodeFlush();
                beginEncode();
            }

            final boolean fits = writable() && generator.remaining() >= need;

            if (!fits && writable())
            {
                server.cleanup(encodeTraceId);
            }

            return fits;
        }

        private void endArgs(
            int base,
            MutableDirectBufferEx buffer,
            int limit)
        {
            encodeShift = base + argsGenerator().length();
            generator.wrap(buffer, encodeBase + encodeShift, limit);

            if (server.streaming)
            {
                writeArgsClose(generator);
                eventEnd(encodeLength());
                flushEncodeSlot(encodeTraceId);
                beginEncode();
            }
        }

        private JsonGeneratorEx argsGenerator()
        {
            if (argsGenerator == null)
            {
                argsGenerator = server.streaming ? JsonEx.createGenerator(ESCAPED_OPTIONS) : JsonEx.createGenerator();
            }

            return argsGenerator;
        }

        private int pullControl(
            JsonParserEx parser,
            boolean resumed)
        {
            final JsonGeneratorEx value = replyArgs();
            final int room = controlMode == CONTROL_INLINE ? Math.min(extBuffer.capacity(), generator.remaining())
                : extBuffer.capacity();

            value.wrap(extBuffer, 0, room);

            int outcome = ARGS_IDLE;
            int consumed = 0;
            boolean more = true;
            boolean stop = false;
            boolean complete = false;
            boolean holding = false;
            boolean pending = resumed;
            boolean pulled = false;

            while (more && !stop && value.remaining() >= ARGS_HEADROOM)
            {
                more = pending || parser.hasNextEvent();

                if (more)
                {
                    final JsonEvent event = pending ? JsonEvent.VALUE_STRING : parser.nextEvent();

                    pending = false;
                    pulled = true;

                    final int taken = replyCopier.copy(value, event, parser);
                    final boolean scalar = event != JsonEvent.START_OBJECT && event != JsonEvent.START_ARRAY &&
                        event != JsonEvent.KEY_NAME;

                    if (taken != LlmEventCopier.NO_TAKEN)
                    {
                        consumed = taken;
                        holding = !parser.deferredBytes();
                        stop = true;
                    }
                    else if (replyCopier.depth() == 0 && scalar)
                    {
                        complete = true;
                        stop = true;
                    }
                }
            }

            if (pulled)
            {
                if (consumed > 0)
                {
                    parser.consumed(consumed);
                }

                flushReplyControl(value);

                outcome = complete ? ARGS_DONE : holding ? ARGS_HELD : more ? ARGS_FULL : ARGS_IDLE;
            }
            else if (more && !stop)
            {
                outcome = ARGS_WAIT;
            }

            return outcome;
        }

        private LlmAnthropicReplyDecoder controlMember(
            CharSequence key)
        {
            LlmAnthropicReplyDecoder state = decodeReplyTextSkip;
            final int action = replyKeys.select(key);

            if (action == LlmMemberKeys.COPY)
            {
                onEncodeControlBegin(replyKeys.target().toString());
                replyArgs().reset();
                replyCopier.reset();
                state = decodeReplyControlValue;
            }
            else if (action == LlmMemberKeys.JUDGE)
            {
                judgedKey = key.toString();
                state = decodeReplyControlJudge;
            }

            return state;
        }

        private void judgeMember(
            JsonParserEx parser)
        {
            final JsonEvent event = parser.nextEvent();
            final boolean scalar = event != JsonEvent.START_OBJECT && event != JsonEvent.START_ARRAY;
            final CharSequence text = event == JsonEvent.VALUE_NUMBER || event == JsonEvent.VALUE_STRING
                ? parser.getStringView()
                : "";
            final int verdict = LlmLegality.advance(judgedKey, LlmLegality.UNDECIDED, 0, event, text);

            if (LlmLegality.harmless(verdict))
            {
                replySkipDepth = scalar ? 0 : 1;
                decoder = scalar ? decodeReplyTextKey : decodeReplyTextSkip;
            }
            else
            {
                rejectReply();
            }
        }

        private boolean pausedControl()
        {
            boolean paused = false;

            if (controlMode == CONTROL_INLINE)
            {
                doEncodeFlush();
                beginEncode();

                paused = !writable();
            }

            return paused;
        }

        private boolean generatorTight()
        {
            return controlMode == CONTROL_INLINE && generator.remaining() < ARGS_HEADROOM;
        }

        private void flushReplyControl(
            JsonGeneratorEx value)
        {
            final int length = value.length();

            if (length > 0)
            {
                if (controlMode == CONTROL_HELD)
                {
                    onEncodeControlHeld(extBuffer, 0, length);
                }
                else if (controlMode == CONTROL_INLINE)
                {
                    generator.writeSegment(extBuffer, 0, length, Completion.INCOMPLETE);
                }
            }

            if (hold.failed() || !holdFits())
            {
                rejectReply();
            }
        }

        private void rejectReply()
        {
            replyTailLength = 0;
            decoder = decodeReplyNext;
            server.cleanup(encodeTraceId);
        }

        private void ackReply(
            long traceId)
        {
            final long replyAckMax = replySeq - hold.held() - stagedReserved;

            if (replyAckMax > replyAck)
            {
                replyAck = replyAckMax;
                doAppWindow(traceId);
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            replySeq = end.sequence();
            state = StreamState.closeReply(state);

            stageReply(end.traceId(), end.authorization(), 0, REPLY_FLAG_END, end.extension(), EMPTY_OCTETS);

            if (StreamState.initialClosing(state) && !StreamState.initialClosed(state))
            {
                server.onDecodeEnd(end.traceId());
            }

            decodeReply();
        }

        private void onReplyEnd(
            LlmReplyFrameFW frame)
        {
            final long traceId = frame.traceId();
            final long authorization = frame.authorization();
            final OctetsFW extension = frame.extension();

            final LlmEndExFW llmEndEx = extension.get(llmEndExRO::tryWrap);
            final LlmUsageFW usage = llmEndEx != null ? llmEndEx.usage() : null;

            beginEncode(traceId, authorization);

            doEncodeEnd();

            finishReasonName = stopReason(llmEndEx != null ? llmEndEx.finishReason().asString() : null);
            finishInputTokens = usage != null ? usage.inputTokens() : -1;
            finishCacheWriteTokens = usage != null ? usage.cacheWriteTokens() : -1;
            finishCacheReadTokens = usage != null ? usage.cacheReadTokens() : -1;
            finishOutputTokens = usage != null ? usage.outputTokens() : -1;

            decoder = server.streaming ? decodeReplyFinishDelta : decodeReplyFinishBody;

            decodeFinish();
        }

        private boolean finishing()
        {
            return decoder == decodeReplyFinishDelta || decoder == decodeReplyFinishStop ||
                decoder == decodeReplyFinishBody || decoder == decodeReplyFinishEnd;
        }

        private void decodeFinish()
        {
            LlmAnthropicReplyDecoder previous = null;

            while (previous != decoder && finishing())
            {
                previous = decoder;
                decoder.decode(this, EMPTY_BUFFER, 0, 0);
            }

            if (decoder == decodeReplyFinishEnd)
            {
                decoder = decodeReplyNext;

                cleanupHeld();

                doEncodeFlush();

                if (!writable())
                {
                    server.state = StreamState.closingReply(server.state);
                    server.pendingReplyEndTraceId = encodeTraceId;
                    server.pendingReplyEndAuthorization = encodeAuthorization;
                }
                else
                {
                    server.doNetEnd(encodeTraceId, encodeAuthorization, EMPTY_OCTETS);
                }
            }
        }

        private boolean roomFinish(
            int need)
        {
            if (encodeLength() > 0)
            {
                doEncodeFlush();
            }
            else
            {
                flushEncodeSlot(encodeTraceId);
            }

            final boolean empty = writable();

            if (empty)
            {
                beginEncode();
            }

            final boolean fits = empty && generator.remaining() >= need;

            if (empty && !fits)
            {
                server.cleanup(encodeTraceId);
            }

            return fits;
        }

        private int finishNeed()
        {
            return preludeCloseMax + finishFraming + RESPONSE_ESCAPE_EXPANSION * finishReasonName.length() + controlSize() +
                usageMax;
        }

        private boolean holdFits()
        {
            return preludeCloseMax + finishFraming + controlSize() + usageMax <= replyEncodePool.slotCapacity() - ENTRY_HEADER;
        }

        private void finishDelta()
        {
            if (roomFinish(finishNeed()))
            {
                if (envelope == ENVELOPE_PRELUDE)
                {
                    closePrelude();
                }

                openTail();
                generator.write("stop_reason", finishReasonName);
                writeStopSequence();
                writeControls();
                generator.writeEnd();
                putUsage(generator, finishInputTokens, finishCacheWriteTokens, finishCacheReadTokens, finishOutputTokens);
                generator.writeEnd();
                eventEnd(encodeLength());

                decoder = decodeReplyFinishStop;
            }
        }

        private void finishStop()
        {
            if (roomFinish(stopMax))
            {
                eventBegin(encodeLength(), EVENT_CODE_MESSAGE_STOP);
                generator.writeStartObject().write("type", EVENT_MESSAGE_STOP).writeEnd();
                eventEnd(encodeLength());

                decoder = decodeReplyFinishEnd;
            }
        }

        private void finishBody()
        {
            if (roomFinish(finishNeed()))
            {
                if (envelope == ENVELOPE_PRELUDE)
                {
                    closePrelude();
                }

                openTail();
                generator.write("stop_reason", finishReasonName);
                writeStopSequence();
                writeControls();
                putUsage(generator, finishInputTokens, finishCacheWriteTokens, finishCacheReadTokens, finishOutputTokens);
                generator.writeEnd();

                decoder = decodeReplyFinishEnd;
            }
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final OctetsFW extension = abort.extension();

            replySeq = abort.sequence();
            state = StreamState.closeReply(state);

            final LlmAbortExFW llmAbortEx = StreamState.replyOpening(state) && server.streaming
                ? extension.get(llmAbortExRO::tryWrap)
                : null;
            final LlmErrorFW error = llmAbortEx != null ? llmAbortEx.error() : null;

            if (error != null && (error.type().asString() != null || error.message().asString() != null))
            {
                stageReply(traceId, authorization, 0, REPLY_FLAG_ERROR, extension, EMPTY_OCTETS);

                if (StreamState.initialClosing(state) && !StreamState.initialClosed(state))
                {
                    server.onDecodeEnd(traceId);
                }

                decodeReply();
            }
            else
            {
                cleanupDecodeSlot();
                cleanupEncodeSlot();
                cleanupHeld();

                server.doNetAbort(traceId, authorization, extension);

                if (StreamState.initialClosing(state) && !StreamState.initialClosed(state))
                {
                    server.onDecodeEnd(traceId);
                }
            }
        }

        private void onReplyError(
            LlmReplyFrameFW frame)
        {
            final long traceId = frame.traceId();
            final long authorization = frame.authorization();
            final LlmAbortExFW llmAbortEx = frame.extension().get(llmAbortExRO::tryWrap);
            final LlmErrorFW error = llmAbortEx.error();

            cleanupHeld();

            beginEncode(traceId, authorization);
            doEncodeError(error.type().asString(), error.param().asString(), error.message().asString());
            doEncodeFlush();

            if (!writable())
            {
                server.state = StreamState.closingReply(server.state);
                server.pendingReplyEndTraceId = traceId;
                server.pendingReplyEndAuthorization = authorization;
            }
            else
            {
                server.doNetEnd(traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void onAppChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();
            final LlmChallengeExFW llmChallengeEx = extension.get(llmChallengeExRO::tryWrap);

            if (llmChallengeEx == null || llmChallengeEx.typeId() != llmTypeId)
            {
                server.doNetChallenge(traceId, authorization, extension);
            }
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            int maxOutputTokens)
        {
            if (!flushed && !StreamState.initialClosed(state))
            {
                flushed = true;

                final FlushFW.Builder builder = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .state(state)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .budgetId(0L)
                    .reserved(0);

                if (maxOutputTokens != NO_MAX_OUTPUT_TOKENS)
                {
                    final LlmFlushExFW llmFlushEx = llmFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(llmTypeId)
                        .dialect(d -> d.maxOutputTokens(maxOutputTokens))
                        .build();

                    builder.extension(llmFlushEx.buffer(), llmFlushEx.offset(), llmFlushEx.sizeof());
                }

                final FlushFW flush = builder.build();

                app.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
            }
        }

        private void doAppEnd(
            long traceId)
        {
            if (!StreamState.initialClosed(state))
            {
                state = StreamState.closeInitial(state);
                final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .state(state)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .build();

                app.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
            }
        }

        private void doAppAbort(
            long traceId)
        {
            if (!StreamState.initialClosed(state))
            {
                state = StreamState.abortingInitial(state);
                state = StreamState.closeInitial(state);
                final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .state(state)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .build();

                app.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
            }
        }

        private void doAppReset(
            long traceId)
        {
            if (!StreamState.replyClosed(state))
            {
                state = StreamState.closeReply(state);
                final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .state(state)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .build();

                app.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
            }
        }

        private void doAppWindow(
            long traceId)
        {
            state = StreamState.openReply(state);

            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(replyMax)
                .traceId(traceId)
                .budgetId(0L)
                .padding(replyPadding)
                .build();

            app.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doAppChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension)
                .build();

            app.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
        }

        private void doAppData(
            long traceId,
            long authorization,
            int flags,
            LlmSectionType type,
            int message,
            String extension,
            LlmSectionAttributes attributes,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final DataFW.Builder builder = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .state(state)
                .maximum(initialMax)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .budgetId(initialBud)
                .reserved(length)
                .payload(buffer, offset, length);

            if (type != null || attributes != null && attributes.present())
            {
                final LlmDataExFW dataEx = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId)
                    .section(x ->
                    {
                        if (type != null)
                        {
                            x.type(t -> t.set(type)).message(message);
                        }

                        if (attributes != null)
                        {
                            attributes.writeTo(x);
                        }

                        if (extension != null)
                        {
                            final byte[] bytes = extension.getBytes(UTF_8);
                            x.extension(e -> e.set(bytes));
                        }
                    })
                    .build();

                builder.extension(dataEx.buffer(), dataEx.offset(), dataEx.sizeof());
            }

            final DataFW data = builder.build();

            app.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

            initialSeq += length;
        }

        private void beginEncode(
            long traceId,
            long authorization)
        {
            encodeTraceId = traceId;
            encodeAuthorization = authorization;

            beginEncode();
        }

        private void beginEncode()
        {
            encodeShift = 0;

            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = replyEncodePool.acquire(replyId);
            }

            if (encodeSlot != NO_SLOT)
            {
                encodeBase = encodeSlotLimit + ENTRY_HEADER;
                generator.wrap(replyEncodePool.buffer(encodeSlot), encodeBase, replyEncodePool.slotCapacity());
            }
            else
            {
                server.cleanup(encodeTraceId);
                generator.wrap(extBuffer, 0, extBuffer.capacity());
            }

            if (eventPhase != EVENT_NONE)
            {
                eventStart = 0;
            }
        }

        private boolean writable()
        {
            return encodeSlotOffset == encodeSlotLimit;
        }

        private void doEncodeFlush()
        {
            if (encodeSlot != NO_SLOT)
            {
                if (server.streaming)
                {
                    if (eventStart != NO_EVENT)
                    {
                        emitEvent(encodeLength(), false);
                        eventStart = NO_EVENT;
                    }
                }
                else if (encodeLength() > 0)
                {
                    writeEntry(encodeBase, encodeLength(), Flags.COMPLETE, 0);
                }
            }

            flushEncodeSlot(encodeTraceId);
        }

        private void writeEntry(
            int start,
            int length,
            int flags,
            int event)
        {
            final MutableDirectBufferEx buffer = replyEncodePool.buffer(encodeSlot);
            final int at = start - ENTRY_HEADER;

            buffer.putInt(at, length);
            buffer.putByte(at + ENTRY_FLAGS, (byte) flags);
            buffer.putByte(at + ENTRY_EVENT, (byte) event);
            buffer.putLong(at + ENTRY_TRACE, encodeTraceId);
            buffer.putLong(at + ENTRY_AUTHORIZATION, encodeAuthorization);

            encodeSlotLimit = start + length;
            encodeBase = encodeSlotLimit + ENTRY_HEADER;
            encodeShift = 0;
            generator.wrap(buffer, encodeBase, replyEncodePool.slotCapacity());
        }

        private void flushEncodeSlot(
            long traceId)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx buffer = replyEncodePool.buffer(encodeSlot);

                boolean progress = true;

                while (progress && encodeSlotOffset < encodeSlotLimit)
                {
                    final int length = buffer.getInt(encodeSlotOffset);
                    final int flags = buffer.getByte(encodeSlotOffset + ENTRY_FLAGS) & 0xFF;
                    final int event = buffer.getByte(encodeSlotOffset + ENTRY_EVENT) & 0xFF;
                    final long entryTraceId = buffer.getLong(encodeSlotOffset + ENTRY_TRACE);
                    final long entryAuthorization = buffer.getLong(encodeSlotOffset + ENTRY_AUTHORIZATION);
                    final int payload = encodeSlotOffset + ENTRY_HEADER;
                    final int remaining = length - encodeSent;
                    final long available = server.replyMax - (server.replySeq - server.replyAck) - server.replyPad;
                    final int sliceMax = (int) Math.min(remaining, Math.max(available, 0L));
                    final int slice = sliceMax > 0 ? server.claimReply(entryTraceId, 1, sliceMax) : sliceMax;

                    progress = slice > 0 || remaining == 0 && available > 0;

                    if (progress)
                    {
                        final boolean init = encodeSent == 0 && Flags.hasInit(flags);
                        final int initFlags = init ? Flags.INIT : Flags.NONE;

                        encodeSent += slice;

                        final boolean last = encodeSent == length && Flags.hasFin(flags);

                        server.doNetData(entryTraceId, entryAuthorization, last ? Flags.fin(initFlags) : initFlags, 0L,
                            slice + server.replyPad, init ? eventAttributes(event) : EMPTY_OCTETS, buffer,
                            payload + encodeSent - slice, slice);

                        if (encodeSent == length)
                        {
                            encodeSlotOffset = payload + length;
                            encodeSent = 0;
                        }
                    }
                }

                if (writable())
                {
                    cleanupEncodeSlot();
                }
            }

            ackReply(traceId);

            server.completeReplyEnd();
        }

        private OctetsFW eventAttributes(
            int event)
        {
            final DirectBufferEx attributes = EVENT_ATTRIBUTES[event];

            return attributes != null ? eventAttributesRO.wrap(attributes, 0, attributes.capacity()) : EMPTY_OCTETS;
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                replyEncodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
            }

            encodeSlotOffset = 0;
            encodeSlotLimit = 0;
            encodeSent = 0;
        }

        private static final int NO_EVENT = -1;
        private static final int EVENT_NONE = 0;
        private static final int EVENT_OPEN = 1;
        private static final int EVENT_STARTED = 2;

        private static final int KIND_NONE = 0;
        private static final int KIND_TEXT = 1;
        private static final int KIND_REASONING = 2;
        private static final int KIND_TOOL_CALL = 3;
        private static final int KIND_SKIPPED = 4;
        private static final int KIND_CONTROL = 5;

        private static final int VALUE_TEXT = 0;
        private static final int VALUE_SIGNATURE = 1;
        private static final int VALUE_REDACTED = 2;

        private static final int REASONING_NONE = 0;
        private static final int REASONING_TEXT = 1;
        private static final int REASONING_SIGNATURE = 2;
        private static final int REASONING_SIGNED = 3;
        private static final int REASONING_DATA = 4;
        private static final int REASONING_REDACTED = 5;

        private static final int CONTROL_DROP = 0;
        private static final int CONTROL_INLINE = 1;
        private static final int CONTROL_HELD = 2;
        private static final String MEMBER_USAGE = "usage";
        private static final String MEMBER_STOP_SEQUENCE = "stop_sequence";
        private static final Predicate<String> ALL_MEMBERS = name -> true;
        private static final Predicate<String> NOT_DERIVED =
            name -> !MEMBER_USAGE.equals(name) && !MEMBER_STOP_SEQUENCE.equals(name);

        private static final int ENVELOPE_PRELUDE = 0;
        private static final int ENVELOPE_CONTENT = 1;
        private static final int ENVELOPE_TAIL = 2;

        private static final int NONE = -1;
        private static final int MESSAGE_FIRST = 0;

        private static final String EVENT_MESSAGE_START = "message_start";
        private static final String EVENT_BLOCK_START = "content_block_start";
        private static final String EVENT_BLOCK_DELTA = "content_block_delta";
        private static final String EVENT_BLOCK_STOP = "content_block_stop";
        private static final String EVENT_MESSAGE_DELTA = "message_delta";
        private static final String EVENT_MESSAGE_STOP = "message_stop";
        private static final String EVENT_ERROR = "error";
        private static final int EVENT_CODE_MESSAGE_START = 1;
        private static final int EVENT_CODE_BLOCK_START = 2;
        private static final int EVENT_CODE_BLOCK_DELTA = 3;
        private static final int EVENT_CODE_BLOCK_STOP = 4;
        private static final int EVENT_CODE_MESSAGE_DELTA = 5;
        private static final int EVENT_CODE_MESSAGE_STOP = 6;
        private static final int EVENT_CODE_ERROR = 7;
        private static final DirectBufferEx[] EVENT_ATTRIBUTES =
        {
            null,
            eventType(EVENT_MESSAGE_START),
            eventType(EVENT_BLOCK_START),
            eventType(EVENT_BLOCK_DELTA),
            eventType(EVENT_BLOCK_STOP),
            eventType(EVENT_MESSAGE_DELTA),
            eventType(EVENT_MESSAGE_STOP),
            eventType(EVENT_ERROR)
        };

        private final JsonGeneratorEx generator;
        private final OctetsFW eventAttributesRO = new OctetsFW();

        private String responseId;
        private String responseModel;
        private String responseDialect;
        private LlmMemberKeys replyKeys;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private int encodeSlotLimit;
        private int encodeBase;
        private int encodeShift;
        private int chunkHead;
        private int chunkTail;
        private int blockStartMax;
        private int blockStopMax;
        private int closeMax;
        private int attachmentHeadMax;
        private int preludeCloseMax;
        private int finishFraming;
        private int stopMax;
        private int usageMax;
        private String finishReasonName;
        private int finishInputTokens;
        private int finishCacheWriteTokens;
        private int finishCacheReadTokens;
        private int finishOutputTokens;
        private int encodeSent;
        private long encodeTraceId;
        private long encodeAuthorization;
        private int eventPhase;
        private int eventStart;
        private int eventCode;

        private int controlMessage;
        private int controlMode;
        private final LlmControlHold hold = new LlmControlHold(this::heldBuffer);

        private int kind;
        private int valueMember;
        private int reasoning;
        private int toolInputs;
        private int blocks;
        private int blocksStopped;
        private int toolCalls;
        private int envelope;

        private void doEncodeBegin()
        {
            if (server.streaming)
            {
                eventBegin(encodeLength(), EVENT_CODE_MESSAGE_START);
                generator.writeStartObject()
                    .write("type", EVENT_MESSAGE_START)
                    .writeStartObject("message");
                messageIdentity();
                generator.writeStartArray("content").writeEnd();
            }
            else
            {
                generator.writeStartObject();
                messageIdentity();
            }
        }

        private void doEncodeSection(
            LlmSectionType type,
            int message,
            String toolCallId,
            String toolName)
        {
            kind = type == LlmSectionType.CONTROL
                ? KIND_CONTROL
                : message == MESSAGE_FIRST && envelope != ENVELOPE_TAIL ? kindOf(type) : KIND_SKIPPED;
            valueMember = VALUE_TEXT;
            reasoning = REASONING_NONE;
            toolInputs = 0;

            switch (kind)
            {
            case KIND_CONTROL:
                controlMessage = message;
                controlMode = CONTROL_DROP;
                break;
            case KIND_TEXT, KIND_TOOL_CALL:
                openBlock(toolCallId, toolName);
                break;
            default:
                break;
            }
        }

        private boolean blockOpen()
        {
            return blocks > blocksStopped;
        }

        private boolean attachmentInProgress()
        {
            return reasoning == REASONING_SIGNATURE || reasoning == REASONING_DATA;
        }

        private boolean readyValue()
        {
            boolean ready = true;

            if (kind == KIND_REASONING)
            {
                final boolean resuming = valueMember == VALUE_TEXT
                    ? server.streaming ? blockOpen() : reasoning == REASONING_TEXT
                    : attachmentInProgress();

                ready = resuming || roomEvent(blockStartMax + preludeCloseMax + attachmentHeadMax + chunkTail);
            }

            return ready;
        }

        private int onEncodeText(
            JsonStringView chars,
            int length,
            boolean last)
        {
            final int taken;

            if (valueMember != VALUE_TEXT)
            {
                taken = writeAttachment(chars, length, last);
            }
            else if (kind == KIND_REASONING)
            {
                taken = onEncodeThinking(chars, length);
            }
            else
            {
                taken = length > 0 ? server.streaming ? emitTextChunk(chars, length) : writeText(chars, length) : 0;
            }

            return taken;
        }

        private int onEncodeThinking(
            JsonStringView chars,
            int length)
        {
            int taken = length;

            if (server.streaming)
            {
                if (length > 0)
                {
                    if (!blockOpen())
                    {
                        openBlock(null, null);
                    }

                    taken = emitTextChunk(chars, length);
                }
            }
            else
            {
                if (reasoning == REASONING_NONE)
                {
                    openThinking();
                    generator.writeKey("thinking");
                    generator.writeStartEscaped();
                    reasoning = REASONING_TEXT;
                }

                if (reasoning == REASONING_TEXT && length > 0)
                {
                    taken = writeText(chars, length);
                }
            }

            return taken;
        }

        private boolean attachable()
        {
            final boolean streaming = server.streaming;

            return attachmentInProgress() ||
                (valueMember == VALUE_SIGNATURE
                    ? streaming || reasoning == REASONING_NONE || reasoning == REASONING_TEXT
                    : streaming ? !blockOpen() : reasoning == REASONING_NONE);
        }

        private void openAttachment()
        {
            final boolean signature = valueMember == VALUE_SIGNATURE;

            if (server.streaming)
            {
                if (signature && !blockOpen())
                {
                    openBlock(null, null);
                }
                else if (!signature && envelope == ENVELOPE_PRELUDE)
                {
                    closePrelude();
                }

                final int index = signature ? blocks - 1 : blocks++;

                eventBegin(encodeLength(), signature ? EVENT_CODE_BLOCK_DELTA : EVENT_CODE_BLOCK_START);
                writeAttachmentOpen(generator, valueMember, index);
            }
            else
            {
                if (reasoning == REASONING_NONE)
                {
                    if (signature)
                    {
                        openThinking();
                    }
                    else
                    {
                        openRedacted();
                    }
                }
                else if (reasoning == REASONING_TEXT)
                {
                    generator.writeEndEscaped();
                }

                generator.writeKey(signature ? MEMBER_SIGNATURE_FIELD : MEMBER_DATA_FIELD);
                generator.writeStartEscaped();
            }

            reasoning = signature ? REASONING_SIGNATURE : REASONING_DATA;
        }

        private void closeAttachment()
        {
            generator.writeEndEscaped();

            if (server.streaming)
            {
                generator.writeEnd().writeEnd();
                eventEnd(encodeLength());
                reasoning = REASONING_NONE;
            }
            else
            {
                reasoning = valueMember == VALUE_SIGNATURE ? REASONING_SIGNED : REASONING_REDACTED;
            }
        }

        private int writeAttachment(
            JsonStringView chars,
            int length,
            boolean last)
        {
            int taken = length;

            if (attachable())
            {
                taken = 0;

                boolean progress = true;

                if (!attachmentInProgress())
                {
                    openAttachment();
                }

                while (taken < length && progress && writable())
                {
                    if (generator.remaining() >= RESPONSE_ESCAPE_EXPANSION + chunkTail)
                    {
                        final MutableDirectBufferEx buffer = encodeBuffer();
                        final int limit = encodeLimit();

                        encodeShift = encodeLength();
                        generator.wrap(buffer, encodeBase + encodeShift, limit - chunkTail);

                        final int before = generator.consumed();

                        generator.writeSegment(chars, taken, length);

                        taken += generator.consumed() - before;
                        progress = generator.consumed() > before;

                        encodeShift += generator.length();
                        generator.wrap(buffer, encodeBase + encodeShift, limit);
                    }
                    else if (encodeLength() > 0)
                    {
                        doEncodeFlush();
                        beginEncode();
                    }
                    else
                    {
                        progress = false;
                        server.cleanup(encodeTraceId);
                    }
                }

                if (taken == length && last)
                {
                    closeAttachment();
                }
            }

            return taken;
        }

        private int writeText(
            JsonStringView chars,
            int length)
        {
            int taken = 0;
            boolean progress = true;

            while (taken < length && progress && writable())
            {
                final int before = generator.consumed();

                generator.writeSegment(chars, taken, length);

                final int advanced = generator.consumed() - before;

                taken += advanced;

                if (taken < length)
                {
                    progress = advanced > 0 || encodeLength() > 0;
                    doEncodeFlush();
                    beginEncode();
                }
            }

            return taken;
        }

        private int emitTextChunk(
            JsonStringView chars,
            int length)
        {
            int taken = 0;
            boolean progress = true;

            while (taken < length && progress && writable())
            {
                final int need = chunkHead + chunkTail + RESPONSE_ESCAPE_EXPANSION;

                if (generator.remaining() >= need)
                {
                    final MutableDirectBufferEx buffer = encodeBuffer();
                    final int limit = encodeLimit();

                    dataChunkStart();

                    encodeShift = encodeLength();
                    generator.wrap(buffer, encodeBase + encodeShift, limit - chunkTail);

                    final int before = generator.consumed();

                    generator.writeSegment(chars, taken, length);

                    taken += generator.consumed() - before;
                    progress = generator.consumed() > before;

                    encodeShift += generator.length();
                    generator.wrap(buffer, encodeBase + encodeShift, limit);

                    dataChunkEnd();
                    flushEncodeSlot(encodeTraceId);
                    beginEncode();
                }
                else if (encodeLength() > 0)
                {
                    doEncodeFlush();
                    beginEncode();
                }
                else
                {
                    progress = false;
                    server.cleanup(encodeTraceId);
                }
            }

            return taken;
        }

        private MutableDirectBufferEx encodeBuffer()
        {
            return encodeSlot != NO_SLOT ? replyEncodePool.buffer(encodeSlot) : extBuffer;
        }

        private int encodeLimit()
        {
            return encodeSlot != NO_SLOT ? replyEncodePool.slotCapacity() : extBuffer.capacity();
        }

        private void measureFraming()
        {
            final JsonGeneratorEx probe = replyArgs();

            probe.wrap(extBuffer, 0, extBuffer.capacity());
            probe.reset();

            writeArgsOpen(probe, Integer.MAX_VALUE);

            chunkHead = probe.length();

            writeArgsClose(probe);

            chunkTail = probe.length() - chunkHead;

            probe.reset();

            writeAttachmentOpen(probe, VALUE_REDACTED, Integer.MAX_VALUE);

            attachmentHeadMax = probe.length();

            probe.reset();

            if (server.streaming)
            {
                writeBlockStart(probe, KIND_REASONING, Integer.MAX_VALUE, null, null);

                blockStartMax = probe.length();

                probe.reset();

                writeBlockStop(probe, Integer.MAX_VALUE);

                blockStopMax = probe.length();
                closeMax = blockStopMax;

                probe.reset();

                probe.writeStartObject().writeStartObject("message").writeStartArray("content").writeEnd();

                final int base = probe.length();

                writePreludeClose(probe);

                preludeCloseMax = probe.length() - base;

                probe.reset();

                probe.writeStartObject().write("type", EVENT_MESSAGE_DELTA).writeStartObject("delta")
                    .write("stop_reason", "").writeNull("stop_sequence");

                finishFraming = probe.length();

                probe.reset();

                probe.writeStartObject().write("type", EVENT_MESSAGE_STOP).writeEnd();

                stopMax = probe.length();
            }
            else
            {
                probe.writeStartObject().write("type", "thinking").writeKey("thinking");
                probe.writeStartEscaped();

                blockStartMax = probe.length();

                probe.reset();

                probe.writeStartObject().writeKey("thinking");
                probe.writeStartEscaped();

                final int escapedBase = probe.length();

                probe.writeEndEscaped().write("signature", "").writeEnd();

                closeMax = probe.length() - escapedBase;

                probe.reset();

                probe.writeStartObject();

                final int objectBase = probe.length();

                probe.writeKey(MEMBER_INPUT).writeStartObject().writeEnd().writeEnd();

                closeMax = Math.max(closeMax, probe.length() - objectBase);

                probe.reset();

                probe.writeStartObject();

                writePreludeClose(probe);

                preludeCloseMax = probe.length();

                probe.reset();

                probe.writeStartObject().writeStartArray("content").writeEnd().write("stop_reason", "")
                    .writeNull("stop_sequence");

                finishFraming = probe.length();
            }

            probe.reset();

            probe.writeStartObject();
            putUsage(probe, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

            usageMax = probe.length();

            probe.reset();
        }

        private int endNeed()
        {
            final boolean reasoned = kind == KIND_REASONING;
            final boolean opening = reasoned && server.streaming && !blockOpen();

            return switch (kind)
            {
            case KIND_TEXT, KIND_TOOL_CALL -> closeMax;
            case KIND_REASONING -> closeMax + (opening ? blockStartMax + preludeCloseMax : 0);
            default -> 0;
            };
        }

        private boolean roomEnd()
        {
            final int need = endNeed();

            return need == 0 || roomEvent(need);
        }

        private void doEncodeEnd()
        {
            switch (kind)
            {
            case KIND_TEXT, KIND_TOOL_CALL:
                closeBlock();
                break;
            case KIND_REASONING:
                closeReasoning();
                break;
            default:
                break;
            }

            kind = KIND_NONE;
        }

        private void closeReasoning()
        {
            if (server.streaming)
            {
                if (!blockOpen())
                {
                    openBlock(null, null);
                }

                closeBlock();
            }
            else
            {
                switch (reasoning)
                {
                case REASONING_TEXT:
                    generator.writeEndEscaped().write("signature", "").writeEnd();
                    blocksStopped++;
                    break;
                case REASONING_SIGNED, REASONING_REDACTED:
                    generator.writeEnd();
                    blocksStopped++;
                    break;
                default:
                    server.cleanup(encodeTraceId);
                    break;
                }
            }

            reasoning = REASONING_NONE;
        }

        private void onEncodeControlBegin(
            String name)
        {
            final boolean derived = MEMBER_USAGE.equals(name) || MEMBER_STOP_SEQUENCE.equals(name);

            if (controlMessage != NONE || envelope == ENVELOPE_TAIL)
            {
                controlMode = CONTROL_DROP;
            }
            else
            {
                controlMode = envelope == ENVELOPE_PRELUDE && !derived ? CONTROL_INLINE : CONTROL_HELD;
            }

            if (controlMode == CONTROL_INLINE)
            {
                generator.writeKey(name);
            }
            else if (controlMode == CONTROL_HELD)
            {
                onEncodeControlHeldBegin(name);
            }
        }

        private void onEncodeControlHeldBegin(
            String name)
        {
            if (!hold.begin(name))
            {
                controlMode = CONTROL_DROP;
            }
        }

        private void onEncodeControlHeld(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            if (!hold.put(payload, offset, length))
            {
                controlMode = CONTROL_DROP;
            }
        }

        private void onEncodeControlEnd()
        {
            if (controlMode == CONTROL_INLINE)
            {
                generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
            }
            else if (controlMode == CONTROL_HELD)
            {
                hold.end();
            }

            controlMode = CONTROL_DROP;
        }

        private int controlSize()
        {
            return hold.writeSize(0, ALL_MEMBERS);
        }

        private void writeControls()
        {
            hold.write(generator, NOT_DERIVED);
        }

        private MutableDirectBufferEx heldBuffer()
        {
            return decodeSlot != NO_SLOT ? replyPool.buffer(decodeSlot) : null;
        }

        private void cleanup(
            long traceId)
        {
            doAppAbort(traceId);
            doAppReset(traceId);
            cleanupDecodeSlot();
            cleanupEncodeSlot();
            cleanupHeld();
        }

        private void cleanupHeld()
        {
            hold.reset();
        }

        private void writeStopSequence()
        {
            final int index = hold.indexOf(MEMBER_STOP_SEQUENCE);

            generator.writeKey(MEMBER_STOP_SEQUENCE);

            if (index != NONE && hold.length(index) > 0)
            {
                generator.writeRaw(hold.buffer(), hold.offset(index), hold.length(index));
            }
            else
            {
                generator.writeNull();
            }
        }

        private void openBlock(
            String toolCallId,
            String toolName)
        {
            if (envelope == ENVELOPE_PRELUDE)
            {
                closePrelude();
            }

            if (server.streaming)
            {
                eventBegin(encodeLength(), EVENT_CODE_BLOCK_START);
                writeBlockStart(generator, kind, blocks++, toolCallId, toolName);
                eventEnd(encodeLength());
            }
            else
            {
                sectionBlock(toolCallId, toolName);
            }

            if (kind == KIND_TOOL_CALL)
            {
                toolCalls++;
            }
        }

        private void closeBlock()
        {
            if (server.streaming)
            {
                eventBegin(encodeLength(), EVENT_CODE_BLOCK_STOP);
                writeBlockStop(generator, blocks - 1);
                eventEnd(encodeLength());
            }
            else
            {
                sectionBlockEnd();
            }

            blocksStopped++;
        }

        private void doEncodeError(
            String type,
            String param,
            String message)
        {
            if (eventPhase != EVENT_NONE)
            {
                eventEnd(encodeLength());
            }

            if (server.streaming && envelope == ENVELOPE_PRELUDE)
            {
                closePrelude();
            }

            eventBegin(encodeLength(), EVENT_CODE_ERROR);
            generator.writeStartObject()
                .write("type", EVENT_ERROR)
                .writeStartObject("error")
                .write("type", type != null ? type : "api_error")
                .write("message", message != null ? message : "")
                .writeEnd()
                .writeEnd();
            eventEnd(encodeLength());
        }

        private void messageIdentity()
        {
            if (responseId != null)
            {
                generator.write("id", responseId);
            }

            generator.write("type", "message").write("role", "assistant");

            if (responseModel != null)
            {
                generator.write("model", responseModel);
            }
        }

        private void writeBlockStart(
            JsonGeneratorEx target,
            int section,
            int index,
            String toolCallId,
            String toolName)
        {
            target.writeStartObject()
                .write("type", EVENT_BLOCK_START)
                .writeKey("index").write(index)
                .writeStartObject("content_block");

            switch (section)
            {
            case KIND_TEXT:
                target.write("type", "text").write("text", "");
                break;
            case KIND_REASONING:
                target.write("type", "thinking").write("thinking", "").write("signature", "");
                break;
            default:
                target.write("type", "tool_use")
                    .write("id", toolCallId != null ? toolCallId : "")
                    .write("name", toolName != null ? toolName : "")
                    .writeStartObject("input").writeEnd();
                break;
            }

            target.writeEnd().writeEnd();
        }

        private void writeBlockStop(
            JsonGeneratorEx target,
            int index)
        {
            target.writeStartObject()
                .write("type", EVENT_BLOCK_STOP)
                .writeKey("index").write(index)
                .writeEnd();
        }

        private void openThinking()
        {
            if (envelope == ENVELOPE_PRELUDE)
            {
                closePrelude();
            }

            blocks++;
            generator.writeStartObject().write("type", "thinking");
        }

        private void openRedacted()
        {
            if (envelope == ENVELOPE_PRELUDE)
            {
                closePrelude();
            }

            blocks++;
            generator.writeStartObject().write("type", "redacted_thinking");
        }

        private void sectionBlock(
            String toolCallId,
            String toolName)
        {
            blocks++;

            if (kind == KIND_TEXT)
            {
                generator.writeStartObject().write("type", "text").writeKey("text");
                generator.writeStartEscaped();
            }
            else
            {
                generator.writeStartObject().write("type", BLOCK_TOOL_USE);

                if (toolCallId != null)
                {
                    generator.write("id", toolCallId);
                }

                if (toolName != null)
                {
                    generator.write("name", toolName);
                }
            }
        }

        private void sectionBlockEnd()
        {
            if (kind == KIND_TEXT)
            {
                generator.writeEndEscaped().writeEnd();
            }
            else
            {
                if (toolInputs == 0)
                {
                    generator.writeKey(MEMBER_INPUT).writeStartObject().writeEnd();
                }
                else
                {
                    generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
                }

                generator.writeEnd();
            }
        }

        private void dataChunkStart()
        {
            eventBegin(encodeLength(), EVENT_CODE_BLOCK_DELTA);
            writeDeltaOpen(generator, kind, blocks - 1);
        }

        private void dataChunkEnd()
        {
            writeDeltaClose(generator);
            eventEnd(encodeLength());
        }

        private void writeDeltaOpen(
            JsonGeneratorEx target,
            int section,
            int index)
        {
            writeDeltaMember(target, section, index);
            target.writeStartEscaped();
        }

        private void writeArgsOpen(
            JsonGeneratorEx target,
            int index)
        {
            writeDeltaMember(target, KIND_TOOL_CALL, index);
            target.writeSegment(QUOTE, 0, 1, Completion.INCOMPLETE);
        }

        private void writeDeltaMember(
            JsonGeneratorEx target,
            int section,
            int index)
        {
            target.writeStartObject()
                .write("type", EVENT_BLOCK_DELTA)
                .writeKey("index").write(index)
                .writeStartObject("delta");

            switch (section)
            {
            case KIND_TEXT:
                target.write("type", "text_delta").writeKey("text");
                break;
            case KIND_REASONING:
                target.write("type", "thinking_delta").writeKey("thinking");
                break;
            default:
                target.write("type", "input_json_delta").writeKey("partial_json");
                break;
            }
        }

        private void writeDeltaClose(
            JsonGeneratorEx target)
        {
            target.writeEndEscaped().writeEnd().writeEnd();
        }

        private void writeArgsClose(
            JsonGeneratorEx target)
        {
            target.writeSegment(QUOTE, 0, 1, Completion.COMPLETE).writeEnd().writeEnd();
        }

        private void writeAttachmentOpen(
            JsonGeneratorEx target,
            int member,
            int index)
        {
            target.writeStartObject();

            if (member == VALUE_REDACTED)
            {
                target.write("type", EVENT_BLOCK_START)
                    .writeKey("index").write(index)
                    .writeStartObject("content_block")
                    .write("type", "redacted_thinking")
                    .writeKey(MEMBER_DATA_FIELD);
            }
            else
            {
                target.write("type", EVENT_BLOCK_DELTA)
                    .writeKey("index").write(index)
                    .writeStartObject("delta")
                    .write("type", "signature_delta")
                    .writeKey(MEMBER_SIGNATURE_FIELD);
            }

            target.writeStartEscaped();
        }

        private void putUsage(
            JsonGeneratorEx target,
            int inputTokens,
            int cacheWriteTokens,
            int cacheReadTokens,
            int outputTokens)
        {
            final int index = hold.indexOf(MEMBER_USAGE);

            if (index != NONE && hold.length(index) > 0)
            {
                target.writeKey(MEMBER_USAGE);
                target.writeRaw(hold.buffer(), hold.offset(index), hold.length(index));
            }
            else
            {
                target.writeStartObject(MEMBER_USAGE)
                    .writeKey("input_tokens").write(inputTokens != NONE ? inputTokens : 0);

                if (cacheWriteTokens != NONE)
                {
                    target.writeKey("cache_creation_input_tokens").write(cacheWriteTokens);
                }

                if (cacheReadTokens != NONE)
                {
                    target.writeKey("cache_read_input_tokens").write(cacheReadTokens);
                }

                target.writeKey("output_tokens").write(outputTokens != NONE ? outputTokens : 0)
                    .writeEnd();
            }
        }

        private String stopReason(
            String finishReason)
        {
            String reason = toolCalls > 0 ? "tool_use" : "end_turn";

            if (finishReason != null)
            {
                reason = switch (finishReason)
                {
                case "stop" -> "end_turn";
                case "length" -> "max_tokens";
                case "tool-call" -> "tool_use";
                case "content-filter" -> "refusal";
                default -> NAME.equals(responseDialect) && finishReason.startsWith(VENDOR_PREFIX)
                    ? finishReason.substring(VENDOR_PREFIX.length())
                    : reason;
                };
            }

            return reason;
        }

        private static int kindOf(
            LlmSectionType type)
        {
            return switch (type)
            {
            case ASSISTANT_TEXT, ASSISTANT_REFUSAL -> KIND_TEXT;
            case ASSISTANT_REASONING -> KIND_REASONING;
            case TOOL_CALL -> KIND_TOOL_CALL;
            default -> KIND_SKIPPED;
            };
        }

        private int encodeLength()
        {
            return encodeShift + generator.length();
        }

        private void eventBegin(
            int position,
            int code)
        {
            this.eventPhase = EVENT_OPEN;
            this.eventStart = position;
            this.eventCode = code;
        }

        private void eventEnd(
            int position)
        {
            if (eventPhase != EVENT_NONE)
            {
                emitEvent(position, true);
                eventPhase = EVENT_NONE;
                eventStart = NO_EVENT;
                generator.reset();
            }
        }

        private void emitEvent(
            int position,
            boolean fin)
        {
            final int length = position - eventStart;

            if (length > 0 || fin && eventPhase == EVENT_STARTED)
            {
                final int init = eventPhase == EVENT_STARTED ? Flags.NONE : Flags.INIT;

                writeEntry(encodeBase + eventStart, length, fin ? Flags.fin(init) : init,
                    eventPhase == EVENT_STARTED ? 0 : eventCode);

                eventPhase = EVENT_STARTED;
            }
        }

        private void writePreludeClose(
            JsonGeneratorEx target)
        {
            if (server.streaming)
            {
                target.writeNull("stop_reason");
                target.writeNull("stop_sequence");
                target.writeStartObject("usage")
                    .writeKey("input_tokens").write(0)
                    .writeKey("output_tokens").write(0)
                    .writeEnd()
                    .writeEnd()
                    .writeEnd();
            }
            else
            {
                target.writeStartArray("content");
            }
        }

        private void closePrelude()
        {
            writePreludeClose(generator);

            if (server.streaming)
            {
                eventEnd(encodeLength());
            }

            envelope = ENVELOPE_CONTENT;
        }

        private void openTail()
        {
            if (server.streaming)
            {
                eventBegin(encodeLength(), EVENT_CODE_MESSAGE_DELTA);
                generator.writeStartObject()
                    .write("type", EVENT_MESSAGE_DELTA)
                    .writeStartObject("delta");
            }
            else
            {
                generator.writeEnd();
            }

            envelope = ENVELOPE_TAIL;
        }
    }


    private final class LlmAnthropicRejectHandler
    {
        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final String status;
        private final DirectBufferEx body;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;
        private boolean began;
        private int bodySent;

        private LlmAnthropicRejectHandler(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            String status,
            String body)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.status = status;
            this.body = asBuffer(body);
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
            case WindowFW.TYPE_ID:
                onNetWindow(windowRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            state = StreamState.openInitial(state);
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            initialMax = decodeMax;

            doNetWindow(traceId);
            doNetBegin(traceId, authorization);
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();

            initialSeq = data.sequence() + data.reserved();
            initialAck = initialSeq;

            doNetWindow(traceId);
        }

        private void onNetEnd(
            EndFW end)
        {
            state = StreamState.closeInitial(state);
            initialSeq = end.sequence();
            initialAck = initialSeq;
        }

        private void onNetAbort(
            AbortFW abort)
        {
            state = StreamState.closeInitial(state);
            initialSeq = abort.sequence();
            initialAck = initialSeq;
        }

        private void onNetWindow(
            WindowFW window)
        {
            state = StreamState.openReply(state);
            replyAck = window.acknowledge();
            replyMax = window.maximum();

            flushBody(window.traceId(), window.authorization());
        }

        private void doNetWindow(
            long traceId)
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .state(state)
                .maximum(initialMax)
                .traceId(traceId)
                .budgetId(0L)
                .padding(0)
                .build();

            network.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doNetBegin(
            long traceId,
            long authorization)
        {
            state = StreamState.openingReply(state);

            final HttpBeginExFW httpBeginEx = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HEADER_STATUS).value(status))
                .headersItem(h -> h.name(HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .build();

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(0L)
                .extension(httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof())
                .build();

            network.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
            began = true;
        }

        private void flushBody(
            long traceId,
            long authorization)
        {
            if (!began || bodySent >= body.capacity())
            {
                return;
            }

            while (bodySent < body.capacity())
            {
                final long available = replyMax - (replySeq - replyAck);
                if (available <= 0)
                {
                    break;
                }

                final int remaining = body.capacity() - bodySent;
                final int length = (int) Math.min(remaining, available);
                final int initFlags = bodySent == 0 ? Flags.INIT : Flags.NONE;
                final int flags = bodySent + length == body.capacity() ? Flags.fin(initFlags) : initFlags;

                final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .state(state)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .flags(flags)
                    .budgetId(0L)
                    .reserved(length)
                    .payload(body, bodySent, length)
                    .extension(EMPTY_OCTETS)
                    .build();

                network.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
                replySeq += length;
                bodySent += length;
            }

            if (bodySent >= body.capacity())
            {
                doNetEnd(traceId, authorization);
            }
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            state = StreamState.closeReply(state);

            final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .extension(EMPTY_OCTETS)
                .build();

            network.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
        }
    }
}
