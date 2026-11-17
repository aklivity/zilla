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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmGeminiDialectFactorySpi.NAME;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

public final class LlmGeminiServerFactory implements LlmDialectHandler
{
    private static final String VENDOR_PREFIX = NAME + ":";
    private static final String HTTP_TYPE_NAME = "http";
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_PATH = ":path";
    private static final String HEADER_STATUS = ":status";
    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_API_KEY = "x-goog-api-key";
    private static final String METHOD_POST = "POST";
    private static final Pattern PATH_OPERATION =
        Pattern.compile("^/(v1(?:beta)?)/models/((?:[^/?%]|%[0-9A-Fa-f]{2})+):" +
            "(generateContent|streamGenerateContent)(?:\\?(.*))?$");
    private static final String OPERATION_STREAM = "streamGenerateContent";
    private static final String PATH_PREFIX_V1 = "/v1/models/";
    private static final String PATH_PREFIX_V1BETA = "/v1beta/models/";
    private static final String SUFFIX_GENERATE = ":generateContent";
    private static final String SUFFIX_STREAM = ":streamGenerateContent";
    private static final String QUERY_ALT_SSE = "alt=sse";
    private static final String STATUS_OK = "200";
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_FORBIDDEN = 403;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_UNSUPPORTED_MEDIA_TYPE = 415;
    private static final int STATUS_INTERNAL_SERVER_ERROR = 500;
    private static final int NO_MAX_OUTPUT_TOKENS = -1;
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream;space=1";
    private static final Set<String> STATUSES = Set.of("INVALID_ARGUMENT", "FAILED_PRECONDITION", "OUT_OF_RANGE",
        "UNAUTHENTICATED", "PERMISSION_DENIED", "NOT_FOUND", "ABORTED", "ALREADY_EXISTS", "RESOURCE_EXHAUSTED", "CANCELLED",
        "DATA_LOSS", "UNKNOWN", "INTERNAL", "UNIMPLEMENTED", "UNAVAILABLE", "DEADLINE_EXCEEDED");

    private static final String MESSAGE_MODEL_NOT_FOUND =
        "models/%s is not found for API version %s, or is not supported for %s.";
    private static final String MESSAGE_FORBIDDEN = "Method doesn't allow unregistered callers (callers without " +
        "established identity). Please use API Key or other form of API consumer identity to call this API.";
    private static final String MESSAGE_API_KEY_INVALID = "API key not valid. Please pass a valid API key.";
    private static final String REASON_API_KEY_INVALID = "API_KEY_INVALID";
    private static final String MESSAGE_PARSE_ERROR = "Invalid JSON payload received.";
    private static final String MESSAGE_CONTENT_SIZE = "content too large to decode before role";
    private static final String MESSAGE_ROLE_INVALID = "Role '%s' is not supported. Please use a valid role: user, model.";
    private static final String MESSAGE_BLOCK_INVALID = "Invalid content part";
    private static final String MESSAGE_TOOL_UNSUPPORTED = "Unsupported tool";
    private static final String MESSAGE_ATTRIBUTE_TOO_LARGE = "Invalid request section";
    private static final String MESSAGE_MEDIA_TYPE = "inlineData mimeType must precede data larger than the decode window";
    private static final String MEMBER_FUNCTION_DECLARATIONS = "functionDeclarations";
    private static final String MEMBER_FUNCTION_CALL = "functionCall";
    private static final String MEMBER_FUNCTION_RESPONSE = "functionResponse";
    private static final String SERVER_TOOL_NAME = "code_execution";
    private static final String MEMBER_THOUGHT = "thought";
    private static final String MEMBER_THOUGHT_SIGNATURE = "thoughtSignature";
    private static final String MEMBER_THOUGHT_SIGNATURE_SNAKE = "thought_signature";
    private static final int CALLS_MAX = 16;
    private static final String TOOL_CALL_ID_PREFIX = "call_";
    private static final int BLOCK_BUFFER_MAX = 4096;
    private static final Map<String, StringEventPositions> PARSER_CONFIG =
        Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED);

    private static final int REPLY_PADDING_FRACTION = 8;
    private static final int RESPONSE_ESCAPE_EXPANSION = 6;
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_ARGUMENTS = "arguments";
    private static final String MEMBER_ARGS = "args";
    private static final String MEMBER_WIRE_ID = "gemini:id";
    private static final String MEMBER_EXECUTABLE_CODE = "executableCode";
    private static final String MEMBER_SIGNATURE = "gemini:thoughtSignature";
    private static final String MEMBER_SIGNATURE_WIRE = "thoughtSignature";
    private static final int THOUGHT_MEMBER = ",\"thought\":true".length();
    private static final int SIGNATURE_MEMBER = (",\"" + MEMBER_SIGNATURE_WIRE + "\":\"\"").length();
    private static final int ARGS_EMPTY = (",\"" + MEMBER_ARGS + "\":{}").length();
    private static final int ID_MEMBER = ",\"id\":\"\"".length();
    private static final int ARGS_HEADROOM = 16;
    private static final int ARGS_IDLE = 0;
    private static final int ARGS_FULL = 1;
    private static final int ARGS_DONE = 2;
    private static final int ARGS_HELD = 3;
    private static final int ARGS_WAIT = 4;
    private static final int REPLY_TAIL_MAX = 64;
    private static final int REPLY_FRAME_OVERHEAD = 64;
    private static final int ENTRY_HEADER = 21;
    private static final int ENTRY_FLAGS = 4;
    private static final int ENTRY_TRACE = 5;
    private static final int ENTRY_AUTHORIZATION = 13;
    private static final int REPLY_FLAG_END = 0x40;
    private static final int VALUE_ARGS = 0;
    private static final int VALUE_MEMBER = 1;
    private static final int VALUE_INLINE = 2;
    private static final int REQUEST_CANONICAL_GROWTH = 4;
    private static final int REQUEST_ENCODE_SLACK = 512;
    private static final int REQUEST_RECORD_HEADER = 28;

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);
    private static final DirectBufferEx EMPTY_BUFFER = new UnsafeBufferEx(new byte[0]);

    private static final DirectBufferEx STREAM_SECTION =
        new UnsafeBufferEx("{\"stream\":true}".getBytes(UTF_8));
    private static final DirectBufferEx STREAM_OPTIONS_SECTION =
        new UnsafeBufferEx("{\"stream_options\":{\"include_usage\":true}}".getBytes(UTF_8));
    private static final DirectBufferEx CONTROL_CLOSE = new UnsafeBufferEx("}".getBytes(UTF_8));
    private static final DirectBufferEx OBJECT_EMPTY = new UnsafeBufferEx("{}".getBytes(UTF_8));
    private static final DirectBufferEx CONTROL_BEGIN = new UnsafeBufferEx("{".getBytes(UTF_8));
    private static final DirectBufferEx CONTROL_CONTINUE = new UnsafeBufferEx(",".getBytes(UTF_8));
    private static final DirectBufferEx RESPONSE_FORMAT_SCHEMA_OPEN = new UnsafeBufferEx(
        "{\"type\":\"json_schema\",\"json_schema\":{\"name\":\"response\",\"schema\":".getBytes(UTF_8));
    private static final DirectBufferEx RESPONSE_FORMAT_SCHEMA_CLOSE = new UnsafeBufferEx("}}".getBytes(UTF_8));
    private static final DirectBufferEx RESPONSE_FORMAT_OBJECT =
        new UnsafeBufferEx("{\"type\":\"json_object\"}".getBytes(UTF_8));

    private static final int MIN_WINDOW = 64;
    private static final int CONTROL_GROWTH = 4;
    private static final int FRAMING_RESERVE = 16;
    private static final int TEXT_ESCAPE_EXPANSION = 6;
    private static final int CONTROL_OPEN_CAPACITY = 8;
    private static final int LEVEL_CONTENT_MEMBER = 0;
    private static final int LEVEL_PART_MEMBER = 2;

    private static final String SHARED_PREFIX = "x:";

    private static final String MEMBER_GENERATION_CONFIG = "generationConfig";
    private static final String MEMBER_THINKING_CONFIG = "thinkingConfig";
    private static final String MEMBER_THINKING_CONFIG_SNAKE = "thinking_config";
    private static final String MEMBER_TOOL_CONFIG = "toolConfig";
    private static final String MEMBER_TOOL_CONFIG_SNAKE = "tool_config";
    private static final String MEMBER_SERVICE_TIER = "serviceTier";
    private static final String MEMBER_SERVICE_TIER_SNAKE = "service_tier";
    private static final String MEMBER_GENERATION_CONFIG_SNAKE = "generation_config";
    private static final String MEMBER_MAX_OUTPUT_TOKENS = "maxOutputTokens";
    private static final String MEMBER_MAX_OUTPUT_TOKENS_SNAKE = "max_output_tokens";
    private static final String MEMBER_RESPONSE_FORMAT = "responseFormat";
    private static final String MEMBER_RESPONSE_FORMAT_SNAKE = "response_format";
    private static final String MEMBER_RESPONSE_MIME_TYPE = "responseMimeType";
    private static final String MEMBER_RESPONSE_MIME_TYPE_SNAKE = "response_mime_type";
    private static final String MEMBER_RESPONSE_SCHEMA = "responseSchema";
    private static final String MEMBER_RESPONSE_SCHEMA_SNAKE = "response_schema";
    private static final String MEMBER_RESPONSE_JSON_SCHEMA = "responseJsonSchema";
    private static final String MEMBER_RESPONSE_JSON_SCHEMA_SNAKE = "response_json_schema";
    private static final String SHARED_RESPONSE_FORMAT = "response_format";
    private static final String MIME_JSON = "application/json";
    private static final String MIME_JSON_ENUM = "APPLICATION_JSON";

    private static final int FORMAT_NONE = 0;
    private static final int FORMAT_PENDING = 1;
    private static final int FORMAT_RESIDUE = 2;
    private static final int FORMAT_LIFTED = 3;
    private static final int MIME_MAX = 64;
    private static final String MEMBER_SYSTEM_INSTRUCTION = "systemInstruction";
    private static final String MEMBER_SYSTEM_INSTRUCTION_SNAKE = "system_instruction";

    private static final String ROLE_USER = "user";
    private static final String ROLE_MODEL = "model";

    private static final int CONTEXT_MESSAGE = 0;
    private static final int CONTEXT_SYSTEM = 1;

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
    private final LlmReplyFrameFW.Builder replyFrameRW = new LlmReplyFrameFW.Builder();
    private final LlmReplyFrameFW replyFrameRO = new LlmReplyFrameFW();
    private final LlmMemberKeys memberKeys = new LlmMemberKeys(NAME);
    private final Array32FW<HttpHeaderFW> eventHeadersRO = new Array32FW<>(new HttpHeaderFW());
    private final LlmResetExFW llmResetExRO = new LlmResetExFW();
    private final LlmEndExFW llmEndExRO = new LlmEndExFW();
    private final LlmAbortExFW llmAbortExRO = new LlmAbortExFW();

    private final EngineContext context;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx transformBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final BufferPool replyEncodePool;
    private final BufferPool replyPool;
    private final int replyPadding;
    private final Long2ObjectHashMap<LlmBindingConfig> bindings;
    private final int decodeMax;
    private final int llmTypeId;
    private final int httpTypeId;

    private final LlmGeminiServerDecoder decodeStart = this::decodeStart;
    private final LlmGeminiServerDecoder decodeRootStart = this::decodeRootStart;
    private final LlmGeminiServerDecoder decodeRoot = this::decodeRoot;
    private final LlmGeminiServerDecoder decodeRootControl = this::decodeRootControl;
    private final LlmGeminiServerDecoder decodeContentsStart = this::decodeContentsStart;
    private final LlmGeminiServerDecoder decodeContents = this::decodeContents;
    private final LlmGeminiServerDecoder decodeContent = this::decodeContent;
    private final LlmGeminiServerDecoder decodeContentStart = this::decodeContentStart;
    private final LlmGeminiServerDecoder decodeRole = this::decodeRole;
    private final LlmGeminiServerDecoder decodeSystemStart = this::decodeSystemStart;
    private final LlmGeminiServerDecoder decodeSystem = this::decodeSystem;
    private final LlmGeminiServerDecoder decodePartsStart = this::decodePartsStart;
    private final LlmGeminiServerDecoder decodeParts = this::decodeParts;
    private final LlmGeminiServerDecoder decodePart = this::decodePart;
    private final LlmGeminiServerDecoder decodeToolsStart = this::decodeToolsStart;
    private final LlmGeminiServerDecoder decodeTools = this::decodeTools;
    private final LlmGeminiServerDecoder decodeToolKey = this::decodeToolKey;
    private final LlmGeminiServerDecoder decodeDeclarationsStart = this::decodeDeclarationsStart;
    private final LlmGeminiServerDecoder decodeDeclarations = this::decodeDeclarations;
    private final LlmGeminiServerDecoder decodeTool = this::decodeTool;
    private final LlmGeminiServerDecoder decodeToolStart = this::decodeToolStart;
    private final LlmGeminiServerDecoder decodeDeclarationStart = this::decodeDeclarationStart;
    private final LlmGeminiServerDecoder decodeSkipStart = this::decodeSkipStart;
    private final LlmGeminiServerDecoder decodePartCaptureStart = this::decodePartCaptureStart;
    private final LlmGeminiServerDecoder decodePartTextKey = this::decodePartTextKey;
    private final LlmGeminiServerDecoder decodeBlockKey = this::decodeBlockKey;
    private final LlmGeminiServerDecoder decodeBlockFunction = this::decodeBlockFunction;
    private final LlmGeminiServerDecoder decodeBlockFunctionKey = this::decodeBlockFunctionKey;
    private final LlmGeminiServerDecoder decodeBlockDrop = this::decodeBlockDrop;
    private final LlmGeminiServerDecoder decodeBlockEscape = this::decodeBlockEscape;
    private final LlmGeminiServerDecoder decodeBlockId = this::decodeBlockId;
    private final LlmGeminiServerDecoder decodeBlockOptional = this::decodeBlockOptional;
    private final LlmGeminiServerDecoder decodeBlockData = this::decodeBlockData;
    private final LlmGeminiServerDecoder decodeBlockControl = this::decodeBlockControl;
    private final LlmGeminiServerDecoder decodeBlockValue = this::decodeBlockValue;
    private final LlmGeminiServerDecoder decodeBlockCapture = this::decodeBlockCapture;
    private final LlmGeminiServerDecoder decodeBlockTokens = this::decodeBlockTokens;
    private final LlmGeminiServerDecoder decodeBlockSchema = this::decodeBlockSchema;
    private final LlmGeminiServerDecoder decodeBlockResponse = this::decodeBlockResponse;
    private final LlmGeminiServerDecoder decodeBlockResponseKey = this::decodeBlockResponseKey;
    private final LlmGeminiServerDecoder decodeBlockCopy = this::decodeBlockCopy;
    private final LlmGeminiBlockHandler blockKey = this::onBlockKey;
    private final LlmGeminiBlockHandler blockFunction = this::onBlockFunction;
    private final LlmGeminiBlockHandler blockFunctionKey = this::onBlockFunctionKey;
    private final LlmGeminiBlockHandler blockDrop = this::onBlockDrop;
    private final LlmGeminiBlockHandler blockEscape = this::onBlockEscape;
    private final LlmGeminiBlockHandler blockId = this::onBlockId;
    private final LlmGeminiBlockHandler blockOptional = this::onBlockOptional;
    private final LlmGeminiBlockHandler blockData = this::onBlockData;
    private final LlmGeminiBlockHandler blockControl = this::onBlockControl;
    private final LlmGeminiBlockHandler blockValue = this::onBlockMember;
    private final LlmGeminiBlockHandler blockCapture = this::onBlockCapture;
    private final LlmGeminiBlockHandler blockTokens = this::onBlockTokens;
    private final LlmGeminiBlockHandler blockSchema = this::onBlockSchema;
    private final LlmGeminiBlockHandler blockResponse = this::onBlockResponse;
    private final LlmGeminiBlockHandler blockResponseKey = this::onBlockResponseKey;
    private final LlmGeminiBlockHandler blockCopy = this::onBlockCopy;
    private final LlmGeminiServerDecoder decodePartTextValue = this::decodePartTextValue;
    private final LlmGeminiServerDecoder decodePartTextPiece = this::decodePartTextPiece;
    private final LlmGeminiServerDecoder decodePartTextCopy = this::decodePartTextCopy;
    private final LlmGeminiServerDecoder decodePartTextSkip = this::decodePartTextSkip;
    private final LlmGeminiServerDecoder decodePartProbeStart = this::decodePartProbeStart;
    private final LlmGeminiServerDecoder decodeControlValue = this::decodeControlValue;
    private final LlmGeminiServerDecoder decodeControlSeparator = this::decodeControlSeparator;
    private final LlmGeminiServerDecoder decodeConfigStart = this::decodeConfigStart;
    private final LlmGeminiServerDecoder decodeConfigFirst = this::decodeConfigFirst;
    private final LlmGeminiServerDecoder decodeConfigOpen = this::decodeConfigOpen;
    private final LlmGeminiServerDecoder decodeConfigClosed = this::decodeConfigClosed;
    private final LlmGeminiServerDecoder decodeConfigMime = this::decodeConfigMime;
    private final LlmGeminiServerDecoder decodeConfigSchema = this::decodeConfigSchema;
    private final LlmGeminiServerDecoder decodeConfigConverted = this::decodeConfigConverted;
    private final LlmGeminiServerDecoder decodeSkip = this::decodeSkip;
    private final LlmGeminiServerDecoder decodeEnd = this::decodeEnd;
    private final LlmGeminiServerDecoder decodeIgnore = this::decodeIgnore;

    private final UnsafeBufferEx pieceBuffer;
    private final SegmentTransferTarget pieceTarget = new SegmentTransferTarget();
    private final JsonStringBody body = new JsonStringBody();

    public LlmGeminiServerFactory(
        EngineContext context)
    {
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.replyEncodePool = context.bufferPool().duplicate();
        this.replyPool = context.bufferPool().duplicate();
        this.replyPadding = replyPool.slotCapacity() / REPLY_PADDING_FRACTION;
        this.decodeMax = decodePool.slotCapacity();
        this.transformBuffer = new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]);
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
        return LlmHeaderBytes.equalToIgnoreCase(headers.get(HEADER_METHOD, 0), METHOD_POST) &&
            operates(headers.get(HEADER_PATH, 0));
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
            final Matcher operation = operation(header(headers, HEADER_PATH));

            if (operation == null)
            {
                newStream = new LlmGeminiRejectHandler(network, originId, routedId, initialId,
                    Integer.toString(STATUS_NOT_FOUND), errorBody(STATUS_NOT_FOUND, null, null))::onNetMessage;
            }
            else if (!LlmHeaderBytes.isMediaType(headers.get(HEADER_CONTENT_TYPE, 0), CONTENT_TYPE_JSON))
            {
                newStream = new LlmGeminiRejectHandler(network, originId, routedId, initialId,
                    Integer.toString(STATUS_UNSUPPORTED_MEDIA_TYPE),
                    errorBody(STATUS_UNSUPPORTED_MEDIA_TYPE, null, null))::onNetMessage;
            }
            else
            {
                final LlmAuthorizationResult authResult = binding.authorize(
                    begin.traceId(), routedId, initialId, authorization, headers, HEADER_API_KEY);

                if (!authResult.authorized())
                {
                    final boolean credentialed = header(headers, HEADER_API_KEY) != null;

                    newStream = new LlmGeminiRejectHandler(network, originId, routedId, initialId,
                        Integer.toString(credentialed ? STATUS_BAD_REQUEST : STATUS_FORBIDDEN),
                        credentialed ? apiKeyInvalidBody() : errorBody(STATUS_FORBIDDEN, null, MESSAGE_FORBIDDEN))::onNetMessage;
                }
                else
                {
                    final String model = model(operation.group(2));
                    final LlmRouteConfig route = binding.resolve(authResult.authorization(), NAME, model);

                    if (route != null)
                    {
                        newStream = new LlmGeminiServer(
                                network,
                                originId,
                                routedId,
                                initialId,
                                route.id,
                                authResult.authorization(),
                                contentType,
                                model,
                                operation.group(3),
                                operation.group(4),
                                authResult.deauthorize())::onNetMessage;
                    }
                    else
                    {
                        final boolean authorizedForRoutes = binding.resolve(authResult.authorization()) != null;
                        authResult.deauthorize().run();

                        if (authorizedForRoutes)
                        {
                            newStream = new LlmGeminiRejectHandler(network, originId, routedId, initialId,
                                Integer.toString(STATUS_NOT_FOUND),
                                errorBody(STATUS_NOT_FOUND, null, MESSAGE_MODEL_NOT_FOUND.formatted(
                                    model, operation.group(1), operation.group(3))))::onNetMessage;
                        }
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

    static boolean operates(
        DirectBufferEx path)
    {
        boolean matched = false;

        if (path != null)
        {
            final int length = path.capacity();

            int pathEnd = 0;

            while (pathEnd < length && path.getByte(pathEnd) != '?')
            {
                pathEnd++;
            }

            final int modelStart = modelStart(path, pathEnd);
            final int operationLength = operationLength(path, pathEnd);
            final int modelEnd = pathEnd - operationLength;

            matched = modelStart != -1 &&
                operationLength != 0 &&
                modelEnd > modelStart &&
                modelEncoded(path, modelStart, modelEnd) &&
                singleLine(path, pathEnd, length);
        }

        return matched;
    }

    private static int modelStart(
        DirectBufferEx path,
        int pathEnd)
    {
        int start = -1;

        if (LlmHeaderBytes.startsWith(path, 0, pathEnd, PATH_PREFIX_V1))
        {
            start = PATH_PREFIX_V1.length();
        }
        else if (LlmHeaderBytes.startsWith(path, 0, pathEnd, PATH_PREFIX_V1BETA))
        {
            start = PATH_PREFIX_V1BETA.length();
        }

        return start;
    }

    private static int operationLength(
        DirectBufferEx path,
        int pathEnd)
    {
        int length = 0;

        if (LlmHeaderBytes.endsWith(path, pathEnd, SUFFIX_GENERATE))
        {
            length = SUFFIX_GENERATE.length();
        }
        else if (LlmHeaderBytes.endsWith(path, pathEnd, SUFFIX_STREAM))
        {
            length = SUFFIX_STREAM.length();
        }

        return length;
    }

    private static boolean modelEncoded(
        DirectBufferEx path,
        int from,
        int to)
    {
        boolean encoded = true;

        for (int position = from; encoded && position < to; position++)
        {
            final byte b = path.getByte(position);

            if (b == '%')
            {
                encoded = position + 2 < to &&
                    hexadecimal(path.getByte(position + 1)) &&
                    hexadecimal(path.getByte(position + 2));
                position += 2;
            }
            else
            {
                encoded = b != '/';
            }
        }

        return encoded;
    }

    private static boolean hexadecimal(
        byte b)
    {
        return b >= '0' && b <= '9' || b >= 'a' && b <= 'f' || b >= 'A' && b <= 'F';
    }

    private static boolean singleLine(
        DirectBufferEx path,
        int from,
        int to)
    {
        boolean single = true;

        for (int position = from; single && position < to; position++)
        {
            final byte b = path.getByte(position);

            single = b != '\n' && b != '\r';
        }

        return single;
    }

    static Matcher operation(
        String path)
    {
        final Matcher matcher = path != null ? PATH_OPERATION.matcher(path) : null;

        return matcher != null && matcher.matches() ? matcher : null;
    }

    private static String model(
        String encoded)
    {
        return encoded.indexOf('%') != -1 ? URLDecoder.decode(encoded.replace("+", "%2B"), UTF_8) : encoded;
    }

    private static String errorBody(
        int status,
        String type,
        String message)
    {
        return Json.createObjectBuilder()
            .add("error", Json.createObjectBuilder()
                .add("code", status)
                .add("message", message != null ? message : reason(status))
                .add("status", type != null && STATUSES.contains(type) ? type : errorStatus(status)))
            .build()
            .toString();
    }

    private static String apiKeyInvalidBody()
    {
        return Json.createObjectBuilder()
            .add("error", Json.createObjectBuilder()
                .add("code", STATUS_BAD_REQUEST)
                .add("message", MESSAGE_API_KEY_INVALID)
                .add("status", errorStatus(STATUS_BAD_REQUEST))
                .add("details", Json.createArrayBuilder()
                    .add(Json.createObjectBuilder()
                        .add("@type", "type.googleapis.com/google.rpc.ErrorInfo")
                        .add("reason", REASON_API_KEY_INVALID)
                        .add("domain", "googleapis.com")
                        .add("metadata", Json.createObjectBuilder()
                            .add("service", "generativelanguage.googleapis.com")))
                    .add(Json.createObjectBuilder()
                        .add("@type", "type.googleapis.com/google.rpc.LocalizedMessage")
                        .add("locale", "en-US")
                        .add("message", MESSAGE_API_KEY_INVALID))))
            .build()
            .toString();
    }

    private static String errorStatus(
        int status)
    {
        return switch (status)
        {
        case 401 -> "UNAUTHENTICATED";
        case 403 -> "PERMISSION_DENIED";
        case 404 -> "NOT_FOUND";
        case 409 -> "ABORTED";
        case 429 -> "RESOURCE_EXHAUSTED";
        case 499 -> "CANCELLED";
        case 500 -> "INTERNAL";
        case 501 -> "UNIMPLEMENTED";
        case 503 -> "UNAVAILABLE";
        case 504 -> "DEADLINE_EXCEEDED";
        default -> status >= 500 ? "INTERNAL" : "INVALID_ARGUMENT";
        };
    }

    @FunctionalInterface
    private interface LlmGeminiReplyDecoder
    {
        int decode(
            LlmGeminiStream stream,
            DirectBufferEx buffer,
            int offset,
            int limit);
    }

    private final LlmGeminiReplyDecoder decodeReplyNext = this::decodeReplyNext;
    private final LlmGeminiReplyDecoder decodeReplyResume = this::decodeReplyResume;
    private final LlmGeminiReplyDecoder decodeReplyFinishChunk = this::decodeReplyFinishChunk;
    private final LlmGeminiReplyDecoder decodeReplyFinishBody = this::decodeReplyFinishBody;
    private final LlmGeminiReplyDecoder decodeReplyFinishEnd = this::decodeReplyFinishEnd;
    private final LlmGeminiReplyDecoder decodeReplyTextHeld = this::decodeReplyTextHeld;
    private final LlmGeminiReplyDecoder decodeReplyTextRoot = this::decodeReplyTextRoot;
    private final LlmGeminiReplyDecoder decodeReplyTextObject = this::decodeReplyTextObject;
    private final LlmGeminiReplyDecoder decodeReplyTextKey = this::decodeReplyTextKey;
    private final LlmGeminiReplyDecoder decodeReplyTextValue = this::decodeReplyTextValue;
    private final LlmGeminiReplyDecoder decodeReplyTextContinue = this::decodeReplyTextContinue;
    private final LlmGeminiReplyDecoder decodeReplyTextSkip = this::decodeReplyTextSkip;
    private final LlmGeminiReplyDecoder decodeReplyArgs = this::decodeReplyArgs;
    private final LlmGeminiReplyDecoder decodeReplyArgsHeld = this::decodeReplyArgsHeld;
    private final LlmGeminiReplyDecoder decodeReplyControlHeld = this::decodeReplyControlHeld;
    private final LlmGeminiReplyDecoder decodeReplyControlValue = this::decodeReplyControlValue;
    private final LlmGeminiReplyDecoder decodeReplyControlJudge = this::decodeReplyControlJudge;
    private final LlmGeminiReplyDecoder decodeReplyTextDone = this::decodeReplyTextDone;
    private final LlmGeminiReplyDecoder decodeReplyEnd = this::decodeReplyEnd;
    private final LlmGeminiReplyDecoder decodeReplySignature = this::decodeReplySignature;
    private final LlmGeminiReplyDecoder decodeReplyId = this::decodeReplyId;

    private int decodeReplyResume(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return offset;
    }

    private int decodeReplyFinishChunk(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.finishChunk();

        return offset;
    }

    private int decodeReplyFinishBody(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.finishBody();

        return offset;
    }

    private int decodeReplyFinishEnd(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return offset;
    }

    private int decodeReplyNext(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return offset;
    }

    private int decodeReplyTextRoot(
        LlmGeminiStream stream,
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
        LlmGeminiStream stream,
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
        LlmGeminiStream stream,
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
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyTextPiece(stream, buffer, offset, decodeReplyTextValue, true, false);
    }

    private int decodeReplyTextHeld(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyTextPiece(stream, buffer, offset, decodeReplyTextHeld, false, true);
    }

    private int decodeReplyTextContinue(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyTextPiece(stream, buffer, offset, decodeReplyTextContinue, false, false);
    }

    private int decodeReplyTextPiece(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        LlmGeminiReplyDecoder state,
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
                        final int taken = length > 0 ? stream.onEncodeText(chars, length) : 0;

                        if (taken < length)
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
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyArgsPass(stream, buffer, offset, decodeReplyArgs, false);
    }

    private int decodeReplyArgsHeld(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyArgsPass(stream, buffer, offset, decodeReplyArgsHeld, true);
    }

    private int decodeReplyArgsPass(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        LlmGeminiReplyDecoder state,
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
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyControlPass(stream, buffer, offset, decodeReplyControlValue, false);
    }

    private int decodeReplyControlHeld(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyControlPass(stream, buffer, offset, decodeReplyControlHeld, true);
    }

    private int decodeReplyControlPass(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        LlmGeminiReplyDecoder state,
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
                    paused = false;
                    break;
                case ARGS_DONE:
                    stream.onEncodeControlEnd();
                    stream.decoder = decodeReplyTextKey;
                    break;
                case ARGS_HELD:
                    stream.decoder = decodeReplyControlHeld;
                    resumed = true;
                    paused = false;
                    break;
                default:
                    stream.decoder = decodeReplyControlValue;
                    paused = false;
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
        LlmGeminiStream stream,
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
        LlmGeminiStream stream,
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
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.decoder = decodeReplyNext;
        stream.replyTailLength = 0;
        stream.dataDecoder = decodeReplyTextDone;

        return offset;
    }

    private int decodeReplyEnd(
        LlmGeminiStream stream,
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

    private int decodeReplySignature(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final JsonParserEx parser = stream.wrapReply(buffer, offset);

        try
        {
            while (stream.decoder == decodeReplySignature && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING && !parser.deferredBytes())
                {
                    stream.signature = parser.getStringView().toString();
                    stream.decoder = decodeReplyTextKey;
                }
                else
                {
                    stream.rejectReply();
                }
            }
        }
        catch (JsonException ex)
        {
            stream.rejectReply();
        }

        return stream.positionReply(buffer, offset, decodeReplySignature);
    }

    private int decodeReplyId(
        LlmGeminiStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final JsonParserEx parser = stream.wrapReply(buffer, offset);

        try
        {
            while (stream.decoder == decodeReplyId && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING && !parser.deferredBytes())
                {
                    if (!stream.callClosed)
                    {
                        stream.callId = parser.getStringView().toString();
                    }

                    stream.decoder = decodeReplyTextKey;
                }
                else
                {
                    stream.rejectReply();
                }
            }
        }
        catch (JsonException ex)
        {
            stream.rejectReply();
        }

        return stream.positionReply(buffer, offset, decodeReplyId);
    }

    @FunctionalInterface
    private interface LlmGeminiBlockHandler
    {
        int handle(
            LlmGeminiServer server,
            JsonEvent event,
            JsonParserEx parser);
    }

    @FunctionalInterface
    private interface LlmGeminiServerDecoder
    {
        int decode(
            LlmGeminiServer server,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int progress,
            int limit);
    }

    private int decodeStart(
        LlmGeminiServer server,
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
        LlmGeminiServer server,
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
        LlmGeminiServer server,
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
        LlmGeminiServer server,
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
                if (isConfig(key))
                {
                    server.beginConfig(key);
                }
                else if (isRootControl(key))
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

    private boolean isRootControl(
        CharSequence key)
    {
        return !matches(key, "contents") &&
            !matches(key, MEMBER_SYSTEM_INSTRUCTION) &&
            !matches(key, MEMBER_SYSTEM_INSTRUCTION_SNAKE) &&
            !matches(key, "tools");
    }

    private boolean isToolConfig(
        CharSequence key)
    {
        return matches(key, MEMBER_TOOL_CONFIG) || matches(key, MEMBER_TOOL_CONFIG_SNAKE);
    }

    private boolean isConverted(
        CharSequence key)
    {
        return isToolConfig(key) || matches(key, MEMBER_SERVICE_TIER) || matches(key, MEMBER_SERVICE_TIER_SNAKE);
    }

    private boolean isResponseFormat(
        CharSequence key)
    {
        return matches(key, MEMBER_RESPONSE_FORMAT) || matches(key, MEMBER_RESPONSE_FORMAT_SNAKE);
    }

    private boolean isThinkingConfig(
        CharSequence key)
    {
        return matches(key, MEMBER_THINKING_CONFIG) || matches(key, MEMBER_THINKING_CONFIG_SNAKE);
    }

    private boolean isResponseMimeType(
        CharSequence key)
    {
        return matches(key, MEMBER_RESPONSE_MIME_TYPE) || matches(key, MEMBER_RESPONSE_MIME_TYPE_SNAKE);
    }

    private boolean isResponseSchema(
        CharSequence key)
    {
        return matches(key, MEMBER_RESPONSE_SCHEMA) ||
            matches(key, MEMBER_RESPONSE_SCHEMA_SNAKE) ||
            matches(key, MEMBER_RESPONSE_JSON_SCHEMA) ||
            matches(key, MEMBER_RESPONSE_JSON_SCHEMA_SNAKE);
    }

    private static boolean isJsonMime(
        CharSequence mime)
    {
        return MIME_JSON.contentEquals(mime) || MIME_JSON_ENUM.contentEquals(mime);
    }

    private boolean isConfig(
        CharSequence key)
    {
        return matches(key, MEMBER_GENERATION_CONFIG) || matches(key, MEMBER_GENERATION_CONFIG_SNAKE);
    }

    private int decodeContentsStart(
        LlmGeminiServer server,
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
                server.message = -1;
                server.decoder = decodeContents;
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

    private int decodeContents(
        LlmGeminiServer server,
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
                server.role = null;
                server.context = CONTEXT_MESSAGE;
                lookaheadContent(server, traceId);
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

    private void lookaheadContent(
        LlmGeminiServer server,
        long traceId)
    {
        final JsonParserEx parser = server.parser;
        final int from = server.position() - 1;

        String role = null;
        boolean roleKey = false;
        int level = LEVEL_CONTENT_MEMBER;

        while (role == null && server.decoder == decodeContents && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                level++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                level--;
                if (level < LEVEL_CONTENT_MEMBER)
                {
                    role = ROLE_USER;
                }
                break;
            case KEY_NAME:
                final CharSequence member = parser.getStringView();

                roleKey = level == LEVEL_CONTENT_MEMBER && matches(member, "role");

                if (level == LEVEL_PART_MEMBER && media(member))
                {
                    role = ROLE_USER;
                }
                break;
            case VALUE_STRING:
                if (parser.deferredBytes())
                {
                    parser.consumed(parser.getStringView().length());
                }
                else if (roleKey)
                {
                    final String value = parser.getString();

                    if (ROLE_USER.equals(value) || ROLE_MODEL.equals(value))
                    {
                        role = value;
                    }
                    else
                    {
                        server.onDecodeInvalidRequest(traceId, MESSAGE_ROLE_INVALID.formatted(value));
                    }
                }
                break;
            default:
                break;
            }
        }

        if (server.decoder == decodeContents)
        {
            parser.rewind();

            if (role != null)
            {
                server.role = role;
                server.decoder = decodeContentStart;
            }
            else if (server.decodedWindowLimit - from > server.windowMax())
            {
                server.onDecodeInvalidRequest(traceId, MESSAGE_CONTENT_SIZE);
            }
        }
    }

    private int decodeContentStart(
        LlmGeminiServer server,
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

            server.message++;
            server.decoder = decodeContent;
        }

        return server.position();
    }

    private int decodeContent(
        LlmGeminiServer server,
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
                else if (matches(key, "parts"))
                {
                    server.partsThen = decodeContent;
                    server.decoder = decodePartsStart;
                }
                else
                {
                    server.skip(decodeContent);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.decoder = decodeContents;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeRole(
        LlmGeminiServer server,
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

                if (!ROLE_USER.equals(role) && !ROLE_MODEL.equals(role))
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_ROLE_INVALID.formatted(role));
                }
                else if (server.role != null && !server.role.equals(role))
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_BLOCK_INVALID);
                }
                else
                {
                    server.role = role;
                    server.decoder = decodeContent;
                }
            }
        }

        return server.position();
    }

    private int decodeSystemStart(
        LlmGeminiServer server,
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
                server.context = CONTEXT_SYSTEM;
                server.decoder = decodeSystem;
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

    private int decodeSystem(
        LlmGeminiServer server,
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
                if (matches(parser.getStringView(), "parts"))
                {
                    server.partsThen = decodeSystem;
                    server.decoder = decodePartsStart;
                }
                else
                {
                    server.skip(decodeSystem);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.decoder = decodeRoot;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodePartsStart(
        LlmGeminiServer server,
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
                server.decoder = decodeParts;
                break;
            case VALUE_NULL:
                server.decoder = server.partsThen;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodeParts(
        LlmGeminiServer server,
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
                lookaheadPart(server, traceId, offset, limit);
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

    private void lookaheadPart(
        LlmGeminiServer server,
        long traceId,
        int offset,
        int limit)
    {
        final JsonParserEx parser = server.parser;
        final int from = server.position() - 1;

        String key = null;
        boolean thought = false;
        boolean ended = false;
        boolean thoughtKey = false;
        int depth = 0;

        while (key == null && !ended && server.decoder == decodeParts && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                break;
            case END_ARRAY:
                depth--;
                break;
            case END_OBJECT:
                ended = depth == 0;
                depth--;
                break;
            case KEY_NAME:
                final CharSequence member = parser.getStringView();
                final boolean signature = matches(member, MEMBER_THOUGHT_SIGNATURE) ||
                    matches(member, MEMBER_THOUGHT_SIGNATURE_SNAKE);

                thoughtKey = matches(member, MEMBER_THOUGHT);

                if (!thoughtKey && !signature)
                {
                    key = member.toString();
                }
                break;
            case VALUE_TRUE:
            case VALUE_FALSE:
                if (thoughtKey)
                {
                    thought = event == JsonEvent.VALUE_TRUE;
                    thoughtKey = false;
                }
                else
                {
                    server.onDecodeParseError(traceId);
                }
                break;
            case VALUE_STRING:
                if (parser.deferredBytes())
                {
                    parser.consumed(parser.getStringView().length());
                }
                break;
            default:
                break;
            }
        }

        if (server.decoder == decodeParts)
        {
            if (ended)
            {
                parser.rewind();
                server.skipThen = decodeParts;
                server.decoder = decodeSkipStart;
            }
            else if (key != null)
            {
                lookaheadPartKey(server, traceId, from, offset, limit, key, thought);
            }
            else
            {
                parser.rewind();

                if (limit - offset >= decodeMax)
                {
                    server.onDecodeParseError(traceId);
                }
            }
        }
    }

    private void lookaheadPartKey(
        LlmGeminiServer server,
        long traceId,
        int from,
        int offset,
        int limit,
        String key,
        boolean thought)
    {
        final JsonParserEx parser = server.parser;

        if (server.decoder == decodeParts)
        {
            final LlmSectionType text = textSection(server);
            final boolean assistant = text == LlmSectionType.ASSISTANT_TEXT;

            if (matches(key, "text") && assistant && thought)
            {
                parser.rewind();
                server.partSection = LlmSectionType.ASSISTANT_REASONING;
                server.decoder = decodePartCaptureStart;
            }
            else if (matches(key, "text") && assistant)
            {
                server.probe.begin(LlmSectionType.ASSISTANT_TEXT);

                if (server.probed(traceId, from, limit))
                {
                    server.decoder = decodePartProbeStart;
                }
            }
            else if (matches(key, "text"))
            {
                parser.rewind();
                server.partSection = text;
                server.decoder = decodePartCaptureStart;
            }
            else if (matches(key, MEMBER_FUNCTION_CALL))
            {
                server.probe.begin(LlmSectionType.TOOL_CALL);

                if (server.probed(traceId, from, limit))
                {
                    server.decoder = decodePartProbeStart;
                }
            }
            else if (matches(key, MEMBER_FUNCTION_RESPONSE))
            {
                server.probe.begin(LlmSectionType.TOOL_RESULT);

                if (server.probed(traceId, from, limit))
                {
                    server.decoder = decodePartProbeStart;
                }
            }
            else if (serverCall(key) && assistant)
            {
                parser.rewind();
                server.probe.begin(LlmSectionType.SERVER_TOOL_CALL);
                server.probe.attributes().toolName(SERVER_TOOL_NAME);
                server.decoder = decodePartProbeStart;
            }
            else if (serverResult(key) && assistant)
            {
                parser.rewind();
                server.probe.begin(LlmSectionType.SERVER_TOOL_RESULT);
                server.decoder = decodePartProbeStart;
            }
            else if (serverCall(key) || serverResult(key))
            {
                server.onDecodeInvalidRequest(traceId, MESSAGE_BLOCK_INVALID);
            }
            else if (media(key) && text == LlmSectionType.USER_TEXT)
            {
                server.probe.begin(LlmSectionType.USER_DOCUMENT);

                if (server.probed(traceId, from, limit))
                {
                    server.decoder = decodePartProbeStart;
                }
            }
            else if (media(key))
            {
                server.onDecodeInvalidRequest(traceId, MESSAGE_BLOCK_INVALID);
            }
            else
            {
                parser.rewind();
                server.skipThen = decodeParts;
                server.decoder = decodeSkipStart;
            }
        }
    }

    private int decodeSkipStart(
        LlmGeminiServer server,
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

            server.skipDepth = 1;
            server.decoder = decodeSkip;
        }

        return server.position();
    }

    private int decodePartCaptureStart(
        LlmGeminiServer server,
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

            server.onDecodeBlock(server.partSection, textMessage(server));
            server.partMembers = 0;
            server.decoder = decodePartTextKey;
        }

        return server.position();
    }

    private int decodePartTextKey(
        LlmGeminiServer server,
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

        while (server.decoder == decodePartTextKey && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (event == JsonEvent.KEY_NAME)
            {
                final JsonStringView key = parser.getStringView();

                if (matches(key, "thought") && server.blockSection != LlmSectionType.ASSISTANT_TEXT)
                {
                    server.skipDepth = 0;
                    server.decoder = decodePartTextSkip;
                }
                else
                {
                    server.partKey = matches(key, "text") ? "text" : VENDOR_PREFIX + key;
                    server.decoder = decodePartTextValue;
                }
            }
            else if (event == JsonEvent.END_OBJECT)
            {
                final boolean init = Flags.hasInit(server.blockFlags);

                server.onDecodeData(traceId, authorization, init ? OBJECT_EMPTY : CONTROL_CLOSE, 0, init ? 2 : 1, true);
                server.decoder = decodeParts;
            }
            else
            {
                server.onDecodeParseError(traceId);
            }
        }

        return server.position();
    }

    private int decodePartTextSkip(
        LlmGeminiServer server,
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

        while (server.decoder == decodePartTextSkip && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (event == JsonEvent.VALUE_STRING && parser.deferredBytes())
            {
                parser.consumed(parser.getStringView().length());
            }
            else if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
            {
                server.skipDepth++;
            }
            else if (event == JsonEvent.END_OBJECT || event == JsonEvent.END_ARRAY)
            {
                server.skipDepth--;
            }

            if (server.skipDepth == 0 && event != JsonEvent.KEY_NAME && !(event == JsonEvent.VALUE_STRING &&
                parser.deferredBytes()))
            {
                server.decoder = decodePartTextKey;
            }
        }

        return server.position();
    }

    private int decodePartTextValue(
        LlmGeminiServer server,
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

        while ((server.decoder == decodePartTextValue || server.decoder == decodePartTextPiece) && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (event == JsonEvent.VALUE_STRING)
            {
                final boolean opened = server.decoder == decodePartTextPiece;
                final boolean fin = !parser.deferredBytes();
                final JsonStringView chars = parser.getStringView();
                final int length = chars.length();
                final int taken = !fin && length > 1 && Character.isHighSurrogate(chars.charAt(length - 1)) ? length - 1 : length;
                final int piece = encodePartPiece(server, chars, taken, fin, opened);

                if (piece < 0)
                {
                    server.onDecodeParseError(traceId);
                }
                else
                {
                    server.onDecodeData(traceId, authorization, pieceBuffer, 0, piece, false);

                    if (fin)
                    {
                        server.partMembers++;
                        server.decoder = decodePartTextKey;
                    }
                    else
                    {
                        parser.consumed(taken);
                        server.decoder = decodePartTextPiece;
                    }
                }
            }
            else if (matches(server.partKey, "text"))
            {
                server.onDecodeInvalidRequest(traceId, MESSAGE_BLOCK_INVALID);
            }
            else
            {
                server.partCopier.reset();
                server.decoder = decodePartTextCopy;
                server.generator.wrap(pieceBuffer, 0, pieceBuffer.capacity());
                server.generator.reset();
                server.generator.writeStartObject().writeKey(server.partKey);
                pieceBuffer.putByte(0, (byte) (server.partMembers == 0 && Flags.hasInit(server.blockFlags) ? '{' : ','));

                decodePartTextCopyEvent(server, traceId, authorization, event);
            }
        }

        return server.position();
    }

    private int decodePartTextPiece(
        LlmGeminiServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodePartTextValue(server, traceId, authorization, budgetId, reserved, buffer, offset, progress, limit);
    }

    private int decodePartTextCopy(
        LlmGeminiServer server,
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

        while (server.decoder == decodePartTextCopy && parser.hasNextEvent())
        {
            decodePartTextCopyEvent(server, traceId, authorization, parser.nextEvent());
        }

        return server.position();
    }

    private void decodePartTextCopyEvent(
        LlmGeminiServer server,
        long traceId,
        long authorization,
        JsonEvent event)
    {
        final JsonParserEx parser = server.parser;
        final boolean deferred = event == JsonEvent.VALUE_STRING && parser.deferredBytes();
        final int taken = deferred ? LlmEventCopier.NO_TAKEN : server.partCopier.copy(server.generator, event, parser);

        if (deferred || taken != LlmEventCopier.NO_TAKEN || server.generator.remaining() < ARGS_HEADROOM)
        {
            server.onDecodeParseError(traceId);
        }
        else if (server.partCopier.depth() == 0)
        {
            server.onDecodeData(traceId, authorization, pieceBuffer, 0, server.generator.length(), false);
            server.partMembers++;
            server.decoder = decodePartTextKey;
        }
    }

    private int encodePartPiece(
        LlmGeminiServer server,
        JsonStringView chars,
        int taken,
        boolean fin,
        boolean opened)
    {
        final int width = body.length(chars, 0, taken);

        int length = -1;

        if (!opened)
        {
            server.generator.wrap(pieceBuffer, 0, pieceBuffer.capacity());
            server.generator.reset();
            server.generator.writeStartObject().writeKey(server.partKey);

            final int prefix = server.generator.length();

            if (prefix + 1 + width + 1 <= pieceBuffer.capacity())
            {
                pieceBuffer.putByte(0, (byte) (server.partMembers == 0 && Flags.hasInit(server.blockFlags) ? '{' : ','));
                pieceBuffer.putByte(prefix, (byte) '"');
                length = prefix + 1 + body.transfer(chars, 0, taken,
                    pieceTarget.wrap(pieceBuffer.segment(), pieceBuffer.wrapAdjustment() + prefix + 1));
            }
        }
        else if (width + 1 <= pieceBuffer.capacity())
        {
            length = body.transfer(chars, 0, taken, pieceTarget.wrap(pieceBuffer.segment(), pieceBuffer.wrapAdjustment()));
        }

        if (length >= 0 && fin)
        {
            pieceBuffer.putByte(length++, (byte) '"');
        }

        return length;
    }

    private int decodeBlock(
        LlmGeminiServer server,
        long traceId,
        LlmGeminiServerDecoder state,
        LlmGeminiBlockHandler handler)
    {
        final JsonParserEx parser = server.parser;

        while (server.decoder == state && parser.hasNextEvent())
        {
            final int outcome = server.onBlockEvent(parser.nextEvent(), parser, handler);

            if (outcome == LlmGeminiBlockWriter.REJECT)
            {
                server.onDecodeInvalidRequest(traceId, MESSAGE_BLOCK_INVALID);
            }
        }

        return server.position();
    }

    private int decodeBlockKey(
        LlmGeminiServer server,
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

    private int decodeBlockFunction(
        LlmGeminiServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockFunction, blockFunction);
    }

    private int decodeBlockFunctionKey(
        LlmGeminiServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockFunctionKey, blockFunctionKey);
    }

    private int decodeBlockDrop(
        LlmGeminiServer server,
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
        LlmGeminiServer server,
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

    private int decodeBlockEscape(
        LlmGeminiServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockEscape, blockEscape);
    }

    private int decodeBlockResponse(
        LlmGeminiServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockResponse, blockResponse);
    }

    private int decodeBlockResponseKey(
        LlmGeminiServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockResponseKey, blockResponseKey);
    }

    private int onBlockEscape(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        final int outcome = server.blockWriter.escape(event, parser);

        return outcome == LlmGeminiBlockWriter.VALUE
            ? onBlockValue(server, outcome)
            : outcome;
    }

    private int onBlockResponse(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = LlmGeminiBlockWriter.MORE;

        if (event == JsonEvent.START_OBJECT)
        {
            server.decoder = decodeBlockResponseKey;
        }
        else
        {
            outcome = LlmGeminiBlockWriter.REJECT;
        }

        return outcome;
    }

    private int onBlockResponseKey(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = LlmGeminiBlockWriter.MORE;

        if (event == JsonEvent.KEY_NAME)
        {
            server.blockWriter.responseAction(parser.getStringView());
            server.decodeBlockNext = decodeBlockResponseKey;
            server.decoder = decodeBlockCopy;
        }
        else if (event == JsonEvent.END_OBJECT)
        {
            server.decoder = decodeBlockFunctionKey;
        }
        else
        {
            outcome = LlmGeminiBlockWriter.REJECT;
        }

        return outcome;
    }

    private int decodeBlockCapture(
        LlmGeminiServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockCapture, blockCapture);
    }

    private int onBlockCapture(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        final int outcome = server.blockWriter.capture(event, parser);

        if (outcome == LlmGeminiBlockWriter.VALUE)
        {
            server.endControl();
        }

        return outcome == LlmGeminiBlockWriter.VALUE ? LlmGeminiBlockWriter.MORE : outcome;
    }

    private int decodeBlockValue(
        LlmGeminiServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockValue, blockValue);
    }

    private int decodeBlockTokens(
        LlmGeminiServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockTokens, blockTokens);
    }

    private int onBlockMember(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        final int outcome = server.blockWriter.copyValue(event, parser);

        if (outcome == LlmGeminiBlockWriter.VALUE)
        {
            server.endValue();
        }

        return outcome == LlmGeminiBlockWriter.VALUE ? LlmGeminiBlockWriter.MORE : outcome;
    }

    private int onBlockTokens(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        if (event == JsonEvent.VALUE_NUMBER && server.blockWriter.topLevel())
        {
            server.maxOutputTokens = parser.getInt();
        }

        return onBlockMember(server, event, parser);
    }

    private int decodeBlockControl(
        LlmGeminiServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockControl, blockControl);
    }

    private int onBlockControl(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        final int outcome = server.blockWriter.copyValue(event, parser);

        if (outcome == LlmGeminiBlockWriter.VALUE)
        {
            server.endControl();
        }

        return outcome == LlmGeminiBlockWriter.VALUE ? LlmGeminiBlockWriter.MORE : outcome;
    }

    private int decodeBlockData(
        LlmGeminiServer server,
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

    private int onBlockData(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.dataValue(event, parser));
    }

    private static boolean mediaSection(
        LlmSectionType type)
    {
        return type == LlmSectionType.USER_IMAGE || type == LlmSectionType.USER_DOCUMENT ||
            type == LlmSectionType.USER_AUDIO || type == LlmSectionType.USER_VIDEO;
    }

    private int decodeBlockOptional(
        LlmGeminiServer server,
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

    private int decodeBlockSchema(
        LlmGeminiServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockSchema, blockSchema);
    }

    private int onBlockOptional(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.optional(event, parser));
    }

    private int onBlockSchema(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.schema(event, parser));
    }

    private int decodeBlockId(
        LlmGeminiServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return decodeBlock(server, traceId, decodeBlockId, blockId);
    }

    private int onBlockId(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = LlmGeminiBlockWriter.REJECT;

        if (event == JsonEvent.VALUE_STRING && !parser.deferredBytes())
        {
            server.blockWriter.holdId(parser.getStringView().toString());
            server.decoder = decodeBlockFunctionKey;
            outcome = LlmGeminiBlockWriter.MORE;
        }

        return outcome;
    }

    private int onBlockKey(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = LlmGeminiBlockWriter.MORE;

        if (event == JsonEvent.KEY_NAME)
        {
            server.decodeBlockNext = decodeBlockKey;

            switch (server.blockWriter.rootAction(parser.getStringView()))
            {
            case LlmGeminiBlockWriter.ACTION_FUNCTION:
                server.decoder = decodeBlockFunction;
                break;
            case LlmGeminiBlockWriter.ACTION_SCHEMA:
                server.decoder = decodeBlockSchema;
                break;
            case LlmGeminiBlockWriter.ACTION_OPTIONAL:
                server.decoder = decodeBlockOptional;
                break;
            default:
                server.decoder = decodeBlockCopy;
                break;
            }
        }
        else if (event == JsonEvent.END_OBJECT && server.blockWriter.complete())
        {
            server.endCall();
        }
        else
        {
            outcome = LlmGeminiBlockWriter.REJECT;
        }

        return outcome;
    }

    private int onBlockFunction(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = LlmGeminiBlockWriter.MORE;

        if (event == JsonEvent.START_OBJECT)
        {
            server.blockWriter.enterFunction();
            server.decoder = decodeBlockFunctionKey;
        }
        else
        {
            outcome = LlmGeminiBlockWriter.REJECT;
        }

        return outcome;
    }

    private int onBlockFunctionKey(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = LlmGeminiBlockWriter.MORE;

        if (event == JsonEvent.KEY_NAME)
        {
            server.decodeBlockNext = decodeBlockFunctionKey;

            switch (server.blockWriter.functionAction(parser.getStringView()))
            {
            case LlmGeminiBlockWriter.ACTION_COPY:
                server.decoder = decodeBlockCopy;
                break;
            case LlmGeminiBlockWriter.ACTION_ESCAPE:
                server.decoder = decodeBlockEscape;
                break;
            case LlmGeminiBlockWriter.ACTION_DESCEND:
                server.decoder = decodeBlockResponse;
                break;
            case LlmGeminiBlockWriter.ACTION_ID:
                server.decoder = decodeBlockId;
                break;
            case LlmGeminiBlockWriter.ACTION_DATA:
                server.decoder = decodeBlockData;
                break;
            default:
                server.decoder = decodeBlockDrop;
                break;
            }
        }
        else if (event == JsonEvent.END_OBJECT)
        {
            server.blockWriter.closeFunction();
            server.decoder = decodeBlockKey;
        }
        else
        {
            outcome = LlmGeminiBlockWriter.REJECT;
        }

        return outcome;
    }

    private int onBlockDrop(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.drop(event, parser));
    }

    private int onBlockCopy(
        LlmGeminiServer server,
        JsonEvent event,
        JsonParserEx parser)
    {
        return onBlockValue(server, server.blockWriter.copyValue(event, parser));
    }

    private int onBlockValue(
        LlmGeminiServer server,
        int outcome)
    {
        int result = outcome;

        if (outcome == LlmGeminiBlockWriter.VALUE)
        {
            server.decoder = server.decodeBlockNext;
            result = LlmGeminiBlockWriter.MORE;
        }

        return result;
    }

    private int decodePartProbeStart(
        LlmGeminiServer server,
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

            if (server.probe.type() == LlmSectionType.TOOL_CALL)
            {
                server.beginCall(LlmSectionType.TOOL_CALL, LlmGeminiBlockWriter.MODE_CALL);
            }
            else if (server.probe.type() == LlmSectionType.TOOL_RESULT)
            {
                server.beginCall(LlmSectionType.TOOL_RESULT, LlmGeminiBlockWriter.MODE_RESULT);
            }
            else if (server.probe.type() == LlmSectionType.SERVER_TOOL_CALL)
            {
                server.beginCall(LlmSectionType.SERVER_TOOL_CALL, LlmGeminiBlockWriter.MODE_SERVER_CALL);
            }
            else if (server.probe.type() == LlmSectionType.SERVER_TOOL_RESULT)
            {
                server.beginCall(LlmSectionType.SERVER_TOOL_RESULT, LlmGeminiBlockWriter.MODE_SERVER_RESULT);
            }
            else if (mediaSection(server.probe.type()))
            {
                server.beginMedia(server.probe.type());
            }
            else if (server.probe.type() == LlmSectionType.ASSISTANT_TEXT ||
                server.probe.type() == LlmSectionType.ASSISTANT_REASONING)
            {
                server.onDecodeBlock(server.probe.type(), textMessage(server));
                server.partMembers = 0;
                server.decoder = decodePartTextKey;
            }
            else
            {
                server.capture(server.probe.type(), textMessage(server), decodeParts);
                server.probing = true;
                server.decoder = decodeTool;
            }
        }

        return server.position();
    }

    private int decodePart(
        LlmGeminiServer server,
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

        while (server.decoder == decodePart && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = onCaptureEvent(server, event);

            if (server.captureDepth == 0 && !deferred)
            {
                server.endCapture(traceId, authorization);
            }
        }

        return server.position();
    }

    private int decodeToolsStart(
        LlmGeminiServer server,
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
        LlmGeminiServer server,
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
                lookaheadTool(server, traceId, offset, limit);
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

    private void lookaheadTool(
        LlmGeminiServer server,
        long traceId,
        int offset,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        String key = null;
        boolean ended = false;

        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case END_OBJECT:
                ended = true;
                break;
            case KEY_NAME:
                key = parser.getStringView().toString();
                break;
            default:
                server.onDecodeParseError(traceId);
                break;
            }
        }

        if (server.decoder == decodeTools)
        {
            if (key != null && key.equals(MEMBER_FUNCTION_DECLARATIONS))
            {
                server.decoder = decodeDeclarationsStart;
            }
            else if (key != null)
            {
                parser.rewind();
                server.probe.begin(LlmSectionType.SERVER_TOOL_DEFINITION);
                server.probe.attributes().toolName(key);
                server.decoder = decodeToolStart;
            }
            else if (ended)
            {
                parser.rewind();
                server.skipThen = decodeTools;
                server.decoder = decodeSkipStart;
            }
            else
            {
                parser.rewind();

                if (limit - offset >= decodeMax)
                {
                    server.onDecodeParseError(traceId);
                }
            }
        }
    }

    private int decodeToolStart(
        LlmGeminiServer server,
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

            server.capture(LlmSectionType.SERVER_TOOL_DEFINITION, -1, decodeTools);
            server.probing = true;
            server.decoder = decodeTool;
        }

        return server.position();
    }

    private int decodeToolKey(
        LlmGeminiServer server,
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
                server.onDecodeInvalidRequest(traceId, MESSAGE_TOOL_UNSUPPORTED);
                break;
            case END_OBJECT:
                server.decoder = decodeTools;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodeDeclarationsStart(
        LlmGeminiServer server,
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
                server.decoder = decodeDeclarations;
                break;
            case VALUE_NULL:
                server.decoder = decodeToolKey;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodeDeclarations(
        LlmGeminiServer server,
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

                if (server.probed(traceId, server.position() - 1, limit))
                {
                    server.decoder = decodeDeclarationStart;
                }
                break;
            case END_ARRAY:
                server.decoder = decodeToolKey;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodeDeclarationStart(
        LlmGeminiServer server,
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

            server.beginBlock(LlmSectionType.TOOL_DEFINITION, -1, LlmGeminiBlockWriter.MODE_DEFINITION, decodeDeclarations);
        }

        return server.position();
    }

    private int decodeTool(
        LlmGeminiServer server,
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

    private int decodeControlSeparator(
        LlmGeminiServer server,
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
        LlmGeminiServer server,
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
            final boolean deferred = onCaptureEvent(server, event);

            if (server.captureDepth == 0 && !deferred)
            {
                server.endCapture(traceId, authorization);
            }
        }

        return server.position();
    }

    private int decodeConfigStart(
        LlmGeminiServer server,
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
                server.decoder = decodeConfigFirst;
                break;
            case VALUE_NULL:
                server.decoder = decodeRootControl;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodeConfigFirst(
        LlmGeminiServer server,
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
                server.onDecodeConfigKey(parser.getStringView(), false);
                break;
            case END_OBJECT:
                server.onDecodeConfigEmpty();
                server.decoder = decodeRootControl;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodeConfigOpen(
        LlmGeminiServer server,
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
                server.onDecodeConfigKey(parser.getStringView(), true);
                break;
            case END_OBJECT:
                server.onDecodeConfigResidueClose();
                server.onDecodeConfigEnd();
                server.decoder = decodeRootControl;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodeConfigClosed(
        LlmGeminiServer server,
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
                server.onDecodeConfigKey(parser.getStringView(), false);
                break;
            case END_OBJECT:
                server.onDecodeConfigEnd();
                server.decoder = decodeRootControl;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodeConfigSchema(
        LlmGeminiServer server,
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

        while (server.decoder == decodeConfigSchema && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = onCaptureEvent(server, event);

            if (server.captureDepth == 0 && !deferred)
            {
                server.endConfigSchema(traceId, authorization);
            }
        }

        return server.position();
    }

    private int decodeConfigConverted(
        LlmGeminiServer server,
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

        while (server.decoder == decodeConfigConverted && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = onCaptureEvent(server, event);

            if (server.captureDepth == 0 && !deferred)
            {
                server.endConfigConverted(traceId, authorization);
            }
        }

        return server.position();
    }

    private int decodeConfigMime(
        LlmGeminiServer server,
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

        while (server.decoder == decodeConfigMime && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (event == JsonEvent.VALUE_STRING && server.mimeType.length() < MIME_MAX)
            {
                server.mimeType.append(parser.getStringView());
            }

            final boolean deferred = onCaptureEvent(server, event);

            if (server.captureDepth == 0 && !deferred)
            {
                server.endConfigMime(traceId, authorization);
            }
        }

        return server.position();
    }

    private static boolean onCaptureEvent(
        LlmGeminiServer server,
        JsonEvent event)
    {
        final JsonParserEx parser = server.parser;
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

        return deferred;
    }

    private static boolean serverCall(
        CharSequence key)
    {
        return matches(key, "executableCode") || matches(key, "executable_code");
    }

    private static boolean serverResult(
        CharSequence key)
    {
        return matches(key, "codeExecutionResult") || matches(key, "code_execution_result");
    }

    private static boolean media(
        CharSequence key)
    {
        return matches(key, "inlineData") || matches(key, "inline_data") ||
            matches(key, "fileData") || matches(key, "file_data");
    }

    private static LlmSectionType textSection(
        LlmGeminiServer server)
    {
        final LlmSectionType type;

        if (server.context == CONTEXT_SYSTEM)
        {
            type = LlmSectionType.SYSTEM_INSTRUCTION;
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

    private static int textMessage(
        LlmGeminiServer server)
    {
        return server.context == CONTEXT_SYSTEM ? -1 : server.message;
    }

    private int decodeSkip(
        LlmGeminiServer server,
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
        LlmGeminiServer server,
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
        LlmGeminiServer server,
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
    private final class LlmGeminiServer
    {
        private final JsonGeneratorEx generator;
        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long exitId;
        private final long initialAuthorization;
        private final String contentType;
        private final String model;
        private final String operation;
        private final boolean streaming;
        private final boolean sse;
        private final Runnable deauthorize;
        private final JsonParserEx parser;

        private LlmGeminiStream stream;
        private LlmGeminiServerDecoder decoder;

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
        private boolean decodedLast;

        private DirectBufferEx replyBody;
        private int replyBodySent;

        private long pendingReplyEndTraceId;
        private long pendingReplyEndAuthorization;

        private int message;
        private String role;
        private int context;
        private LlmGeminiServerDecoder partsThen;

        private LlmGeminiServerDecoder skipThen;
        private int skipDepth;
        private LlmSectionType partSection;
        private LlmGeminiBlockWriter blockWriter;
        private LlmGeminiBlockWriter writing;
        private int resultShape;
        private LlmGeminiServerDecoder decodeBlockNext;
        private LlmGeminiServerDecoder blockThen;
        private String partKey;
        private int partMembers;
        private final LlmEventCopier partCopier = new LlmEventCopier();
        private String serverCallId;

        private boolean streamOptions;
        private int maxOutputTokens = NO_MAX_OUTPUT_TOKENS;

        private LlmGeminiServerDecoder captureThen;
        private boolean captureOpen;
        private int captureDepth;
        private int captureFrom;
        private LlmGeminiServerDecoder separatorThen;


        private final LlmSectionAttributes blockAttributes;
        private final LlmSectionAttributes sentAttributes;
        private final LlmSectionAttributes finAttributes;
        private final LlmGeminiAttributeProbe probe;
        private boolean probing;

        private long decodeTraceId;
        private long decodeAuthorization;
        private LlmSectionType blockSection;
        private int blockMessage;
        private int blockFlags;

        private final String[] callNames = new String[CALLS_MAX];
        private final String[] callIds = new String[CALLS_MAX];
        private final boolean[] callUsed = new boolean[CALLS_MAX];
        private int callCount;
        private int callMessage = -1;
        private int callOrdinal;
        private LlmSectionPipeline blockPipeline;
        private final StringBuilder controlKey = new StringBuilder();
        private final StringBuilder configKey = new StringBuilder();
        private final LlmSectionPipeline.Output blockOutput = (buffer, offset, length, last) ->
            onDecodeSectionData(decodeTraceId, decodeAuthorization, buffer, offset, length, last);
        private final LlmMembersOutput memberOutput = new LlmMembersOutput((buffer, offset, length) ->
            onDecodeSectionData(decodeTraceId, decodeAuthorization, buffer, offset, length, false));
        private LlmSectionPipeline toolConfigPipeline;
        private LlmGeminiControlToCanonical toolConfigTransform;
        private final StringBuilder mimeKey = new StringBuilder();
        private final StringBuilder mimeType = new StringBuilder();
        private LlmGeminiServerDecoder mimeThen;
        private int format;

        private LlmGeminiServer(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            long exitId,
            long authorization,
            String contentType,
            String model,
            String operation,
            String query,
            Runnable deauthorize)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.exitId = exitId;
            this.initialAuthorization = authorization;
            this.contentType = contentType;
            this.model = model;
            this.operation = operation;
            this.streaming = OPERATION_STREAM.equals(operation);
            this.sse = query != null && Arrays.asList(query.split("&")).contains(QUERY_ALT_SSE);
            this.deauthorize = deauthorize;
            this.parser = JsonEx.createParser(PARSER_CONFIG);
            this.generator = JsonEx.createGenerator();
            this.blockAttributes = new LlmSectionAttributes();
            this.sentAttributes = new LlmSectionAttributes();
            this.finAttributes = new LlmSectionAttributes();
            this.probe = new LlmGeminiAttributeProbe();
            this.decoder = decodeStart;
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

            stream = new LlmGeminiStream(this);
            stream.doAppBegin(traceId, initialAuthorization, model, operation);

            if (streaming)
            {
                stream.doAppData(traceId, initialAuthorization, Flags.COMPLETE, LlmSectionType.CONTROL, -1, null, null,
                    STREAM_SECTION, 0, STREAM_SECTION.capacity());
            }
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

                int previous;
                LlmGeminiServerDecoder previousDecoder;
                do
                {
                    previous = progress;
                    previousDecoder = decoder;
                    progress = decodeInput(traceId, authorization, budgetId, reserved, buffer, progress, limit);
                }
                while (progress != previous && progress < limit || decoder != previousDecoder);
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
            decodedLast = StreamState.initialClosing(state);

            if (decoder == decodeEnd || decoder == decodeIgnore)
            {
                decodedWindowLimit = limit;
                progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, offset, progress, limit);
            }
            else
            {
                int window = windowFor(offset, limit);

                if (window < Math.min(MIN_WINDOW, limit - offset) && writing != null)
                {
                    writing.flush(false);
                    window = windowFor(offset, limit);
                }

                if (window >= Math.min(MIN_WINDOW, limit - offset))
                {
                    final int windowLimit = offset + window;

                    final boolean windowLast = decodedLast && windowLimit == limit;

                    decodedWindowLimit = windowLimit;
                    decodeTraceId = traceId;
                    decodeAuthorization = authorization;
                    parser.wrap(buffer, offset, windowLimit, windowLast);

                    final LlmGeminiServerDecoder previous = decoder;

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

                    if (captureOpen)
                    {
                        if (stalled)
                        {
                            flushCapture(traceId, authorization, false);
                            progress = position();
                        }
                    }
                    else if (writing != null && stalled)
                    {
                        writing.flush(false);
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
                decodeSlot == NO_SLOT && decoder != decodeIgnore &&
                (stream == null || stream.requestSlot == NO_SLOT))
            {
                state = StreamState.closeInitial(state);
                onDecodeEnd(traceId);
            }
        }

        private int windowFor(
            int offset,
            int limit)
        {
            return (int) Math.min(limit - offset, Math.max(available() - FRAMING_RESERVE, 0) / CONTROL_GROWTH);
        }

        private int windowMax()
        {
            final int room = (encodePool.slotCapacity() - REQUEST_ENCODE_SLACK) / REQUEST_CANONICAL_GROWTH;
            final int credit = Math.min(initialMax, room);

            return Math.max(credit - FRAMING_RESERVE, 0) / CONTROL_GROWTH - MIN_WINDOW;
        }

        private int available()
        {
            final int pending = blockWriter != null ? blockWriter.pending() : 0;
            final int credit = (int) Math.max(stream.initialAvailable() - pending, 0L);
            final int available = Math.min(credit, stream.requestRoom());
            final int appendRoom = streaming && !streamOptions ? Math.min(STREAM_OPTIONS_SECTION.capacity(), available >> 1) : 0;

            return available - appendRoom;
        }

        private int position()
        {
            return decodedWindowLimit - parser.remaining();
        }

        private void skip(
            LlmGeminiServerDecoder then)
        {
            skipThen = then;
            skipDepth = 0;
        }

        private boolean probed(
            long traceId,
            int from,
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

            final boolean ready = accepted && (!pending || decodedWindowLimit == limit && !probe.unclassified());

            if (ready)
            {
                if (probe.type() == LlmSectionType.TOOL_RESULT)
                {
                    resultShape = probe.settle();
                }

                probe.restart();
            }
            else if (accepted && probe.unclassified() && decodedWindowLimit - from > windowMax())
            {
                onDecodeInvalidRequest(traceId, MESSAGE_MEDIA_TYPE);
            }

            return ready;
        }

        private void beginCall(
            LlmSectionType type,
            int mode)
        {
            beginBlock(type, textMessage(this), mode, decodeParts);
        }

        private void beginMedia(
            LlmSectionType type)
        {
            final LlmSectionAttributes attributes = probe.attributes();
            final boolean reference = LlmSectionAttributes.SOURCE_REFERENCE.equals(attributes.source());
            final LlmGeminiBlockWriter writer = blockWriter();

            switch (type)
            {
            case USER_IMAGE:
                writer.media("image_url", "image_url", "url", "url", false, attributes.mediaType(), reference);
                break;
            case USER_AUDIO:
                writer.media("input_audio", "input_audio", "data", "url", true, attributes.mediaType(), reference);
                break;
            case USER_VIDEO:
                writer.media("input_video", "input_video", "data", "url", true, attributes.mediaType(), reference);
                break;
            default:
                writer.media("file", "file", "file_data", "file_url", false, attributes.mediaType(), reference);
                break;
            }

            beginBlock(type, textMessage(this), LlmGeminiBlockWriter.MODE_MEDIA, decodeParts);
        }

        private void beginBlock(
            LlmSectionType type,
            int message,
            int mode,
            LlmGeminiServerDecoder then)
        {
            onDecodeBlock(type, message);

            blockThen = then;
            probing = true;
            probe.consuming(false);
            writing = blockWriter();
            blockWriter().begin(pieceBuffer, 0, mode, resultShape != LlmGeminiAttributeProbe.SHAPE_OTHER);
            decoder = decodeBlockKey;
        }

        private void endCall()
        {
            blockWriter.finish();
            writing = null;
            probe.consuming(true);
            probing = false;
            decoder = blockThen;
        }

        private LlmGeminiBlockWriter blockWriter()
        {
            if (blockWriter == null)
            {
                blockWriter = new LlmGeminiBlockWriter(
                    (buffer, offset, length, last) ->
                        onDecodeData(decodeTraceId, decodeAuthorization, buffer, offset, length, last),
                    JsonEx.createGenerator());
            }

            return blockWriter;
        }

        private int onBlockEvent(
            JsonEvent event,
            JsonParserEx parser,
            LlmGeminiBlockHandler handler)
        {
            int outcome = LlmGeminiBlockWriter.MORE;

            if (probing)
            {
                probe.onEvent(event, parser);
            }

            final boolean fragment = event == JsonEvent.VALUE_STRING && parser.deferredBytes();
            final int length = fragment ? parser.getStringView().length() : 0;

            try
            {
                if (blockWriter.reserve(event, parser))
                {
                    outcome = handler.handle(this, event, parser);

                    if (fragment && outcome != LlmGeminiBlockWriter.REJECT)
                    {
                        parser.consumed(length);
                    }
                }
                else
                {
                    outcome = LlmGeminiBlockWriter.REJECT;
                }
            }
            catch (JsonException ex)
            {
                outcome = LlmGeminiBlockWriter.REJECT;
            }

            return outcome;
        }

        private void capture(
            LlmSectionType type,
            int blockMessage,
            LlmGeminiServerDecoder then)
        {
            final int from = position() - 1;

            onDecodeBlock(type, blockMessage);

            captureThen = then;
            captureOpen = true;
            captureDepth = 1;
            captureFrom = from;
        }

        private void onDecodeRootKey(
            CharSequence key)
        {
            if (matches(key, "contents"))
            {
                decoder = decodeContentsStart;
            }
            else if (matches(key, MEMBER_SYSTEM_INSTRUCTION) || matches(key, MEMBER_SYSTEM_INSTRUCTION_SNAKE))
            {
                decoder = decodeSystemStart;
            }
            else if (matches(key, "tools"))
            {
                decoder = decodeToolsStart;
            }
            else if (isConfig(key))
            {
                onDecodeBlock(LlmSectionType.CONTROL, -1);
                beginConfig(key);
            }
            else
            {
                control(key, -1, decodeRootControl);
            }
        }

        private void onDecodeControlValue(
            LlmGeminiServerDecoder then)
        {
            captureOpen = true;
            captureDepth = 0;
            captureFrom = position();
            separatorThen = then;
            decoder = decodeControlSeparator;
        }

        private void onDecodeControlSeparator()
        {
            final int start = LlmValueStart.of(decodedInput, captureFrom, decodedWindowLimit);

            if (start < decodedWindowLimit)
            {
                captureFrom = start;
                decoder = separatorThen;
            }
        }

        private void controlMore(
            CharSequence key,
            LlmGeminiServerDecoder then)
        {
            captureThen = then;

            if (isConverted(key))
            {
                controlCapture(key);
            }
            else
            {
                controlMember(key);
            }
        }

        private void controlCapture(
            CharSequence key)
        {
            writing = blockWriter();
            writing.beginCapture(pieceBuffer, 0, controlOpen(),
                isToolConfig(key) ? LlmGeminiBlockWriter.CAPTURE_TOOL_CONFIG : LlmGeminiBlockWriter.CAPTURE_SERVICE_TIER,
                key);
            decoder = decodeBlockCapture;
        }

        private void controlMember(
            CharSequence key)
        {
            writing = blockWriter();
            writing.beginMember(pieceBuffer, 0, controlOpen());
            writing.memberKey(key);
            decoder = decodeBlockControl;
        }

        private void beginValue(
            LlmGeminiServerDecoder then,
            LlmGeminiServerDecoder state)
        {
            blockThen = then;
            writing = blockWriter();
            writing.beginValue(pieceBuffer, 0);
            decoder = state;
        }

        private void endValue()
        {
            blockWriter.flush(false);
            writing = null;
            decoder = blockThen;
        }

        private void endControl()
        {
            blockWriter.endMember();
            writing = null;
            decoder = captureThen;
        }

        private LlmSectionPipeline toolConfigPipeline()
        {
            if (toolConfigPipeline == null)
            {
                toolConfigTransform = new LlmGeminiControlToCanonical();
                toolConfigPipeline = new LlmSectionPipeline(toolConfigTransform, transformBuffer);
            }

            return toolConfigPipeline;
        }

        private void closeControl(
            long traceId,
            long authorization)
        {
            if (!Flags.hasInit(blockFlags))
            {
                onDecodeData(traceId, authorization, EMPTY_BUFFER, 0, 0, true);
            }
        }

        private void beginConfig(
            CharSequence key)
        {
            configKey.setLength(0);
            configKey.append(key);
            format = FORMAT_NONE;
            captureThen = decodeRootControl;
            decoder = decodeConfigStart;
        }

        private void onDecodeConfigKey(
            CharSequence key,
            boolean residueOpen)
        {
            final String shared = LlmSharedMembers.gemini(key);

            if (shared != null)
            {
                if (residueOpen)
                {
                    onDecodeConfigResidueClose();
                }

                onDecodeSharedKey(shared, controlOpen());
                beginValue(decodeConfigClosed, decodeBlockValue);
            }
            else if (isResponseFormat(key) || isThinkingConfig(key))
            {
                if (residueOpen)
                {
                    onDecodeConfigResidueClose();
                }

                onDecodeConfigConverted(key);
                decoder = decodeConfigConverted;
            }
            else if (isResponseMimeType(key))
            {
                mimeKey.setLength(0);
                mimeKey.append(key);
                mimeType.setLength(0);
                mimeThen = residueOpen ? decodeConfigOpen : decodeConfigClosed;
                decoder = decodeConfigMime;
            }
            else if (isResponseSchema(key) && format == FORMAT_PENDING)
            {
                if (residueOpen)
                {
                    onDecodeConfigResidueClose();
                }

                onDecodeSharedKey(SHARED_RESPONSE_FORMAT, controlOpen());
                onDecodeBlockData(decodeTraceId, decodeAuthorization, RESPONSE_FORMAT_SCHEMA_OPEN, 0,
                    RESPONSE_FORMAT_SCHEMA_OPEN.capacity(), false);
                decoder = decodeConfigSchema;
            }
            else
            {
                if (residueOpen)
                {
                    onDecodeControlKey(key, false, ',');
                }
                else
                {
                    onDecodeControlKey(configKey, true, controlOpen());
                    onDecodeControlKey(key, false, '{');
                }

                format = format == FORMAT_NONE && isResponseSchema(key) ? FORMAT_RESIDUE : format;

                beginValue(decodeConfigOpen,
                    matches(key, MEMBER_MAX_OUTPUT_TOKENS) || matches(key, MEMBER_MAX_OUTPUT_TOKENS_SNAKE)
                        ? decodeBlockTokens
                        : decodeBlockValue);
            }

            if (decoder != decodeBlockValue && decoder != decodeBlockTokens)
            {
                onDecodeControlValue(decoder);
            }
        }

        private void onDecodeConfigResidueClose()
        {
            onDecodeBlockData(decodeTraceId, decodeAuthorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), false);
        }

        private void onDecodeConfigEmpty()
        {
            onDecodeControlKey(configKey, true, controlOpen());
            onDecodeBlockData(decodeTraceId, decodeAuthorization, OBJECT_EMPTY, 0, OBJECT_EMPTY.capacity(), false);
        }

        private void endConfigValue(
            long traceId,
            long authorization,
            LlmGeminiServerDecoder then)
        {
            flushCapture(traceId, authorization, false);

            captureOpen = false;
            decoder = then;
        }

        private void onDecodeConfigConverted(
            CharSequence key)
        {
            final DirectBufferEx separator = Flags.hasInit(blockFlags) ? CONTROL_BEGIN : CONTROL_CONTINUE;

            onDecodeSectionData(decodeTraceId, decodeAuthorization, separator, 0, separator.capacity(), false);

            blockPipeline = toolConfigPipeline();
            toolConfigTransform.residueKey(NAME + ':' + configKey);
            blockPipeline.begin(blockAttributes);
            memberOutput.begin();

            onDecodeControlKey(key, false, '{');
        }

        private void endConfigConverted(
            long traceId,
            long authorization)
        {
            flushCapture(traceId, authorization, false);

            if (!onDecodeBlockData(traceId, authorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), true))
            {
                onDecodeInvalidRequest(traceId, MESSAGE_BLOCK_INVALID);
            }

            blockPipeline = null;
            captureOpen = false;
            decoder = decodeConfigClosed;
        }

        private void endConfigSchema(
            long traceId,
            long authorization)
        {
            flushCapture(traceId, authorization, false);
            onDecodeBlockData(traceId, authorization, RESPONSE_FORMAT_SCHEMA_CLOSE, 0,
                RESPONSE_FORMAT_SCHEMA_CLOSE.capacity(), false);

            format = FORMAT_LIFTED;
            captureOpen = false;
            decoder = decodeConfigClosed;
        }

        private void endConfigMime(
            long traceId,
            long authorization)
        {
            if (format == FORMAT_NONE && isJsonMime(mimeType))
            {
                format = FORMAT_PENDING;
                captureFrom = position();
                decoder = mimeThen;
            }
            else
            {
                if (mimeThen == decodeConfigOpen)
                {
                    onDecodeControlKey(mimeKey, false, ',');
                }
                else
                {
                    onDecodeControlKey(configKey, true, controlOpen());
                    onDecodeControlKey(mimeKey, false, '{');
                }

                format = format == FORMAT_NONE ? FORMAT_RESIDUE : format;
                decoder = decodeConfigOpen;
                flushCapture(traceId, authorization, false);
            }

            captureOpen = false;
        }

        private void onDecodeConfigEnd()
        {
            if (format == FORMAT_PENDING)
            {
                onDecodeSharedKey(SHARED_RESPONSE_FORMAT, controlOpen());
                onDecodeBlockData(decodeTraceId, decodeAuthorization, RESPONSE_FORMAT_OBJECT, 0,
                    RESPONSE_FORMAT_OBJECT.capacity(), false);
                format = FORMAT_LIFTED;
            }
        }

        private char controlOpen()
        {
            return Flags.hasInit(blockFlags) ? '{' : ',';
        }

        private void control(
            CharSequence key,
            int message,
            LlmGeminiServerDecoder then)
        {
            onDecodeBlock(LlmSectionType.CONTROL, message);

            captureThen = then;

            if (isConverted(key))
            {
                controlCapture(key);
            }
            else
            {
                controlMember(key);
            }
        }

        private void endCapture(
            long traceId,
            long authorization)
        {
            flushCapture(traceId, authorization, captureThen != decodeRootControl);

            if (blockSection == LlmSectionType.CONTROL && blockPipeline != null && captureThen == decodeRootControl &&
                !onDecodeBlockData(traceId, authorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), true))
            {
                onDecodeInvalidRequest(traceId, MESSAGE_BLOCK_INVALID);
            }

            blockPipeline = null;
            captureOpen = false;
            probing = false;
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

        private void onDecodeBlock(
            LlmSectionType type,
            int message)
        {
            blockSection = type;
            blockMessage = message;
            blockFlags = Flags.INIT;

            blockAttributes.clear();

            blockPipeline = null;
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

                if (!onDecodeBlockData(traceId, authorization, buffer, offset, length, last && !closing) ||
                    closing && !onDecodeBlockData(traceId, authorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), true))
                {
                    onDecodeInvalidRequest(traceId, MESSAGE_BLOCK_INVALID);
                }
            }
        }

        private boolean onDecodeBlockData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            boolean accepted = true;

            if (blockPipeline == null)
            {
                onDecodeSectionData(traceId, authorization, buffer, offset, length, last);
            }
            else
            {
                accepted = blockPipeline.feed(buffer, offset, length, last,
                    blockSection == LlmSectionType.CONTROL && captureThen == decodeRootControl ? memberOutput : blockOutput);
            }

            return accepted;
        }

        private void onDecodeControlKey(
            CharSequence key,
            boolean prefixed,
            char open)
        {
            controlKey.setLength(0);

            if (prefixed)
            {
                controlKey.append(NAME).append(':');
            }

            controlKey.append(key);

            onDecodeControlPiece(open);
        }

        private void onDecodeSharedKey(
            String member,
            char open)
        {
            controlKey.setLength(0);
            controlKey.append(SHARED_PREFIX).append(member);

            onDecodeControlPiece(open);
        }

        private void onDecodeControlPiece(
            char open)
        {
            generator.wrap(pieceBuffer, 0, pieceBuffer.capacity());
            generator.reset();
            generator.writeStartObject().writeKey(controlKey);

            pieceBuffer.putByte(0, (byte) open);

            onDecodeBlockData(decodeTraceId, decodeAuthorization, pieceBuffer, 0, generator.length(), false);
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
                if (probing)
                {
                    if (blockSection == LlmSectionType.TOOL_CALL)
                    {
                        if (probe.attributes().toolCallId() == null)
                        {
                            probe.attributes().toolCallId(synthesizeToolCallId());
                        }

                        recordToolCall(probe.attributes().toolName(), probe.attributes().toolCallId());
                    }
                    else if (blockSection == LlmSectionType.TOOL_RESULT)
                    {
                        if (probe.attributes().toolCallId() == null)
                        {
                            probe.attributes().toolCallId(pairToolCallId(probe.attributes().toolName()));
                        }

                        probe.attributes().toolName(null);
                    }
                    else if (blockSection == LlmSectionType.SERVER_TOOL_CALL)
                    {
                        serverCallId = synthesizeToolCallId();
                        probe.attributes().toolCallId(serverCallId);
                    }
                    else if (blockSection == LlmSectionType.SERVER_TOOL_RESULT)
                    {
                        probe.attributes().toolCallId(serverCallId);
                    }

                    blockAttributes.copyFrom(probe.attributes());
                    sentAttributes.copyFrom(probe.attributes());
                }

                attributes = blockAttributes;
            }
            else if (probing && last)
            {
                finAttributes.difference(probe.attributes(), sentAttributes);
                attributes = finAttributes;
            }
            else
            {
                attributes = null;
            }

            stream.doAppData(traceId, authorization, flags, init ? blockSection : null, blockMessage, null,
                attributes, buffer, offset, length);

            blockFlags = Flags.NONE;
        }

        private void recordToolCall(
            String name,
            String id)
        {
            if (callCount < CALLS_MAX)
            {
                callNames[callCount] = name;
                callIds[callCount] = id;
                callUsed[callCount] = false;
                callCount++;
            }
        }

        private String pairToolCallId(
            String name)
        {
            String id = null;

            for (int index = 0; id == null && index < callCount; index++)
            {
                if (!callUsed[index] && callNames[index] != null && callNames[index].equals(name))
                {
                    callUsed[index] = true;
                    id = callIds[index];
                }
            }

            return id != null ? id : synthesizeToolCallId();
        }

        private String synthesizeToolCallId()
        {
            if (blockMessage != callMessage)
            {
                callMessage = blockMessage;
                callOrdinal = 0;
            }

            return TOOL_CALL_ID_PREFIX + blockMessage + '_' + callOrdinal++;
        }

        private void onDecodeRootEnd(
            long traceId)
        {
            if (streaming && !streamOptions)
            {
                streamOptions = true;
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
            decoder = decodeIgnore;

            initialAck = initialSeq;
            doNetWindow(traceId);
            doNetError(traceId, initialAuthorization, STATUS_BAD_REQUEST, message);
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
                    replyDebit = LlmGeminiServerFactory.this.context.supplyDebit(replyId, replyBudgetId,
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

    private final class LlmGeminiStream
    {
        private static final int NO_EVENT = -1;
        private static final int EVENT_NONE = 0;
        private static final int EVENT_OPEN = 1;
        private static final int EVENT_STARTED = 2;

        private static final int KIND_NONE = 0;
        private static final int KIND_TEXT = 1;
        private static final int KIND_SKIPPED = 2;
        private static final int KIND_CONTROL = 3;
        private static final int KIND_TOOL_CALL = 4;
        private static final int KIND_REASONING = 5;
        private static final int KIND_SERVER_TOOL_CALL = 6;
        private static final int KIND_SERVER_TOOL_RESULT = 7;

        private static final int CONTROL_DROP = 0;
        private static final int CONTROL_HELD = 1;
        private static final String MEMBER_USAGE_METADATA = "usageMetadata";
        private static final Set<String> CANDIDATE_MEMBERS = Set.of("finishMessage", "safetyRatings", "groundingMetadata",
            "urlContextMetadata", "citationMetadata", "avgLogprobs", "logprobsResult", "tokenCount");
        private static final Predicate<String> CANDIDATE_ONLY = CANDIDATE_MEMBERS::contains;
        private static final Predicate<String> USAGE_ONLY = MEMBER_USAGE_METADATA::equals;
        private static final Predicate<String> RESPONSE_ONLY =
            name -> !CANDIDATE_MEMBERS.contains(name) && !MEMBER_USAGE_METADATA.equals(name);

        private static final int NONE = -1;
        private static final int MESSAGE_FIRST = 0;

        private final LlmGeminiServer server;
        private final JsonGeneratorEx generator;

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

        private int requestSlot = NO_SLOT;
        private int requestSlotOffset;
        private int requestSlotProgress;
        private int requestHeadSent;

        private LlmGeminiReplyDecoder decoder;
        private LlmGeminiReplyDecoder dataDecoder;
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
        private JsonGeneratorEx replyProbe;
        private JsonGeneratorEx argsGenerator;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotFrom;
        private long stagedReserved;

        private boolean flushed;

        private String responseId;
        private String responseModel;
        private String responseDialect;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private int encodeSlotLimit;
        private int encodeBase;
        private int encodeShift;
        private int encodeSent;
        private long encodeTraceId;
        private long encodeAuthorization;
        private long encodeBudgetId;
        private int eventPhase;
        private int eventStart;

        private int chunkHead;
        private int tailBare;
        private int signatureFraming;
        private int argsOuter;
        private int valueMode;
        private int textTail;
        private int reasoningTail;
        private int finishFraming;
        private int finishBodyFraming;
        private int usageMax;
        private String finishReasonName;
        private int finishInputTokens;
        private int finishCacheReadTokens;
        private int finishOutputTokens;
        private int finishReasoningTokens;
        private int finishTotalTokens;

        private int kind;
        private int controlMessage;
        private int controlMode;
        private final LlmControlHold hold = new LlmControlHold(this::heldBuffer);
        private String signature;
        private String callId;
        private boolean callClosed;
        private boolean argsWritten;

        private LlmGeminiStream(
            LlmGeminiServer server)
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

        private boolean streaming()
        {
            return server.streaming;
        }

        private boolean events()
        {
            return server.streaming && server.sse;
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final OctetsFW extension = reset.extension();
            final LlmResetExFW resetEx = extension.get(llmResetExRO::tryWrap);

            state = StreamState.closeInitial(state);
            cleanupRequestSlot();

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

            drainRequestSlot(traceId);

            server.decodeNet(traceId, server.initialAuthorization, 0L);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            String model,
            String operation)
        {
            this.initialId = supplyInitialId.applyAsLong(server.exitId);
            this.replyId = supplyReplyId.applyAsLong(initialId);

            final byte[] marker = operation.getBytes(UTF_8);
            final LlmBeginExFW.Builder builder = llmBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .dialect(NAME);

            if (server.contentType != null)
            {
                builder.contentType(server.contentType);
            }

            final LlmBeginExFW llmBeginEx = builder.model(model).extension(e -> e.set(marker)).build();

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
            measureFraming();

            server.doNetBegin(traceId, authorization, affinity, STATUS_OK,
                events() ? CONTENT_TYPE_EVENT_STREAM : CONTENT_TYPE_JSON);

            beginEncode(0L, traceId, authorization);
            doEncodeBegin();
            doEncodeFlush();

            doAppWindow(traceId);
        }

        private void onAppData(
            DataFW data)
        {
            replySeq = data.sequence() + data.reserved();
            stagedReserved += data.reserved();

            stageReply(data.traceId(), data.authorization(), data.budgetId(), data.reserved(), data.flags(),
                data.extension(), data.payload() != null ? data.payload() : EMPTY_OCTETS);
            decodeReply();
        }

        private void stageReply(
            long traceId,
            long authorization,
            long budgetId,
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
                    .budgetId(budgetId)
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
            final long budgetId = frame.budgetId();
            final OctetsFW payload = frame.payload();

            replyFlags = flags;
            replyReserved = frame.reserved();
            replyFrameLength = frame.limit() - frame.offset();
            replyPayloadFrom = payload != null ? payload.offset() - frame.offset() : replyFrameLength;

            beginEncode(budgetId, traceId, authorization);

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

                if (kind == KIND_TOOL_CALL || kind == KIND_SERVER_TOOL_CALL)
                {
                    replyCopier.reset();
                    argsGenerator().reset();
                }

                dataDecoder = switch (kind)
                {
                case KIND_TEXT, KIND_REASONING, KIND_TOOL_CALL, KIND_SERVER_TOOL_CALL, KIND_SERVER_TOOL_RESULT, KIND_CONTROL ->
                    decodeReplyTextRoot;
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
            beginEncode(encodeBudgetId, encodeTraceId, encodeAuthorization);

            decoder = dataDecoder;

            final int offset = decodeSlotFrom;

            decodeSlotFrom = offset + replyFrameLength;

            decodeFrame(buffer, offset);
        }

        private void decodeFrame(
            DirectBufferEx buffer,
            int offset)
        {
            LlmGeminiReplyDecoder previous = null;

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
            LlmGeminiReplyDecoder state)
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

        private JsonGeneratorEx probe()
        {
            if (replyProbe == null)
            {
                replyProbe = JsonEx.createGenerator();
            }

            return replyProbe;
        }

        private JsonGeneratorEx argsGenerator()
        {
            if (argsGenerator == null)
            {
                argsGenerator = JsonEx.createGenerator();
            }

            return argsGenerator;
        }

        private LlmGeminiReplyDecoder memberState(
            CharSequence key)
        {
            LlmGeminiReplyDecoder next = decodeReplyTextSkip;

            switch (kind)
            {
            case KIND_CONTROL:
                next = controlMember(key);
                break;
            case KIND_TEXT, KIND_REASONING:
                if (MEMBER_TEXT.contentEquals(key))
                {
                    next = decodeReplyTextValue;
                }
                else if (MEMBER_SIGNATURE.contentEquals(key))
                {
                    next = decodeReplySignature;
                }
                break;
            case KIND_TOOL_CALL:
                next = toolCallMember(key);
                break;
            case KIND_SERVER_TOOL_CALL:
                next = serverToolCallMember(key);
                break;
            case KIND_SERVER_TOOL_RESULT:
                next = passMember(key);
                break;
            default:
                break;
            }

            return next;
        }

        private LlmGeminiReplyDecoder toolCallMember(
            CharSequence key)
        {
            LlmGeminiReplyDecoder next = decodeReplyTextSkip;

            if (MEMBER_ARGUMENTS.contentEquals(key))
            {
                valueMode = VALUE_ARGS;
                next = decodeReplyArgs;
            }
            else if (MEMBER_WIRE_ID.contentEquals(key))
            {
                next = decodeReplyId;
            }
            else
            {
                next = extraMember(key);
            }

            return next;
        }

        private LlmGeminiReplyDecoder serverToolCallMember(
            CharSequence key)
        {
            LlmGeminiReplyDecoder next = decodeReplyTextSkip;

            if (MEMBER_ARGUMENTS.contentEquals(key))
            {
                valueMode = VALUE_INLINE;
                next = decodeReplyArgs;
            }
            else if (MEMBER_WIRE_ID.contentEquals(key))
            {
                next = decodeReplyId;
            }
            else
            {
                next = extraMember(key);
            }

            return next;
        }

        private LlmGeminiReplyDecoder extraMember(
            CharSequence key)
        {
            LlmGeminiReplyDecoder next = decodeReplyTextSkip;

            if (memberKeys.select(key) == LlmMemberKeys.COPY && roomMember(memberKeys.target()))
            {
                closeCall();
                generator.writeKey(memberKeys.target().toString());
                replyCopier.reset();
                valueMode = VALUE_MEMBER;
                next = decodeReplyArgs;
            }

            return next;
        }

        private LlmGeminiReplyDecoder passMember(
            CharSequence key)
        {
            generator.writeKey(key.toString());
            replyCopier.reset();
            valueMode = VALUE_MEMBER;

            return decodeReplyArgs;
        }

        private boolean roomMember(
            CharSequence name)
        {
            return roomEvent((callClosed ? 0 : callCloseMax()) + RESPONSE_ESCAPE_EXPANSION * name.length() + ARGS_HEADROOM);
        }

        private LlmGeminiReplyDecoder afterValue()
        {
            return decodeReplyTextKey;
        }

        private int pullArgs(
            JsonParserEx parser,
            boolean resumed)
        {
            final boolean inline = valueMode == VALUE_INLINE;
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
                    final boolean container = event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY;
                    final boolean outer = inline && replyCopier.depth() == 0;

                    pending = false;

                    if (valueMode != VALUE_MEMBER && !(inline && argsOuter == 1) && replyCopier.depth() == 0 &&
                        event != JsonEvent.START_OBJECT)
                    {
                        replySkipDepth = event == JsonEvent.START_ARRAY ? 1 : 0;
                        decoder = event == JsonEvent.START_ARRAY ? decodeReplyTextSkip : decodeReplyTextKey;
                        outcome = ARGS_FULL;
                        stop = true;
                    }
                    else if (outer && event == JsonEvent.START_OBJECT)
                    {
                        argsOuter = 1;
                    }
                    else if (outer && event == JsonEvent.END_OBJECT)
                    {
                        argsOuter = 0;
                        complete = true;
                        stop = true;
                    }
                    else
                    {
                        if (!opened)
                        {
                            opened = true;
                            buffer = encodeBuffer();
                            limit = encodeLimit();

                            if (valueMode == VALUE_ARGS && replyCopier.depth() == 0)
                            {
                                generator.writeKey(MEMBER_ARGS);
                            }

                            if (!inline && replyCopier.depth() == 0)
                            {
                                generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.INCOMPLETE);
                            }

                            base = encodeLength();
                            argsGenerator().wrap(buffer, encodeBase + base, limit);
                        }

                        final JsonGeneratorEx args = inline ? generator : argsGenerator();
                        final int taken = replyCopier.copy(args, event, parser);

                        if (event == JsonEvent.END_OBJECT || event == JsonEvent.END_ARRAY)
                        {
                            complete = replyCopier.depth() == 0 && !inline;
                            stop = complete;
                        }
                        else if (valueMode == VALUE_MEMBER && !container && event != JsonEvent.KEY_NAME &&
                            replyCopier.depth() == 0 && taken == LlmEventCopier.NO_TAKEN)
                        {
                            complete = true;
                            stop = true;
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

                if (!inline)
                {
                    endArgs(base, buffer, limit);
                }

                if (complete && !inline)
                {
                    generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
                }

                if (full)
                {
                    doEncodeFlush();
                    beginEncode();
                }

                if (complete && valueMode != VALUE_MEMBER)
                {
                    argsWritten = true;
                }

                outcome = complete ? ARGS_DONE : holding ? ARGS_HELD : more ? ARGS_FULL : ARGS_IDLE;
            }
            else if (complete)
            {
                argsWritten = true;
                outcome = ARGS_DONE;
            }

            return outcome;
        }

        private boolean roomArgs()
        {
            final int need = ARGS_HEADROOM;

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
        }

        private int pullControl(
            JsonParserEx parser,
            boolean resumed)
        {
            final JsonGeneratorEx value = probe();
            final int room = extBuffer.capacity();

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

            return outcome;
        }

        private LlmGeminiReplyDecoder controlMember(
            CharSequence key)
        {
            LlmGeminiReplyDecoder next = decodeReplyTextSkip;
            final int action = memberKeys.select(key);

            if (action == LlmMemberKeys.COPY && controlMessage == NONE)
            {
                onEncodeControlBegin(memberKeys.target().toString());
                probe().reset();
                replyCopier.reset();
                next = decodeReplyControlValue;
            }
            else if (action == LlmMemberKeys.JUDGE)
            {
                judgedKey = key.toString();
                next = decodeReplyControlJudge;
            }

            return next;
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

        private void flushReplyControl(
            JsonGeneratorEx value)
        {
            final int length = value.length();

            if (length > 0 && controlMode == CONTROL_HELD)
            {
                onEncodeControlHeld(extBuffer, 0, length);
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

            stageReply(end.traceId(), end.authorization(), 0L, 0, REPLY_FLAG_END, end.extension(), EMPTY_OCTETS);

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

            beginEncode(0L, traceId, authorization);

            finishReasonName = geminiFinishReason(llmEndEx != null ? llmEndEx.finishReason().asString() : null);
            finishInputTokens = usage != null ? usage.inputTokens() : NONE;
            finishCacheReadTokens = usage != null ? usage.cacheReadTokens() : NONE;
            finishOutputTokens = usage != null ? usage.outputTokens() : NONE;
            finishReasoningTokens = usage != null ? usage.reasoningTokens() : NONE;
            finishTotalTokens = usage != null ? usage.totalTokens() : NONE;

            decoder = streaming() ? decodeReplyFinishChunk : decodeReplyFinishBody;

            decodeFinish();
        }

        private boolean finishing()
        {
            return decoder == decodeReplyFinishChunk || decoder == decodeReplyFinishBody ||
                decoder == decodeReplyFinishEnd;
        }

        private void decodeFinish()
        {
            LlmGeminiReplyDecoder previous = null;

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

        private int controlSize()
        {
            return hold.writeSize(0, CANDIDATE_ONLY) + hold.writeSize(0, RESPONSE_ONLY);
        }

        private int usageSize()
        {
            final int index = hold.indexOf(MEMBER_USAGE_METADATA);

            return index != NONE && hold.length(index) > 0 ? hold.writeSize(0, USAGE_ONLY) : usageMax;
        }

        private int finishNeed()
        {
            return (streaming() ? finishFraming : finishBodyFraming) + RESPONSE_ESCAPE_EXPANSION * finishReasonName.length() +
                controlSize() + usageSize();
        }

        private boolean holdFits()
        {
            return finishFraming + controlSize() + usageMax <= replyEncodePool.slotCapacity() - ENTRY_HEADER;
        }

        private void finishChunk()
        {
            if (roomFinish(finishNeed()))
            {
                if (events())
                {
                    eventBegin(encodeLength());
                }

                generator.writeStartObject();
                candidateBegin();
                generator.writeStartObject().write("text", "").writeEnd();
                candidateContentEnd();
                finishCandidate();

                if (events())
                {
                    eventEnd(encodeLength());
                }
                else
                {
                    generator.writeEnd();
                }

                decoder = decodeReplyFinishEnd;
            }
        }

        private void finishBody()
        {
            if (roomFinish(finishNeed()))
            {
                generator.writeEnd();
                generator.write("role", "model").writeEnd();
                finishCandidate();

                decoder = decodeReplyFinishEnd;
            }
        }

        private void finishCandidate()
        {
            generator.write("finishReason", finishReasonName);
            hold.write(generator, CANDIDATE_ONLY);
            generator.write("index", 0);
            candidateEnd();
            putUsage(generator);
            hold.write(generator, RESPONSE_ONLY);
            responseIdentity(generator);
            generator.writeEnd();
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final OctetsFW extension = abort.extension();

            replySeq = abort.sequence();
            state = StreamState.closeReply(state);

            cleanupDecodeSlot();
            cleanupEncodeSlot();
            cleanupHeld();

            server.doNetAbort(traceId, abort.authorization(), extension);

            if (StreamState.initialClosing(state) && !StreamState.initialClosed(state))
            {
                server.onDecodeEnd(traceId);
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
            cleanupRequestSlot();

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
            if (!StreamState.initialClosed(state))
            {
                int extLength = 0;

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

                    extLength = dataEx.sizeof();
                }

                if (enqueueData(traceId, authorization, flags, extLength, buffer, offset, length))
                {
                    drainRequestSlot(traceId);
                }
                else
                {
                    server.doNetReset(traceId);
                    doAppAbort(traceId);
                    server.cleanup(traceId);
                }
            }
        }

        private boolean enqueueData(
            long traceId,
            long authorization,
            int flags,
            int extLength,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final int required = REQUEST_RECORD_HEADER + extLength + length;

            if (requestSlot == NO_SLOT)
            {
                requestSlot = encodePool.acquire(initialId);
            }

            boolean encoded = requestSlot != NO_SLOT;

            if (encoded)
            {
                final MutableDirectBufferEx slot = encodePool.buffer(requestSlot);

                if (requestSlotOffset + required > encodePool.slotCapacity() && requestSlotProgress > 0)
                {
                    final int remaining = requestSlotOffset - requestSlotProgress;

                    slot.putBytes(0, slot, requestSlotProgress, remaining);
                    requestSlotOffset = remaining;
                    requestSlotProgress = 0;
                }

                encoded = requestSlotOffset + required <= encodePool.slotCapacity();

                if (encoded)
                {
                    slot.putLong(requestSlotOffset, traceId);
                    slot.putLong(requestSlotOffset + 8, authorization);
                    slot.putInt(requestSlotOffset + 16, flags);
                    slot.putInt(requestSlotOffset + 20, extLength);
                    slot.putInt(requestSlotOffset + 24, length);
                    slot.putBytes(requestSlotOffset + REQUEST_RECORD_HEADER, extBuffer, 0, extLength);
                    slot.putBytes(requestSlotOffset + REQUEST_RECORD_HEADER + extLength, buffer, offset, length);
                    requestSlotOffset += required;
                }
            }

            return encoded;
        }

        private void drainRequestSlot(
            long traceId)
        {
            while (requestSlot != NO_SLOT && requestSlotProgress < requestSlotOffset && !StreamState.initialClosed(state))
            {
                final MutableDirectBufferEx slot = encodePool.buffer(requestSlot);
                final int record = requestSlotProgress;
                final long recordTraceId = slot.getLong(record);
                final long recordAuthorization = slot.getLong(record + 8);
                final int flags = slot.getInt(record + 16);
                final int extLength = slot.getInt(record + 20);
                final int payloadLength = slot.getInt(record + 24);
                final int payloadOffset = record + REQUEST_RECORD_HEADER + extLength;

                final int remaining = payloadLength - requestHeadSent;
                final int length = (int) Math.min(remaining, Math.max(initialAvailable(), 0L));

                if (length < remaining && length == 0)
                {
                    break;
                }

                final boolean first = requestHeadSent == 0;
                final boolean last = length == remaining;
                final int sentFlags = (first ? flags & Flags.INIT : 0) | (last ? flags & Flags.FIN : 0);

                final DataFW.Builder builder = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .state(state)
                    .maximum(initialMax)
                    .traceId(recordTraceId)
                    .authorization(recordAuthorization)
                    .flags(sentFlags)
                    .budgetId(initialBud)
                    .reserved(length)
                    .payload(slot, payloadOffset + requestHeadSent, length);

                if (first && extLength > 0)
                {
                    builder.extension(slot, record + REQUEST_RECORD_HEADER, extLength);
                }

                final DataFW data = builder.build();

                app.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

                initialSeq += length;
                requestHeadSent += length;

                if (last)
                {
                    requestSlotProgress = payloadOffset + payloadLength;
                    requestHeadSent = 0;
                }
            }

            if (requestSlotProgress == requestSlotOffset || StreamState.initialClosed(state))
            {
                cleanupRequestSlot();
                server.flushNetEnd(traceId);
            }
        }

        private void cleanupRequestSlot()
        {
            if (requestSlot != NO_SLOT)
            {
                encodePool.release(requestSlot);
                requestSlot = NO_SLOT;
            }

            requestSlotOffset = 0;
            requestSlotProgress = 0;
            requestHeadSent = 0;
        }

        private int requestRoom()
        {
            final int room = encodePool.slotCapacity() - (requestSlotOffset - requestSlotProgress) - REQUEST_ENCODE_SLACK;

            return Math.max(room, 0) / REQUEST_CANONICAL_GROWTH;
        }

        private void beginEncode()
        {
            beginEncode(encodeBudgetId, encodeTraceId, encodeAuthorization);
        }

        private void beginEncode(
            long budgetId,
            long traceId,
            long authorization)
        {
            encodeBudgetId = budgetId;
            encodeTraceId = traceId;
            encodeAuthorization = authorization;
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
                server.cleanup(traceId);
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
                if (events())
                {
                    if (eventStart != NO_EVENT)
                    {
                        emitEvent(encodeLength(), false);
                        eventStart = NO_EVENT;
                    }
                }
                else if (encodeLength() > 0)
                {
                    writeEntry(encodeBase, encodeLength(), Flags.COMPLETE);
                }
            }

            flushEncodeSlot(encodeTraceId);
        }

        private void writeEntry(
            int start,
            int length,
            int flags)
        {
            final MutableDirectBufferEx buffer = replyEncodePool.buffer(encodeSlot);
            final int at = start - ENTRY_HEADER;

            buffer.putInt(at, length);
            buffer.putByte(at + ENTRY_FLAGS, (byte) flags);
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
                            slice + server.replyPad, EMPTY_OCTETS, buffer, payload + encodeSent - slice, slice);

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

        private void doEncodeBegin()
        {
            if (!streaming())
            {
                generator.writeStartObject();
                candidateBegin();
            }
            else if (!server.sse)
            {
                generator.writeStartArray();
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
                : message == MESSAGE_FIRST ? kindOf(type, NAME.equals(responseDialect)) : KIND_SKIPPED;

            signature = null;
            callId = toolCallId;
            callClosed = false;
            argsWritten = false;
            argsOuter = 0;

            switch (kind)
            {
            case KIND_TEXT, KIND_REASONING:
                sectionText();
                break;
            case KIND_TOOL_CALL:
                sectionToolCall(toolName);
                break;
            case KIND_SERVER_TOOL_CALL:
                sectionServerToolCall();
                break;
            case KIND_SERVER_TOOL_RESULT:
                sectionServerToolResult();
                break;
            case KIND_CONTROL:
                controlMessage = message;
                controlMode = CONTROL_DROP;
                break;
            default:
                break;
            }
        }

        private void sectionText()
        {
            if (!streaming())
            {
                generator.writeStartObject().writeKey("text");
                generator.writeStartEscaped();
            }
        }

        private void sectionToolCall(
            String toolName)
        {
            partBegin();
            generator.writeStartObject()
                .writeStartObject(MEMBER_FUNCTION_CALL);

            if (toolName != null)
            {
                generator.write("name", toolName);
            }
        }

        private void sectionServerToolCall()
        {
            partBegin();
            generator.writeStartObject()
                .writeStartObject(MEMBER_EXECUTABLE_CODE);
        }

        private void sectionServerToolResult()
        {
            partBegin();
            generator.writeStartObject();
        }

        private void partBegin()
        {
            if (streaming())
            {
                chunkStart();
            }
        }

        private int onEncodeText(
            JsonStringView chars,
            int length)
        {
            return streaming() ? emitTextChunk(chars, length) : writeText(chars, length);
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
                final int tail = kind == KIND_REASONING ? reasoningTail : textTail;
                final int need = chunkHead + tail + RESPONSE_ESCAPE_EXPANSION;

                if (generator.remaining() >= need)
                {
                    final MutableDirectBufferEx buffer = encodeBuffer();
                    final int limit = encodeLimit();

                    chunkStart();
                    generator.writeStartObject().writeKey("text");
                    generator.writeStartEscaped();

                    encodeShift = encodeLength();
                    generator.wrap(buffer, encodeBase + encodeShift, limit - tail);

                    final int before = generator.consumed();

                    generator.writeSegment(chars, taken, length);

                    taken += generator.consumed() - before;
                    progress = generator.consumed() > before;

                    encodeShift += generator.length();
                    generator.wrap(buffer, encodeBase + encodeShift, limit);

                    generator.writeEndEscaped();

                    if (kind == KIND_REASONING)
                    {
                        generator.write("thought", true);
                    }

                    generator.writeEnd();
                    chunkEnd();
                    doEncodeFlush();
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

        private int encodeLength()
        {
            return encodeShift + generator.length();
        }

        private void measureFraming()
        {
            final JsonGeneratorEx target = probe();

            target.wrap(extBuffer, 0, extBuffer.capacity());
            target.reset();

            target.writeStartObject();
            candidateBegin(target);
            target.writeStartObject().writeKey("text");
            target.writeStartEscaped();

            chunkHead = target.length();

            target.writeEndEscaped().writeEnd();
            chunkClose(target);

            textTail = target.length() - chunkHead;

            target.reset();

            target.writeStartObject();
            candidateBegin(target);
            target.writeStartObject().writeKey("text");
            target.writeStartEscaped();
            target.writeEndEscaped();
            target.write("thought", true).writeEnd();
            chunkClose(target);

            reasoningTail = target.length() - chunkHead;

            target.reset();

            target.writeStartObject();
            candidateBegin(target);
            target.writeStartObject().write("text", "").writeEnd();
            candidateContentEnd(target);
            target.write("finishReason", "").write("index", 0);
            candidateEnd(target);
            responseIdentity(target);
            target.writeEnd();

            finishFraming = target.length();

            target.reset();

            target.writeStartObject();
            candidateBegin(target);

            final int open = target.length();

            target.writeEnd();
            target.write("role", "model").writeEnd();
            target.write("finishReason", "").write("index", 0);
            candidateEnd(target);
            responseIdentity(target);
            target.writeEnd();

            finishBodyFraming = target.length() - open;

            target.reset();

            target.writeStartObject();
            candidateBegin(target);

            final int bareFrom = target.length();

            chunkClose(target);

            tailBare = target.length() - bareFrom;

            target.reset();

            target.writeStartObject();
            candidateBegin(target);
            target.writeStartObject().write("text", "").write(MEMBER_SIGNATURE_WIRE, "").writeEnd();
            chunkClose(target);

            signatureFraming = target.length();

            target.reset();

            target.writeStartObject();

            final int usageFrom = target.length();

            putUsage(target, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

            usageMax = target.length() - usageFrom;

            target.reset();
        }

        private void doEncodeEnd()
        {
            switch (kind)
            {
            case KIND_TEXT, KIND_REASONING:
                closeText();
                break;
            case KIND_TOOL_CALL, KIND_SERVER_TOOL_CALL:
                closeCall();
                closePart();
                break;
            case KIND_SERVER_TOOL_RESULT:
                closePart();
                break;
            default:
                break;
            }

            kind = KIND_NONE;
        }

        private int endNeed()
        {
            int need = 0;

            switch (kind)
            {
            case KIND_TEXT, KIND_REASONING:
                if (streaming())
                {
                    need = signature != null ? signatureFraming + signature.length() : 0;
                }
                else
                {
                    need = 2 + (kind == KIND_REASONING ? THOUGHT_MEMBER : 0) +
                        (signature != null ? SIGNATURE_MEMBER + signature.length() : 0);
                }
                break;
            case KIND_TOOL_CALL, KIND_SERVER_TOOL_CALL:
                need = (callClosed ? 0 : callCloseMax()) + 1 + (streaming() ? tailBare : 0);
                break;
            case KIND_SERVER_TOOL_RESULT:
                need = 1 + (streaming() ? tailBare : 0);
                break;
            default:
                break;
            }

            return need;
        }

        private int callCloseMax()
        {
            return (kind == KIND_TOOL_CALL && !argsWritten ? ARGS_EMPTY : 0) +
                (callId != null ? ID_MEMBER + RESPONSE_ESCAPE_EXPANSION * callId.length() : 0) + 1;
        }

        private boolean roomEnd()
        {
            final int need = endNeed();

            return need == 0 || roomEvent(need);
        }

        private void closeText()
        {
            if (signature != null && !LlmGeminiSignatureFromCanonical.plain(signature))
            {
                server.cleanup(encodeTraceId);
            }
            else if (!streaming())
            {
                writeTextEnd(generator);
            }
            else if (signature != null)
            {
                chunkStart();
                generator.writeStartObject().write("text", "").write(MEMBER_SIGNATURE_WIRE, signature).writeEnd();
                chunkEnd();
            }

            signature = null;
        }

        private void writeTextEnd(
            JsonGeneratorEx target)
        {
            if (!streaming())
            {
                target.writeEndEscaped();

                if (kind == KIND_REASONING)
                {
                    target.write("thought", true);
                }

                if (signature != null)
                {
                    target.write(MEMBER_SIGNATURE_WIRE, signature);
                }

                target.writeEnd();
            }
            else if (signature != null)
            {
                target.writeStartObject().write("text", "").write(MEMBER_SIGNATURE_WIRE, signature).writeEnd();
            }
        }

        private void writeCallClose()
        {
            if (kind == KIND_TOOL_CALL && !argsWritten)
            {
                generator.writeStartObject(MEMBER_ARGS).writeEnd();
            }

            if (callId != null)
            {
                generator.write("id", callId);
            }

            generator.writeEnd();
        }

        private void closeCall()
        {
            if (!callClosed)
            {
                writeCallClose();
                callClosed = true;
            }
        }

        private void closePart()
        {
            generator.writeEnd();

            if (streaming())
            {
                chunkEnd();
            }
        }

        private void onEncodeControlBegin(
            String name)
        {
            controlMode = controlMessage == NONE && hold.begin(name) ? CONTROL_HELD : CONTROL_DROP;
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
            if (controlMode == CONTROL_HELD)
            {
                hold.end();
            }

            controlMode = CONTROL_DROP;
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
            cleanupRequestSlot();
            cleanupHeld();
        }

        private void cleanupHeld()
        {
            hold.reset();
        }

        private void eventBegin(
            int position)
        {
            if (events())
            {
                this.eventPhase = EVENT_OPEN;
                this.eventStart = position;
            }
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

                writeEntry(encodeBase + eventStart, length, fin ? Flags.fin(init) : init);

                eventPhase = EVENT_STARTED;
            }
        }

        private void chunkStart()
        {
            eventBegin(encodeLength());
            generator.writeStartObject();
            candidateBegin();
        }

        private void chunkEnd()
        {
            chunkClose(generator);
            eventEnd(encodeLength());
        }

        private void candidateBegin()
        {
            candidateBegin(generator);
        }

        private void candidateBegin(
            JsonGeneratorEx target)
        {
            target.writeStartArray("candidates")
                .writeStartObject()
                .writeStartObject("content")
                .writeStartArray("parts");
        }

        private void candidateContentEnd(
            JsonGeneratorEx target)
        {
            target.writeEnd()
                .write("role", "model")
                .writeEnd();
        }

        private void candidateContentEnd()
        {
            candidateContentEnd(generator);
        }

        private void candidateEnd()
        {
            candidateEnd(generator);
        }

        private void candidateEnd(
            JsonGeneratorEx target)
        {
            target.writeEnd()
                .writeEnd();
        }

        private void chunkClose(
            JsonGeneratorEx target)
        {
            candidateContentEnd(target);
            target.write("index", 0);
            candidateEnd(target);
            responseIdentity(target);
            target.writeEnd();
        }

        private void responseIdentity(
            JsonGeneratorEx target)
        {
            if (responseModel != null)
            {
                target.write("modelVersion", responseModel);
            }

            if (responseId != null)
            {
                target.write("responseId", responseId);
            }
        }

        private void putUsage(
            JsonGeneratorEx target)
        {
            final int index = hold.indexOf(MEMBER_USAGE_METADATA);

            if (index != NONE && hold.length(index) > 0)
            {
                target.writeKey(MEMBER_USAGE_METADATA);
                target.writeRaw(hold.buffer(), hold.offset(index), hold.length(index));
            }
            else
            {
                putUsage(target, finishInputTokens, finishCacheReadTokens, finishOutputTokens, finishReasoningTokens,
                    finishTotalTokens);
            }
        }

        private void putUsage(
            JsonGeneratorEx target,
            int inputTokens,
            int cacheReadTokens,
            int outputTokens,
            int reasoningTokens,
            int totalTokens)
        {
            final int prompt = inputTokens != NONE ? inputTokens : 0;
            final int completion = outputTokens != NONE ? outputTokens : 0;
            final int thoughts = reasoningTokens != NONE ? reasoningTokens : 0;

            target.writeStartObject(MEMBER_USAGE_METADATA)
                .writeKey("promptTokenCount").write(prompt)
                .writeKey("candidatesTokenCount").write(completion - thoughts);

            if (cacheReadTokens != NONE)
            {
                target.writeKey("cachedContentTokenCount").write(cacheReadTokens);
            }

            if (reasoningTokens != NONE)
            {
                target.writeKey("thoughtsTokenCount").write(reasoningTokens);
            }

            target.writeKey("totalTokenCount").write(totalTokens != NONE ? totalTokens : prompt + completion)
                .writeEnd();
        }

        private String geminiFinishReason(
            String finishReason)
        {
            String reason = "STOP";

            if (finishReason != null)
            {
                reason = switch (finishReason)
                {
                case "stop", "tool-call" -> "STOP";
                case "length" -> "MAX_TOKENS";
                case "content-filter" -> "SAFETY";
                default -> NAME.equals(responseDialect) && finishReason.startsWith(VENDOR_PREFIX)
                    ? finishReason.substring(VENDOR_PREFIX.length())
                    : "OTHER";
                };
            }

            return reason;
        }

        private static int kindOf(
            LlmSectionType type,
            boolean sameDialect)
        {
            return switch (type)
            {
            case ASSISTANT_TEXT, ASSISTANT_REFUSAL -> KIND_TEXT;
            case ASSISTANT_REASONING -> KIND_REASONING;
            case TOOL_CALL -> KIND_TOOL_CALL;
            case SERVER_TOOL_CALL -> sameDialect ? KIND_SERVER_TOOL_CALL : KIND_SKIPPED;
            case SERVER_TOOL_RESULT -> sameDialect ? KIND_SERVER_TOOL_RESULT : KIND_SKIPPED;
            default -> KIND_SKIPPED;
            };
        }
    }


    private final class LlmGeminiRejectHandler
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

        private LlmGeminiRejectHandler(
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
