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
import io.aklivity.zilla.runtime.binding.llm.internal.types.queue.LlmQueueEntryFW;
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
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.util.Flags;

public final class LlmGeminiServerFactory implements LlmDialectHandler
{
    private static final String VENDOR_PREFIX = NAME + ":";
    private static final String HTTP_TYPE_NAME = "http";
    private static final int QUEUE_ENTRY_OVERHEAD = 5 * Integer.BYTES + 3 * Long.BYTES + Byte.BYTES;
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_PATH = ":path";
    private static final String HEADER_STATUS = ":status";
    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_API_KEY = "x-goog-api-key";
    private static final String METHOD_POST = "POST";
    private static final Pattern PATH_OPERATION =
        Pattern.compile("^/v1(?:beta)?/models/((?:[^/?%]|%[0-9A-Fa-f]{2})+):" +
            "(generateContent|streamGenerateContent)(?:\\?(.*))?$");
    private static final String OPERATION_STREAM = "streamGenerateContent";
    private static final String QUERY_ALT_SSE = "alt=sse";
    private static final String STATUS_OK = "200";
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_FORBIDDEN = 403;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_UNSUPPORTED_MEDIA_TYPE = 415;
    private static final int STATUS_INTERNAL_SERVER_ERROR = 500;
    private static final int NO_MAX_OUTPUT_TOKENS = -1;
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final Pattern CONTENT_TYPE = Pattern.compile("\\s*([^;\\s]+)\\s*(?:;.*)?");
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream;space=1";
    private static final Set<String> STATUSES = Set.of("INVALID_ARGUMENT", "FAILED_PRECONDITION", "OUT_OF_RANGE",
        "UNAUTHENTICATED", "PERMISSION_DENIED", "NOT_FOUND", "ABORTED", "ALREADY_EXISTS", "RESOURCE_EXHAUSTED", "CANCELLED",
        "DATA_LOSS", "UNKNOWN", "INTERNAL", "UNIMPLEMENTED", "UNAVAILABLE", "DEADLINE_EXCEEDED");

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
    private static final int MEDIA_TYPES = 4;
    private static final int TOOL_HOLD_MAX = 8192;
    private static final String TOOL_CALL_ID_PREFIX = "call_";
    private static final int TOOL_PART_MAX = 24576;
    private static final int BLOCK_BUFFER_MAX = 4096;

    private static final int RESPONSE_ENCODE_PADDING = 128;
    private static final int RESPONSE_ESCAPE_EXPANSION = 6;
    private static final int RESPONSE_SIGNATURE_RESERVE = LlmToolTransform.HELD_MAX +
        ",\"thought\":true,\"thoughtSignature\":\"\"".length();
    private static final int RESPONSE_ENCODE_SLACK = 2048 + RESPONSE_SIGNATURE_RESERVE;
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
    private static final DirectBufferEx PARTS_PREFIX = new UnsafeBufferEx("{\"contents\":[{\"parts\":[".getBytes(UTF_8));

    private static final int MIN_WINDOW = 64;
    private static final int CONTROL_GROWTH = 4;
    private static final int FRAMING_RESERVE = 16;
    private static final int TEXT_ESCAPE_EXPANSION = 6;
    private static final int CONTROL_OPEN_CAPACITY = 8;
    private static final int HOLD_MAX = 1024;

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

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final Matcher contentTypeMatcher;
    private final MutableDirectBufferEx copyBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
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
    private final LlmGeminiServerDecoder decodeRole = this::decodeRole;
    private final LlmGeminiServerDecoder decodeLookaheadParts = this::decodeLookaheadParts;
    private final LlmGeminiServerDecoder decodeLookaheadPart = this::decodeLookaheadPart;
    private final LlmGeminiServerDecoder decodeLookaheadContent = this::decodeLookaheadContent;
    private final LlmGeminiServerDecoder decodeLookaheadRole = this::decodeLookaheadRole;
    private final LlmGeminiServerDecoder decodeSystemStart = this::decodeSystemStart;
    private final LlmGeminiServerDecoder decodeSystem = this::decodeSystem;
    private final LlmGeminiServerDecoder decodePartsStart = this::decodePartsStart;
    private final LlmGeminiServerDecoder decodeParts = this::decodeParts;
    private final LlmGeminiServerDecoder decodePartKey = this::decodePartKey;
    private final LlmGeminiServerDecoder decodePart = this::decodePart;
    private final LlmGeminiServerDecoder decodePartThought = this::decodePartThought;
    private final LlmGeminiServerDecoder decodeToolsStart = this::decodeToolsStart;
    private final LlmGeminiServerDecoder decodeTools = this::decodeTools;
    private final LlmGeminiServerDecoder decodeToolKey = this::decodeToolKey;
    private final LlmGeminiServerDecoder decodeDeclarationsStart = this::decodeDeclarationsStart;
    private final LlmGeminiServerDecoder decodeDeclarations = this::decodeDeclarations;
    private final LlmGeminiServerDecoder decodeAttributes = this::decodeAttributes;
    private final LlmGeminiServerDecoder decodeTool = this::decodeTool;
    private final LlmGeminiServerDecoder decodeControlValue = this::decodeControlValue;
    private final LlmGeminiServerDecoder decodeConfigStart = this::decodeConfigStart;
    private final LlmGeminiServerDecoder decodeConfigFirst = this::decodeConfigFirst;
    private final LlmGeminiServerDecoder decodeConfigOpen = this::decodeConfigOpen;
    private final LlmGeminiServerDecoder decodeConfigClosed = this::decodeConfigClosed;
    private final LlmGeminiServerDecoder decodeConfigLeaf = this::decodeConfigLeaf;
    private final LlmGeminiServerDecoder decodeConfigResidue = this::decodeConfigResidue;
    private final LlmGeminiServerDecoder decodeConfigTokens = this::decodeConfigTokens;
    private final LlmGeminiServerDecoder decodeConfigMime = this::decodeConfigMime;
    private final LlmGeminiServerDecoder decodeConfigSchema = this::decodeConfigSchema;
    private final LlmGeminiServerDecoder decodeConfigConverted = this::decodeConfigConverted;
    private final LlmGeminiServerDecoder decodeSkip = this::decodeSkip;
    private final LlmGeminiServerDecoder decodeEnd = this::decodeEnd;
    private final LlmGeminiServerDecoder decodeIgnore = this::decodeIgnore;

    private byte[] pieceBytes;
    private UnsafeBufferEx pieceBuffer;

    public LlmGeminiServerFactory(
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.contentTypeMatcher = CONTENT_TYPE.matcher("");
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.decodeMax = decodePool.slotCapacity();
        this.copyBuffer = new UnsafeBufferEx(new byte[encodePool.slotCapacity()]);
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.bindings = new Long2ObjectHashMap<>();
        this.pieceBytes = new byte[1024];
        this.pieceBuffer = new UnsafeBufferEx(pieceBytes);
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
        return METHOD_POST.equalsIgnoreCase(header(headers, HEADER_METHOD)) &&
            operation(header(headers, HEADER_PATH)) != null;
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
            final LlmRequestHeaders headers = LlmRequestHeaders.of(httpBeginEx);
            final String contentType = header(headers, HEADER_CONTENT_TYPE);
            final Matcher operation = operation(header(headers, HEADER_PATH));

            if (operation == null)
            {
                newStream = new LlmGeminiRejectHandler(network, originId, routedId, initialId,
                    Integer.toString(STATUS_NOT_FOUND), errorBody(STATUS_NOT_FOUND, null, null))::onNetMessage;
            }
            else if (!CONTENT_TYPE_JSON.equals(mediaType(contentType)))
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
                    final LlmRouteConfig route = binding.resolve(authResult.authorization());

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
                                model(operation.group(1)),
                                operation.group(2),
                                operation.group(3),
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

    private static Matcher operation(
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
                if (parser.deferredBytes())
                {
                    break;
                }
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
                if (parser.deferredBytes())
                {
                    break;
                }
                final CharSequence key = parser.getStringView();
                if (isConfig(key))
                {
                    server.beginConfig(key);
                }
                else if (isRootControl(key))
                {
                    server.controlMore(key, decodeRootControl, decodeControlValue);
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

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                server.message++;
                server.role = null;
                server.context = CONTEXT_MESSAGE;
                server.decoder = decodeContent;
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
                if (parser.deferredBytes())
                {
                    break;
                }
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

    private int decodeLookaheadParts(
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
                server.decoder = decodeLookaheadPart;
                break;
            case END_ARRAY:
                server.decoder = decodeLookaheadContent;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodeLookaheadPart(
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
                if (parser.deferredBytes())
                {
                    break;
                }
                if (media(parser.getStringView()))
                {
                    server.replay(ROLE_USER, limit);
                }
                else
                {
                    server.skip(decodeLookaheadPart);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.decoder = decodeLookaheadParts;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodeLookaheadContent(
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
                if (parser.deferredBytes())
                {
                    break;
                }
                if (matches(parser.getStringView(), "role"))
                {
                    server.decoder = decodeLookaheadRole;
                }
                else
                {
                    server.skip(decodeLookaheadContent);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.replay(ROLE_USER, limit);
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodeLookaheadRole(
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

                if (ROLE_USER.equals(role) || ROLE_MODEL.equals(role))
                {
                    server.replay(role, limit);
                }
                else
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_ROLE_INVALID.formatted(role));
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
                if (parser.deferredBytes())
                {
                    break;
                }
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

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                server.hold();
                server.partThought = false;
                server.holdMax = Math.min(TOOL_HOLD_MAX, server.holdLimit());
                server.decoder = decodePartKey;
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

    private int decodePartKey(
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
                if (!parser.deferredBytes())
                {
                    final CharSequence key = parser.getStringView();

                    if (roleUnknown(server) && media(key))
                    {
                        server.role = ROLE_USER;
                    }

                    if (roleUnknown(server) && (matches(key, "text") || serverCall(key) || serverResult(key)))
                    {
                        server.lookahead();
                        server.skip(decodeLookaheadPart);
                        server.decoder = decodeSkip;
                    }
                    else if (matches(key, "text") && textSection(server) == LlmSectionType.ASSISTANT_TEXT && server.partThought)
                    {
                        server.captureHeld(LlmSectionType.ASSISTANT_REASONING, textMessage(server), decodeParts, 1);
                        server.decoder = decodePart;
                    }
                    else if (matches(key, "text") && textSection(server) == LlmSectionType.ASSISTANT_TEXT)
                    {
                        server.probe(LlmSectionType.ASSISTANT_TEXT, textMessage(server), decodeTool, decodeParts);
                        server.decoder = decodeAttributes;
                    }
                    else if (matches(key, "text"))
                    {
                        server.captureHeld(textSection(server), textMessage(server), decodeParts, 1);
                        server.decoder = decodePart;
                    }
                    else if (matches(key, MEMBER_THOUGHT))
                    {
                        server.decoder = decodePartThought;
                    }
                    else if (matches(key, MEMBER_THOUGHT_SIGNATURE) || matches(key, MEMBER_THOUGHT_SIGNATURE_SNAKE))
                    {
                        server.skip(decodePartKey);
                        server.decoder = decodeSkip;
                    }
                    else if (matches(key, MEMBER_FUNCTION_CALL))
                    {
                        server.probe(LlmSectionType.TOOL_CALL, textMessage(server), decodeTool, decodeParts);
                        server.decoder = decodeAttributes;
                    }
                    else if (matches(key, MEMBER_FUNCTION_RESPONSE))
                    {
                        server.probe(LlmSectionType.TOOL_RESULT, textMessage(server), decodeTool, decodeParts);
                        server.decoder = decodeAttributes;
                    }
                    else if (serverCall(key) && textSection(server) == LlmSectionType.ASSISTANT_TEXT)
                    {
                        server.probe(LlmSectionType.SERVER_TOOL_CALL, textMessage(server), decodeTool, decodeParts);
                        server.probe.attributes().toolName(SERVER_TOOL_NAME);
                        server.captureProbed();
                    }
                    else if (serverResult(key) && textSection(server) == LlmSectionType.ASSISTANT_TEXT)
                    {
                        server.probe(LlmSectionType.SERVER_TOOL_RESULT, textMessage(server), decodeTool, decodeParts);
                        server.captureProbed();
                    }
                    else if (serverCall(key) || serverResult(key))
                    {
                        server.onDecodeInvalidRequest(traceId, MESSAGE_BLOCK_INVALID);
                        break decode;
                    }
                    else if (media(key) && textSection(server) == LlmSectionType.USER_TEXT)
                    {
                        server.probe(LlmSectionType.USER_DOCUMENT, textMessage(server), decodeTool, decodeParts);
                        server.decoder = decodeAttributes;
                    }
                    else if (media(key))
                    {
                        server.onDecodeInvalidRequest(traceId, MESSAGE_BLOCK_INVALID);
                        break decode;
                    }
                    else
                    {
                        server.release();
                        server.skipDepth = 1;
                        server.skipThen = decodeParts;
                        server.decoder = decodeSkip;
                    }
                }
                break;
            case END_OBJECT:
                server.release();
                server.decoder = decodeParts;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodePartThought(
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
            case VALUE_TRUE:
                server.partThought = true;
                server.decoder = decodePartKey;
                break;
            case VALUE_FALSE:
                server.decoder = decodePartKey;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
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

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                server.hold();
                server.decoder = decodeToolKey;
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
                if (!parser.deferredBytes())
                {
                    if (matches(parser.getStringView(), MEMBER_FUNCTION_DECLARATIONS))
                    {
                        server.release();
                        server.decoder = decodeDeclarationsStart;
                    }
                    else if (server.holding)
                    {
                        server.probe(LlmSectionType.SERVER_TOOL_DEFINITION, -1, decodeTool, decodeTools);
                        server.probe.attributes().toolName(parser.getStringView().toString());
                        server.captureProbed();
                    }
                    else
                    {
                        server.onDecodeInvalidRequest(traceId, MESSAGE_TOOL_UNSUPPORTED);
                    }
                }
                break;
            case END_OBJECT:
                server.release();
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

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                server.hold();
                server.probe(LlmSectionType.TOOL_DEFINITION, -1, decodeTool, decodeDeclarations);
                server.decoder = decodeAttributes;
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

    private int decodeAttributes(
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

        while (server.decoder == decodeAttributes && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            switch (server.probe.onEvent(event, parser))
            {
            case RESOLVED:
                server.captureProbed();
                break;
            case COMPLETE:
                server.captureProbed();
                server.endCapture(traceId, authorization);
                break;
            case REJECTED:
                server.onDecodeInvalidRequest(traceId, MESSAGE_ATTRIBUTE_TOO_LARGE);
                break;
            default:
                if (server.position() - server.holdFrom > server.holdMax && server.probe.unclassified())
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_MEDIA_TYPE);
                }
                else if (server.position() - server.holdFrom > server.holdMax)
                {
                    server.captureProbed();
                }
                break;
            }
        }

        return server.decoder == decodeIgnore ? limit : server.position();
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
                if (parser.deferredBytes())
                {
                    break;
                }
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
                if (parser.deferredBytes())
                {
                    break;
                }
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
                if (parser.deferredBytes())
                {
                    break;
                }
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

    private int decodeConfigLeaf(
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

        while (server.decoder == decodeConfigLeaf && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = onCaptureEvent(server, event);

            if (server.captureDepth == 0 && !deferred)
            {
                server.endConfigValue(traceId, authorization, decodeConfigClosed);
            }
        }

        return server.position();
    }

    private int decodeConfigResidue(
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

        while (server.decoder == decodeConfigResidue && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = onCaptureEvent(server, event);

            if (server.captureDepth == 0 && !deferred)
            {
                server.endConfigValue(traceId, authorization, decodeConfigOpen);
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

    private int decodeConfigTokens(
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

        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (event == JsonEvent.VALUE_NUMBER && parser.deferredBytes())
            {
                parser.consumed(0);
            }
            else
            {
                final boolean deferred;

                if (event == JsonEvent.VALUE_NUMBER)
                {
                    server.maxOutputTokens = parser.getInt();
                    deferred = false;
                }
                else
                {
                    deferred = onCaptureEvent(server, event);
                }

                if (server.captureDepth == 0 && !deferred)
                {
                    server.endConfigValue(traceId, authorization, decodeConfigOpen);
                }
                else
                {
                    server.decoder = decodeConfigResidue;
                }
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

    private static boolean roleUnknown(
        LlmGeminiServer server)
    {
        return server.context == CONTEXT_MESSAGE && server.role == null;
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

    private void reservePiece(
        int capacity)
    {
        if (pieceBytes.length < capacity)
        {
            pieceBytes = new byte[capacity];
            pieceBuffer = new UnsafeBufferEx(pieceBytes);
        }
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
        private final LlmQueueEntryFW queueEntryRO = new LlmQueueEntryFW();
        private final LlmQueueEntryFW.Builder queueEntryRW = new LlmQueueEntryFW.Builder();
        private final Array32FW<HttpHeaderFW> eventHeadersRO = new Array32FW<>(new HttpHeaderFW());

        private LlmGeminiStream stream;
        private LlmGeminiServerDecoder decoder;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private boolean replyBegun;
        private int state;
        private boolean appEndPending;
        private boolean deauthorized;
        private boolean decoding;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private DirectBufferEx decodedInput;
        private int decodedWindowLimit;
        private boolean decodedLast;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private int encodeSlotRawOffset;
        private int encodeSent;
        private boolean flushingReply;

        private long pendingReplyEndTraceId;
        private long pendingReplyEndAuthorization;

        private int message;
        private String role;
        private int context;
        private LlmGeminiServerDecoder partsThen;

        private LlmGeminiServerDecoder skipThen;
        private int skipDepth;
        private boolean partThought;
        private String serverCallId;

        private boolean streamOptions;
        private int maxOutputTokens = NO_MAX_OUTPUT_TOKENS;

        private LlmGeminiServerDecoder captureThen;
        private boolean captureOpen;
        private int captureDepth;
        private int captureFrom;

        private boolean holding;
        private int holdFrom;
        private int retained;

        private final LlmSectionAttributes blockAttributes;
        private final LlmSectionAttributes sentAttributes;
        private final LlmSectionAttributes finAttributes;
        private final LlmGeminiAttributeProbe probe;
        private LlmGeminiServerDecoder probeCapture;
        private LlmGeminiServerDecoder probeThen;
        private int probeMessage;
        private int holdMax = HOLD_MAX;
        private String holdRejection;
        private boolean probing;

        private long decodeTraceId;
        private long decodeAuthorization;
        private LlmSectionType blockSection;
        private int blockMessage;
        private int blockFlags;

        private LlmSectionPipeline textPipeline;
        private LlmSectionPipeline assistantTextPipeline;
        private LlmSectionPipeline toolDefinitionPipeline;
        private LlmSectionPipeline toolCallPipeline;
        private LlmSectionPipeline serverToolCallPipeline;
        private final LlmSectionPipeline[] mediaPipelines = new LlmSectionPipeline[MEDIA_TYPES];
        private LlmSectionPipeline toolResultPipeline;
        private LlmGeminiToolResultToCanonical toolResultTransform;
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
            this.parser = JsonEx.createParser(Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED));
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
            state = LlmState.openingInitial(state);

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
            state = LlmState.closeInitial(state);
            state = LlmState.deferInitialEnd(state);

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
            if (!decoding && decodeSlot != NO_SLOT)
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
                decoding = true;

                try
                {
                    int previous;
                    do
                    {
                        previous = progress;
                        progress = decodeInput(traceId, authorization, budgetId, reserved, buffer, progress, limit);
                    }
                    while (progress != previous && progress < limit && decoder != decodeIgnore);
                }
                catch (JsonException ex)
                {
                    onDecodeParseError(traceId);
                }
                finally
                {
                    decoding = false;
                }
            }

            if (decoder != decodeIgnore)
            {
                if (progress < limit)
                {
                    final int length = limit - progress;

                    if (decodeSlot == NO_SLOT)
                    {
                        decodeSlot = decodePool.acquire(initialId);
                    }

                    if (decodeSlot == NO_SLOT)
                    {
                        doNetReset(traceId);
                        if (stream != null)
                        {
                            stream.doAppAbort(traceId);
                        }
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

                if (decoder != decodeIgnore)
                {
                    flushNetWindow(traceId);
                    flushNetEnd(traceId);
                }
            }
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
            decodedLast = LlmState.initialClosed(state);
            decodedWindowLimit = limit;

            if (decoder == decodeEnd)
            {
                progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, offset, progress, limit);
            }
            else if (decoder != decodeIgnore)
            {
                final int held = holding ? retained : 0;
                final int window = windowFor(offset, limit);

                if (window >= held + Math.min(MIN_WINDOW, limit - offset - held))
                {
                    progress = decodeWindow(traceId, authorization, budgetId, reserved, buffer, offset, offset + window, limit);
                }
            }

            return progress;
        }

        private int decodeWindow(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int windowLimit,
            int limit)
        {
            final boolean windowLast = decodedLast && windowLimit == limit;

            decodedWindowLimit = windowLimit;
            decodeTraceId = traceId;
            decodeAuthorization = authorization;
            captureFrom = offset;
            holdFrom = offset;
            parser.wrap(buffer, offset + retained, windowLimit, windowLast);

            LlmGeminiServerDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, offset, progress, limit);
            }

            if (captureOpen)
            {
                flushCapture(traceId, authorization, false);
                progress = position();
            }
            else if (holding)
            {
                retained = position() - holdFrom;
                progress = holdFrom;

                if (retained > holdMax && holdRejection != null)
                {
                    onDecodeInvalidRequest(traceId, holdRejection);
                    progress = limit;
                }
                else if (retained > holdMax)
                {
                    onDecodeParseError(traceId);
                    progress = limit;
                }
            }

            if (windowLast && decoder != decodeEnd && decoder != decodeIgnore)
            {
                onDecodeParseError(traceId);
                progress = limit;
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
            if (LlmState.initialEndDeferred(state) && decodeSlot == NO_SLOT && decoder != decodeIgnore &&
                (stream == null || stream.encodeSlot == NO_SLOT))
            {
                state = LlmState.clearInitialEndDeferred(state);
                onDecodeEnd(traceId);
            }
        }

        private int windowFor(
            int offset,
            int limit)
        {
            return (int) Math.min(limit - offset, Math.max(available() - FRAMING_RESERVE, 0) / CONTROL_GROWTH);
        }

        private int available()
        {
            final int credit = (int) Math.max(stream.initialAvailable(), 0L);
            final int available = Math.min(credit, stream.encodeRoom());
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

        private void probe(
            LlmSectionType type,
            int message,
            LlmGeminiServerDecoder capturing,
            LlmGeminiServerDecoder then)
        {
            probeMessage = message;
            probeCapture = capturing;
            probeThen = then;
            holdMax = switch (type)
            {
            case TOOL_CALL, TOOL_RESULT -> TOOL_HOLD_MAX;
            case USER_IMAGE, USER_DOCUMENT, USER_AUDIO, USER_VIDEO -> holdLimit();
            case ASSISTANT_TEXT -> Math.min(TOOL_HOLD_MAX, holdLimit());
            default -> HOLD_MAX;
            };
            probe.begin(type);
        }

        private int holdLimit()
        {
            final int room = (encodePool.slotCapacity() - REQUEST_ENCODE_SLACK) / REQUEST_CANONICAL_GROWTH;
            final int credit = Math.min(stream.initialMax, room);

            return Math.max(credit - FRAMING_RESERVE, 0) / CONTROL_GROWTH - MIN_WINDOW;
        }

        private void captureProbed()
        {
            if (probe.type() == LlmSectionType.TOOL_RESULT)
            {
                toolResultTransform().shape(probe.settle());
            }

            captureHeld(probe.type(), probeMessage, probeThen, probe.depth());
            probing = true;
            decoder = probeCapture;
        }

        private void lookahead()
        {
            holdMax = holdLimit();
            holdRejection = MESSAGE_CONTENT_SIZE;
        }

        private void replay(
            String resolved,
            int limit)
        {
            role = resolved;
            holdRejection = null;
            release();

            parser.reset();
            parser.wrap(PARTS_PREFIX, 0, PARTS_PREFIX.capacity(), false);
            while (parser.hasNextEvent())
            {
                parser.nextEvent();
            }
            parser.wrap(decodedInput, holdFrom, decodedWindowLimit, decodedLast && decodedWindowLimit == limit);

            decoder = decodeParts;
        }

        private void hold()
        {
            holding = true;
            holdFrom = position() - 1;
            retained = 0;
        }

        private void release()
        {
            holding = false;
            retained = 0;
        }

        private void captureHeld(
            LlmSectionType type,
            int blockMessage,
            LlmGeminiServerDecoder then,
            int depth)
        {
            onDecodeBlock(type, blockMessage);

            captureThen = then;
            captureOpen = true;
            captureDepth = depth;
            captureFrom = holdFrom;
            release();
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
                control(key, -1, decodeRootControl, decodeControlValue);
            }
        }

        private void controlMore(
            CharSequence key,
            LlmGeminiServerDecoder then,
            LlmGeminiServerDecoder value)
        {
            captureThen = then;

            if (isConverted(key))
            {
                controlConverted(key);
            }
            else
            {
                onDecodeControlKey(key, true, controlOpen());
            }

            captureOpen = true;
            captureDepth = 0;
            captureFrom = position();
            decoder = value;
        }

        private void controlConverted(
            CharSequence key)
        {
            final DirectBufferEx separator = Flags.hasInit(blockFlags) ? CONTROL_BEGIN : CONTROL_CONTINUE;

            onDecodeSectionData(decodeTraceId, decodeAuthorization, separator, 0, separator.capacity(), false);

            blockPipeline = toolConfigPipeline();
            blockPipeline.begin(blockAttributes);
            memberOutput.begin();

            onDecodeControlKey(key, false, '{');
        }

        private LlmSectionPipeline toolConfigPipeline()
        {
            if (toolConfigPipeline == null)
            {
                toolConfigTransform = new LlmGeminiControlToCanonical();
                toolConfigPipeline = new LlmSectionPipeline(toolConfigTransform, new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
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
                decoder = decodeConfigLeaf;
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
                decoder = matches(key, MEMBER_MAX_OUTPUT_TOKENS) || matches(key, MEMBER_MAX_OUTPUT_TOKENS_SNAKE)
                    ? decodeConfigTokens
                    : decodeConfigResidue;
            }

            captureOpen = true;
            captureDepth = 0;
            captureFrom = position();
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
            LlmGeminiServerDecoder then,
            LlmGeminiServerDecoder value)
        {
            onDecodeBlock(LlmSectionType.CONTROL, message);

            captureThen = then;

            if (isConverted(key))
            {
                controlConverted(key);
            }
            else
            {
                onDecodeControlKey(key, true, '{');
            }

            captureOpen = true;
            captureDepth = 0;
            captureFrom = position();
            decoder = value;
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
            holdMax = HOLD_MAX;
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

            blockPipeline = blockPipelineFor(type);

            if (blockPipeline != null)
            {
                blockPipeline.begin(LlmGeminiAttributeProbe.carries(type) ? probe.attributes() : blockAttributes);
            }
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
            reservePiece(controlKey.length() * TEXT_ESCAPE_EXPANSION + CONTROL_OPEN_CAPACITY);

            generator.wrap(pieceBuffer, 0, pieceBytes.length);
            generator.reset();
            generator.writeStartObject().writeKey(controlKey);

            pieceBuffer.putByte(0, (byte) open);

            onDecodeBlockData(decodeTraceId, decodeAuthorization, pieceBuffer, 0, generator.length(), false);
        }

        private LlmSectionPipeline blockPipelineFor(
            LlmSectionType type)
        {
            LlmSectionPipeline pipeline = null;

            switch (type)
            {
            case SYSTEM_INSTRUCTION, USER_TEXT, ASSISTANT_REASONING:
                if (textPipeline == null)
                {
                    textPipeline = new LlmSectionPipeline(new LlmGeminiTextToCanonical(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = textPipeline;
                break;
            case ASSISTANT_TEXT:
                if (assistantTextPipeline == null)
                {
                    assistantTextPipeline = new LlmSectionPipeline(new LlmGeminiTextToCanonical(true),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = assistantTextPipeline;
                break;
            case TOOL_RESULT:
                toolResultTransform();
                pipeline = toolResultPipeline;
                break;
            case TOOL_CALL:
                if (toolCallPipeline == null)
                {
                    toolCallPipeline = new LlmSectionPipeline(new LlmGeminiToolCallToCanonical(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = toolCallPipeline;
                break;
            case TOOL_DEFINITION:
                if (toolDefinitionPipeline == null)
                {
                    toolDefinitionPipeline = new LlmSectionPipeline(new LlmGeminiToolDefinitionToCanonical(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = toolDefinitionPipeline;
                break;
            case USER_IMAGE, USER_DOCUMENT, USER_AUDIO, USER_VIDEO:
                pipeline = mediaPipeline(type);
                break;
            case SERVER_TOOL_CALL:
                if (serverToolCallPipeline == null)
                {
                    serverToolCallPipeline = new LlmSectionPipeline(new LlmGeminiServerToolCallToCanonical(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = serverToolCallPipeline;
                break;
            default:
                break;
            }

            return pipeline;
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

        private LlmSectionPipeline mediaPipeline(
            LlmSectionType type)
        {
            final int index = switch (type)
            {
            case USER_IMAGE -> 0;
            case USER_AUDIO -> 1;
            case USER_VIDEO -> 2;
            default -> 3;
            };

            if (mediaPipelines[index] == null)
            {
                final LlmGeminiMediaToCanonical transform = switch (type)
                {
                case USER_IMAGE -> LlmGeminiMediaToCanonical.image();
                case USER_AUDIO -> LlmGeminiMediaToCanonical.audio();
                case USER_VIDEO -> LlmGeminiMediaToCanonical.video();
                default -> LlmGeminiMediaToCanonical.document();
                };

                mediaPipelines[index] = new LlmSectionPipeline(transform, new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return mediaPipelines[index];
        }

        private LlmGeminiToolResultToCanonical toolResultTransform()
        {
            if (toolResultTransform == null)
            {
                toolResultTransform = new LlmGeminiToolResultToCanonical();
                toolResultPipeline = new LlmSectionPipeline(toolResultTransform, new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return toolResultTransform;
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

                if (LlmState.replyClosed(stream.state))
                {
                    appEndPending = false;
                    stream.doAppEnd(traceId);
                }
                else
                {
                    appEndPending = true;
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
            release();

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
            doNetData(body, 0, body.capacity(), 0, Flags.COMPLETE, EMPTY_OCTETS, 0L, traceId, authorization);

            if (encodeSlot != NO_SLOT)
            {
                state = LlmState.deferReplyEnd(state);
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
            cleanupDecodeSlot();
            cleanupEncodeSlot();
            if (stream != null)
            {
                stream.cleanupEncodeSlot();
            }
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

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSent = 0;
            }
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
            state = LlmState.closeInitial(state);

            if (stream != null)
            {
                stream.doAppAbort(traceId);
            }
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
            state = LlmState.openReply(state);

            flushEncodeSlot(traceId);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            if (stream != null)
            {
                stream.doAppAbort(traceId);
            }
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
            OctetsFW payload,
            int flags,
            long budgetId,
            long traceId,
            long authorization)
        {
            doNetData(payload.buffer(), payload.offset(), payload.sizeof(), payload.sizeof(), flags, EMPTY_OCTETS, budgetId,
                traceId, authorization);
        }

        private void doNetData(
            DirectBufferEx buffer,
            int offset,
            int length,
            int rawLength,
            int flags,
            OctetsFW attributes,
            long budgetId,
            long traceId,
            long authorization)
        {
            final long available = replyMax - (replySeq - replyAck) - replyPad;

            if (encodeSlot == NO_SLOT && available > 0 && length <= available)
            {
                doNetData(traceId, authorization, flags, budgetId, length + replyPad, attributes, buffer, offset, length);
                stream.ackReply(traceId);
            }
            else
            {
                doNetEncode(buffer, offset, length, rawLength, flags, attributes, budgetId, traceId, authorization);
            }
        }

        private void doNetEncode(
            DirectBufferEx buffer,
            int offset,
            int length,
            int rawLength,
            int flags,
            OctetsFW attributes,
            long budgetId,
            long traceId,
            long authorization)
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(replyId);
            }

            if (encodeSlot == NO_SLOT ||
                encodeSlotOffset + QUEUE_ENTRY_OVERHEAD + attributes.sizeof() + length > encodePool.slotCapacity())
            {
                doNetReset(traceId);
                stream.doAppAbort(traceId);
                cleanup(traceId);
            }
            else
            {
                final MutableDirectBufferEx slotBuffer = encodePool.buffer(encodeSlot);
                encodeSlotOffset = queueEntryRW.wrap(slotBuffer, encodeSlotOffset, slotBuffer.capacity())
                    .extension(attributes.buffer(), attributes.offset(), attributes.sizeof())
                    .traceId(traceId)
                    .authorization(authorization)
                    .budgetId(budgetId)
                    .reserved(rawLength)
                    .flags(flags)
                    .payload(buffer, offset, length)
                    .build()
                    .limit();
                encodeSlotRawOffset += rawLength;

                flushEncodeSlot(traceId);
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
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .budgetId(budgetId)
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

        private void flushEncodeSlot(
            long traceId)
        {
            if (flushingReply || encodeSlot == NO_SLOT)
            {
                return;
            }

            flushingReply = true;
            try
            {
                final MutableDirectBufferEx buffer = encodePool.buffer(encodeSlot);
                while (encodeSlotOffset > 0)
                {
                    final long available = replyMax - (replySeq - replyAck) - replyPad;
                    if (available <= 0)
                    {
                        break;
                    }

                    final LlmQueueEntryFW entry = queueEntryRO.wrap(buffer, 0, encodeSlotOffset);
                    final OctetsFW payload = entry.payload();
                    final int remaining = payload.sizeof() - encodeSent;
                    final int sliceLength = (int) Math.min(remaining, available);
                    if (sliceLength <= 0 && remaining > 0)
                    {
                        break;
                    }

                    final int sliceOffset = payload.offset() + encodeSent;
                    final boolean init = encodeSent == 0 && Flags.hasInit(entry.flags());
                    encodeSent += sliceLength;

                    final int initFlags = init ? Flags.INIT : Flags.NONE;
                    final boolean last = encodeSent == payload.sizeof() && Flags.hasFin(entry.flags());
                    final int flags = last ? Flags.fin(initFlags) : initFlags;
                    doNetData(entry.traceId(), entry.authorization(), flags, entry.budgetId(),
                        sliceLength + replyPad, init ? entry.extension() : EMPTY_OCTETS, buffer, sliceOffset, sliceLength);

                    if (encodeSent >= payload.sizeof())
                    {
                        final int size = entry.sizeof();

                        encodeSlotRawOffset -= entry.reserved();
                        encodeSlotOffset -= size;
                        if (encodeSlotOffset > 0)
                        {
                            buffer.putBytes(0, buffer, size, encodeSlotOffset);
                        }
                        encodeSent = 0;
                    }
                }

                if (encodeSlotOffset == 0)
                {
                    encodePool.release(encodeSlot);
                    encodeSlot = NO_SLOT;
                    encodeSlotRawOffset = 0;
                }
            }
            finally
            {
                flushingReply = false;
            }

            if (stream != null)
            {
                stream.ackReply(traceId);
            }

            if (LlmState.replyEndDeferred(state) && encodeSlot == NO_SLOT)
            {
                state = LlmState.clearReplyEndDeferred(state);
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

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof())
                .build();

            network.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
            this.replyBegun = true;
            state = LlmState.openingReply(state);
        }

        private void doNetEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.replyClosed(state))
            {
                final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .extension(extension)
                    .build();

                network.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
                state = LlmState.closeReply(state);

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
            if (!LlmState.replyClosed(state))
            {
                final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .extension(extension)
                    .build();

                network.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
                state = LlmState.closeReply(state);

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
            if (!LlmState.initialClosed(state))
            {
                final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .build();

                network.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
                state = LlmState.closeInitial(state);
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
        private static final int EVENT_CAPACITY = 8;
        private static final int NO_EVENT = -1;

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
        private static final int CONTROL_MEMBERS_MAX = 8;
        private static final int CONTROL_HELD_MAX = 4096;
        private static final String MEMBER_USAGE_METADATA = "usageMetadata";
        private static final Set<String> CANDIDATE_MEMBERS = Set.of("finishMessage", "safetyRatings", "groundingMetadata",
            "urlContextMetadata", "citationMetadata", "avgLogprobs", "logprobsResult", "tokenCount");

        private static final int NONE = -1;
        private static final int MESSAGE_FIRST = 0;

        private final LlmGeminiServer server;
        private final JsonGeneratorEx generator;
        private final LlmTextPipeline sectionText;
        private final LlmSectionPipeline.Output encodeControl = (buffer, offset, length, last) ->
            onEncodeControl(buffer, offset, length);

        private LlmTextPipeline textPipeline;
        private LlmSectionPipeline controlPipeline;
        private LlmGeminiControlFromCanonical controlTransform;
        private LlmSectionPipeline toolCallPipeline;
        private LlmSectionPipeline serverToolCallPipeline;
        private LlmSectionPipeline serverToolResultPipeline;
        private LlmSectionPipeline signaturePipeline;
        private LlmGeminiSignatureFromCanonical signatureTransform;
        private UnsafeBufferEx partBuffer;
        private int partLength;
        private final LlmSectionPipeline.Output toolOutput = (buffer, offset, length, last) -> onToolPart(buffer, offset, length);
        private final LlmSectionPipeline.Output signatureOutput = (buffer, offset, length, last) ->
        {
        };
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

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private int encodeSlotProgress;
        private int encodeHeadSent;

        private boolean replyBegun;
        private boolean flushed;

        private String responseId;
        private String responseModel;
        private String responseDialect;

        private int[] eventOffsets;
        private int[] eventLengths;
        private int eventCount;
        private int eventStart;

        private int kind;

        private int controlMessage;
        private int controlMode;
        private boolean controlOpen;
        private String[] controlKeys;
        private int[] controlOffsets;
        private int[] controlLengths;
        private UnsafeBufferEx controlBuffer;
        private int controlCount;
        private int controlLimit;
        private int usageOffset;
        private int usageLength;

        private LlmGeminiStream(
            LlmGeminiServer server)
        {
            this.server = server;
            this.generator = JsonEx.createGenerator();
            this.eventOffsets = new int[EVENT_CAPACITY];
            this.eventLengths = new int[EVENT_CAPACITY];
            this.eventStart = NO_EVENT;
            this.sectionText = new LlmTextPipeline(this::onSectionText);
        }

        private long initialAvailable()
        {
            return initialMax - (initialSeq - initialAck);
        }

        private int encodeRoom()
        {
            final int room = encodePool.slotCapacity() - (encodeSlotOffset - encodeSlotProgress) - REQUEST_ENCODE_SLACK;

            return Math.max(room, 0) / REQUEST_CANONICAL_GROWTH;
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final OctetsFW extension = reset.extension();
            final LlmResetExFW resetEx = extension.get(llmResetExRO::tryWrap);

            state = LlmState.closeInitial(state);
            cleanupEncodeSlot();
            server.appEndPending = false;

            if (resetEx != null && !server.replyBegun)
            {
                server.doNetError(traceId, authorization, resetEx.error());
            }
            else
            {
                server.doNetReset(traceId);
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
            state = LlmState.openInitial(state);

            drainEncodeSlot(traceId);

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

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(0L)
                .extension(llmBeginEx.buffer(), llmBeginEx.offset(), llmBeginEx.sizeof())
                .build();

            app = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(),
                this::onAppMessage);
            app.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

            state = LlmState.openingInitial(state);
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
            state = LlmState.openingReply(state);

            replyMax = (encodePool.slotCapacity() - RESPONSE_ENCODE_SLACK) / RESPONSE_ESCAPE_EXPANSION;

            responseId = llmBeginEx != null ? llmBeginEx.id().asString() : null;
            responseModel = llmBeginEx != null ? llmBeginEx.model().asString() : null;
            responseDialect = llmBeginEx != null ? llmBeginEx.dialect().asString() : null;
            replyBegun = true;

            server.doNetBegin(traceId, authorization, affinity, STATUS_OK,
                server.streaming && server.sse ? CONTENT_TYPE_EVENT_STREAM : CONTENT_TYPE_JSON);

            beginEncode();
            doEncodeBegin();
            doEncodeFlush(0, 0L, traceId, authorization);

            doAppWindow(traceId);
        }

        private void onAppData(
            DataFW data)
        {
            replySeq = data.sequence() + data.reserved();

            onAppSection(data);
        }

        private void onAppSection(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload() != null ? data.payload() : EMPTY_OCTETS;

            beginEncode();

            if (Flags.hasInit(flags))
            {
                final LlmDataExFW llmDataEx = data.extension().get(llmDataExRO::tryWrap);
                final LlmSectionDataExFW section = llmDataEx != null ? llmDataEx.section() : null;
                final LlmSectionType type = section != null ? section.type().get() : LlmSectionType.NONE;

                replyAttributes.readFrom(section);

                doEncodeSection(type, section != null ? section.message() : -1);
            }
            else if (data.extension().sizeof() > 0)
            {
                final LlmDataExFW llmDataEx = data.extension().get(llmDataExRO::tryWrap);

                if (llmDataEx != null)
                {
                    replyAttributes.mergeFrom(llmDataEx.section());
                }
            }

            doEncodeData(traceId, payload.buffer(), payload.offset(), payload.sizeof(), Flags.hasFin(flags));

            if (Flags.hasFin(flags))
            {
                doEncodeEnd();
            }

            if (generator.length() > 0)
            {
                doEncodeFlush(reserved, budgetId, traceId, authorization);
            }
            else
            {
                ackReply(traceId);
            }
        }

        private void ackReply(
            long traceId)
        {
            final long replyAckMax = replySeq - server.encodeSlotRawOffset;

            if (replyAckMax > replyAck)
            {
                replyAck = replyAckMax;
                doAppWindow(traceId);
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            final OctetsFW extension = end.extension();

            replySeq = end.sequence();
            state = LlmState.closeReply(state);

            final LlmEndExFW llmEndEx = extension.get(llmEndExRO::tryWrap);
            final LlmUsageFW usage = llmEndEx != null ? llmEndEx.usage() : null;

            beginEncode();

            doEncodeEnd();
            doEncodeFinish(
                llmEndEx != null ? llmEndEx.finishReason().asString() : null,
                usage != null ? usage.inputTokens() : NONE,
                usage != null ? usage.cacheReadTokens() : NONE,
                usage != null ? usage.outputTokens() : NONE,
                usage != null ? usage.reasoningTokens() : NONE,
                usage != null ? usage.totalTokens() : NONE);

            doEncodeFlush(0, 0L, traceId, authorization);

            if (server.encodeSlot != NO_SLOT)
            {
                server.state = LlmState.deferReplyEnd(server.state);
                server.pendingReplyEndTraceId = traceId;
                server.pendingReplyEndAuthorization = authorization;
            }
            else
            {
                server.doNetEnd(traceId, authorization, EMPTY_OCTETS);
            }

            if (server.appEndPending)
            {
                server.onDecodeEnd(traceId);
            }
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final OctetsFW extension = abort.extension();

            replySeq = abort.sequence();
            state = LlmState.closeReply(state);

            server.doNetAbort(traceId, abort.authorization(), extension);

            if (server.appEndPending)
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
            if (!flushed && !LlmState.initialClosed(state))
            {
                flushed = true;

                final FlushFW.Builder builder = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
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
            if (!LlmState.initialClosed(state))
            {
                final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .build();

                app.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
                state = LlmState.closeInitial(state);
            }
        }

        private void doAppAbort(
            long traceId)
        {
            cleanupEncodeSlot();

            if (!LlmState.initialClosed(state))
            {
                final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .build();

                app.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
                state = LlmState.closeInitial(state);
            }
        }

        private void doAppReset(
            long traceId)
        {
            if (!LlmState.replyClosed(state))
            {
                final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .build();

                app.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
                state = LlmState.closeReply(state);
            }
        }

        private void doAppWindow(
            long traceId)
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .budgetId(0L)
                .padding(RESPONSE_ENCODE_PADDING)
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
            if (!LlmState.initialClosed(state))
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
                    drainEncodeSlot(traceId);
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

            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(initialId);
            }

            boolean encoded = encodeSlot != NO_SLOT;

            if (encoded)
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);

                if (encodeSlotOffset + required > encodePool.slotCapacity() && encodeSlotProgress > 0)
                {
                    final int remaining = encodeSlotOffset - encodeSlotProgress;

                    slot.putBytes(0, slot, encodeSlotProgress, remaining);
                    encodeSlotOffset = remaining;
                    encodeSlotProgress = 0;
                }

                encoded = encodeSlotOffset + required <= encodePool.slotCapacity();

                if (encoded)
                {
                    slot.putLong(encodeSlotOffset, traceId);
                    slot.putLong(encodeSlotOffset + 8, authorization);
                    slot.putInt(encodeSlotOffset + 16, flags);
                    slot.putInt(encodeSlotOffset + 20, extLength);
                    slot.putInt(encodeSlotOffset + 24, length);
                    slot.putBytes(encodeSlotOffset + REQUEST_RECORD_HEADER, extBuffer, 0, extLength);
                    slot.putBytes(encodeSlotOffset + REQUEST_RECORD_HEADER + extLength, buffer, offset, length);
                    encodeSlotOffset += required;
                }
            }

            return encoded;
        }

        private void drainEncodeSlot(
            long traceId)
        {
            while (encodeSlot != NO_SLOT && encodeSlotProgress < encodeSlotOffset && !LlmState.initialClosed(state))
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final int record = encodeSlotProgress;
                final long recordTraceId = slot.getLong(record);
                final long recordAuthorization = slot.getLong(record + 8);
                final int flags = slot.getInt(record + 16);
                final int extLength = slot.getInt(record + 20);
                final int payloadLength = slot.getInt(record + 24);
                final int payloadOffset = record + REQUEST_RECORD_HEADER + extLength;

                final int remaining = payloadLength - encodeHeadSent;
                final int length = (int) Math.min(remaining, Math.max(initialAvailable(), 0L));

                if (length < remaining && length == 0)
                {
                    break;
                }

                final boolean first = encodeHeadSent == 0;
                final boolean last = length == remaining;
                final int sentFlags = (first ? flags & Flags.INIT : 0) | (last ? flags & Flags.FIN : 0);

                final DataFW.Builder builder = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(recordTraceId)
                    .authorization(recordAuthorization)
                    .flags(sentFlags)
                    .budgetId(initialBud)
                    .reserved(length)
                    .payload(slot, payloadOffset + encodeHeadSent, length);

                if (first && extLength > 0)
                {
                    builder.extension(slot, record + REQUEST_RECORD_HEADER, extLength);
                }

                final DataFW data = builder.build();

                app.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

                initialSeq += length;
                encodeHeadSent += length;

                if (last)
                {
                    encodeSlotProgress = payloadOffset + payloadLength;
                    encodeHeadSent = 0;
                }
            }

            if (encodeSlotProgress == encodeSlotOffset || LlmState.initialClosed(state))
            {
                cleanupEncodeSlot();
                server.flushNetEnd(traceId);
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
            }

            encodeSlotOffset = 0;
            encodeSlotProgress = 0;
            encodeHeadSent = 0;
        }


        private void beginEncode()
        {
            generator.wrap(copyBuffer, 0, copyBuffer.capacity());
            eventStart = NO_EVENT;
        }

        private void doEncodeFlush(
            int reserved,
            long budgetId,
            long traceId,
            long authorization)
        {
            if (server.streaming && server.sse)
            {
                final int count = eventCount;

                for (int index = 0; index < count; index++)
                {
                    server.doNetData(copyBuffer, eventOffsets[index], eventLengths[index], index == count - 1 ? reserved : 0,
                        Flags.COMPLETE, EMPTY_OCTETS, budgetId, traceId, authorization);
                }

                eventCount = 0;

                if (count == 0)
                {
                    ackReply(traceId);
                }
            }
            else if (generator.length() > 0)
            {
                server.doNetData(copyBuffer, 0, generator.length(), reserved, Flags.COMPLETE, EMPTY_OCTETS, budgetId, traceId,
                    authorization);
            }
            else
            {
                ackReply(traceId);
            }
        }

        private void doEncodeBegin()
        {
            if (!server.streaming)
            {
                generator.writeStartObject()
                    .writeStartArray("candidates")
                    .writeStartObject()
                    .writeStartObject("content")
                    .writeStartArray("parts");
            }
            else if (!server.sse)
            {
                generator.writeStartArray();
            }
        }

        private void doEncodeSection(
            LlmSectionType type,
            int message)
        {
            kind = type == LlmSectionType.CONTROL
                ? KIND_CONTROL
                : message == MESSAGE_FIRST ? kindOf(type, NAME.equals(responseDialect)) : KIND_SKIPPED;

            if (kind == KIND_CONTROL)
            {
                controlMessage = message;
                controlOpen = false;
                controlPipeline().begin(replyAttributes);
            }

            if (part(kind))
            {
                partLength = 0;
                partPipeline().begin(replyAttributes);
            }

            if (kind == KIND_TEXT || kind == KIND_REASONING)
            {
                sectionText.begin();
                textPipeline = sectionText;
                signaturePipeline().begin(replyAttributes);

                if (!server.streaming)
                {
                    generator.writeStartObject().writeKey("text");
                    generator.writeStartEscaped();
                }
            }
            else
            {
                textPipeline = null;
            }
        }

        private void doEncodeData(
            long traceId,
            DirectBufferEx payload,
            int payloadOffset,
            int payloadLength,
            boolean fin)
        {
            if (kind == KIND_CONTROL)
            {
                if (!controlPipeline.feed(payload, payloadOffset, payloadLength, fin, encodeControl))
                {
                    server.cleanup(traceId);
                }
            }
            else if (part(kind))
            {
                if (!partPipeline().feed(payload, payloadOffset, payloadLength, fin, toolOutput))
                {
                    server.cleanup(traceId);
                }
            }
            else if (textPipeline != null && (!textPipeline.feed(payload, payloadOffset, payloadLength, fin) ||
                !signaturePipeline.feed(payload, payloadOffset, payloadLength, fin, signatureOutput)))
            {
                server.cleanup(traceId);
            }
        }

        private LlmSectionPipeline signaturePipeline()
        {
            if (signaturePipeline == null)
            {
                signatureTransform = new LlmGeminiSignatureFromCanonical();
                signaturePipeline = new LlmSectionPipeline(signatureTransform, new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return signaturePipeline;
        }

        private static boolean part(
            int kind)
        {
            return kind == KIND_TOOL_CALL || kind == KIND_SERVER_TOOL_CALL || kind == KIND_SERVER_TOOL_RESULT;
        }

        private LlmSectionPipeline partPipeline()
        {
            return switch (kind)
            {
            case KIND_SERVER_TOOL_CALL -> serverToolCallPipeline();
            case KIND_SERVER_TOOL_RESULT -> serverToolResultPipeline();
            default -> toolCallPipeline();
            };
        }

        private LlmSectionPipeline toolCallPipeline()
        {
            if (toolCallPipeline == null)
            {
                toolCallPipeline = new LlmSectionPipeline(new LlmGeminiToolCallFromCanonical(NAME.equals(responseDialect)),
                    new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return toolCallPipeline;
        }

        private LlmSectionPipeline serverToolCallPipeline()
        {
            if (serverToolCallPipeline == null)
            {
                serverToolCallPipeline = new LlmSectionPipeline(
                    new LlmGeminiServerToolCallFromCanonical(NAME.equals(responseDialect)),
                    new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return serverToolCallPipeline;
        }

        private LlmSectionPipeline serverToolResultPipeline()
        {
            if (serverToolResultPipeline == null)
            {
                serverToolResultPipeline = new LlmSectionPipeline(new LlmGeminiServerToolFromCanonical(),
                    new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return serverToolResultPipeline;
        }

        private void onToolPart(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (!server.streaming)
            {
                generator.writeSegment(buffer, offset, length, Completion.INCOMPLETE);
            }
            else if (partBuffer == null && length <= TOOL_PART_MAX ||
                partBuffer != null && partLength + length <= partBuffer.capacity())
            {
                if (partBuffer == null)
                {
                    partBuffer = new UnsafeBufferEx(new byte[TOOL_PART_MAX]);
                }

                partBuffer.putBytes(partLength, buffer, offset, length);
                partLength += length;
            }
            else
            {
                server.cleanup(server.decodeTraceId);
            }
        }

        private void doEncodeEnd()
        {
            if (kind == KIND_CONTROL)
            {
                onEncodeControlEnd();
            }
            else if (kind == KIND_TEXT || kind == KIND_REASONING)
            {
                final String signature = signatureTransform.signature();

                if (signature != null && !LlmGeminiSignatureFromCanonical.plain(signature))
                {
                    server.cleanup(server.decodeTraceId);
                }
                else if (!server.streaming)
                {
                    generator.writeEndEscaped();

                    if (kind == KIND_REASONING)
                    {
                        generator.write("thought", true);
                    }

                    if (signature != null)
                    {
                        generator.write("thoughtSignature", signature);
                    }

                    generator.writeEnd();
                }
                else if (signature != null)
                {
                    onSignaturePart(signature);
                }
            }
            else if (part(kind))
            {
                onToolPartEnd();
            }

            kind = KIND_NONE;
            textPipeline = null;
        }

        private void onToolPartEnd()
        {
            if (!server.streaming)
            {
                generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
            }
            else
            {
                chunkBegin();
                generator.writeStartObject();
                generator.writeStartArray("candidates")
                    .writeStartObject()
                    .writeStartObject("content")
                    .writeStartArray("parts");
                generator.writeRaw(partBuffer, 0, partLength);
                candidateContentEnd();
                generator.write("index", 0);
                candidateEnd();
                responseIdentity();
                generator.writeEnd();
                chunkEnd();
            }
        }

        private void onSignaturePart(
            String signature)
        {
            chunkBegin();
            generator.writeStartObject();
            candidateBegin();
            generator.writeStartObject().write("text", "").write("thoughtSignature", signature).writeEnd();
            candidateContentEnd();
            generator.write("index", 0);
            candidateEnd();
            responseIdentity();
            generator.writeEnd();
            chunkEnd();
        }

        private void onSectionText(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (!server.streaming)
            {
                generator.writeSegment(buffer, offset, length);
            }
            else if (length > 0)
            {
                chunkBegin();
                generator.writeStartObject();
                candidateBegin();
                generator.writeStartObject().writeKey("text");
                generator.writeStartEscaped();
                generator.writeSegment(buffer, offset, length);
                generator.writeEndEscaped();

                if (kind == KIND_REASONING)
                {
                    generator.write("thought", true);
                }

                generator.writeEnd();
                candidateContentEnd();
                generator.write("index", 0);
                candidateEnd();
                responseIdentity();
                generator.writeEnd();
                chunkEnd();
            }
        }

        private void candidateBegin()
        {
            generator.writeStartArray("candidates")
                .writeStartObject()
                .writeStartObject("content")
                .writeStartArray("parts");
        }

        private void candidateContentEnd()
        {
            generator.writeEnd()
                .write("role", "model")
                .writeEnd();
        }

        private void candidateEnd()
        {
            generator.writeEnd()
                .writeEnd();
        }

        private void responseIdentity()
        {
            if (responseModel != null)
            {
                generator.write("modelVersion", responseModel);
            }

            if (responseId != null)
            {
                generator.write("responseId", responseId);
            }
        }

        private void doEncodeFinish(
            String finishReason,
            int inputTokens,
            int cacheReadTokens,
            int outputTokens,
            int reasoningTokens,
            int totalTokens)
        {
            if (server.streaming)
            {
                chunkBegin();
                generator.writeStartObject();
                candidateBegin();
                generator.writeStartObject().write("text", "").writeEnd();
                candidateContentEnd();
            }
            else
            {
                generator.writeEnd();
                generator.write("role", "model").writeEnd();
            }

            generator.write("finishReason", geminiFinishReason(finishReason));
            writeControls(true);
            generator.write("index", 0);
            candidateEnd();
            putUsage(inputTokens, cacheReadTokens, outputTokens, reasoningTokens, totalTokens);
            writeControls(false);
            responseIdentity();
            generator.writeEnd();

            if (server.streaming)
            {
                chunkEnd();

                if (!server.sse)
                {
                    generator.writeEnd();
                }
            }
        }

        private void writeControls(
            boolean candidate)
        {
            for (int index = 0; index < controlCount; index++)
            {
                if (CANDIDATE_MEMBERS.contains(controlKeys[index]) == candidate)
                {
                    generator.writeKey(controlKeys[index]);
                    generator.writeRaw(controlBuffer, controlOffsets[index], controlLengths[index]);
                }
            }
        }

        private void putUsage(
            int inputTokens,
            int cacheReadTokens,
            int outputTokens,
            int reasoningTokens,
            int totalTokens)
        {
            if (usageLength > 0)
            {
                generator.writeKey(MEMBER_USAGE_METADATA);
                generator.writeRaw(controlBuffer, usageOffset, usageLength);
            }
            else
            {
                final int prompt = inputTokens != NONE ? inputTokens : 0;
                final int completion = outputTokens != NONE ? outputTokens : 0;
                final int thoughts = reasoningTokens != NONE ? reasoningTokens : 0;

                generator.writeStartObject(MEMBER_USAGE_METADATA)
                    .writeKey("promptTokenCount").write(prompt)
                    .writeKey("candidatesTokenCount").write(completion - thoughts);

                if (cacheReadTokens != NONE)
                {
                    generator.writeKey("cachedContentTokenCount").write(cacheReadTokens);
                }

                if (reasoningTokens != NONE)
                {
                    generator.writeKey("thoughtsTokenCount").write(reasoningTokens);
                }

                generator.writeKey("totalTokenCount").write(totalTokens != NONE ? totalTokens : prompt + completion)
                    .writeEnd();
            }
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

        private LlmSectionPipeline controlPipeline()
        {
            if (controlPipeline == null)
            {
                controlTransform = new LlmGeminiControlFromCanonical(NAME.equals(responseDialect));
                controlPipeline = new LlmSectionPipeline(controlTransform, new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return controlPipeline;
        }

        private void onEncodeControl(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            final String name = controlTransform.member();

            if (name != null)
            {
                if (!controlOpen)
                {
                    controlOpen = true;
                    onEncodeControlBegin(name);
                }

                if (controlMode == CONTROL_HELD)
                {
                    onEncodeControlHeld(payload, offset, length);
                }
            }
        }

        private void onEncodeControlBegin(
            String name)
        {
            controlMode = controlMessage == NONE ? CONTROL_HELD : CONTROL_DROP;

            if (controlMode == CONTROL_HELD)
            {
                if (controlKeys == null)
                {
                    controlKeys = new String[CONTROL_MEMBERS_MAX];
                    controlOffsets = new int[CONTROL_MEMBERS_MAX];
                    controlLengths = new int[CONTROL_MEMBERS_MAX];
                    controlBuffer = new UnsafeBufferEx(new byte[CONTROL_HELD_MAX]);
                }

                if (controlCount < CONTROL_MEMBERS_MAX)
                {
                    controlKeys[controlCount] = name;
                    controlOffsets[controlCount] = controlLimit;
                    controlLengths[controlCount] = 0;
                }
                else
                {
                    controlMode = CONTROL_DROP;
                }
            }
        }

        private void onEncodeControlHeld(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            if (controlLimit + length <= CONTROL_HELD_MAX)
            {
                controlBuffer.putBytes(controlLimit, payload, offset, length);
                controlLimit += length;
                controlLengths[controlCount] += length;
            }
            else
            {
                controlLimit = controlOffsets[controlCount];
                controlMode = CONTROL_DROP;
            }
        }

        private void onEncodeControlEnd()
        {
            if (controlMode == CONTROL_HELD)
            {
                if (MEMBER_USAGE_METADATA.equals(controlKeys[controlCount]))
                {
                    usageOffset = controlOffsets[controlCount];
                    usageLength = controlLengths[controlCount];
                }
                else
                {
                    controlCount++;
                }
            }

            controlMode = CONTROL_DROP;
        }

        private void chunkBegin()
        {
            if (server.sse)
            {
                eventStart = generator.length();
            }
        }

        private void chunkEnd()
        {
            if (server.sse)
            {
                if (eventCount == eventOffsets.length)
                {
                    final int capacity = eventCount << 1;
                    eventOffsets = Arrays.copyOf(eventOffsets, capacity);
                    eventLengths = Arrays.copyOf(eventLengths, capacity);
                }

                eventOffsets[eventCount] = eventStart;
                eventLengths[eventCount] = generator.length() - eventStart;
                eventCount++;

                generator.reset();
            }
        }
    }

    private String mediaType(
        String contentType)
    {
        contentTypeMatcher.reset(contentType != null ? contentType : "");

        return contentTypeMatcher.matches() ? contentTypeMatcher.group(1) : null;
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
            initialSeq = end.sequence();
            initialAck = initialSeq;
        }

        private void onNetAbort(
            AbortFW abort)
        {
            initialSeq = abort.sequence();
            initialAck = initialSeq;
        }

        private void onNetWindow(
            WindowFW window)
        {
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
            final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .extension(EMPTY_OCTETS)
                .build();

            network.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
        }
    }
}
