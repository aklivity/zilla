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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmOpenaiResponsesDialectFactorySpi.NAME;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObjectBuilder;

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
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
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

public final class LlmOpenaiResponsesServerFactory implements LlmDialectHandler
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final int QUEUE_ENTRY_OVERHEAD = 7 * Integer.BYTES + 3 * Long.BYTES + Byte.BYTES;
    private static final int EVENT_ATTRIBUTES_CAPACITY = 2048;
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_PATH = ":path";
    private static final String HEADER_STATUS = ":status";
    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_AUTHORIZATION = "authorization";
    private static final String METHOD_POST = "POST";
    private static final String PATH_RESPONSES = "/v1/responses";
    private static final String DIALECT_PREFIX = NAME + ":";
    private static final String STATUS_OK = "200";
    private static final String STATUS_UNAUTHORIZED = "401";
    private static final int NO_MAX_OUTPUT_TOKENS = -1;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_UNSUPPORTED_MEDIA_TYPE = 415;
    private static final int STATUS_INTERNAL_SERVER_ERROR = 500;
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String UNAUTHORIZED_BODY = "{\"error\":{\"message\":\"Incorrect API key provided.\"," +
        "\"type\":\"invalid_request_error\",\"param\":null,\"code\":\"invalid_api_key\"}}";

    private static final String MESSAGE_PARSE_ERROR = "We could not parse the JSON body of your request.";
    private static final String MESSAGE_MODEL_MISSING = "Missing required parameter: 'model'.";
    private static final String MESSAGE_MODEL_NOT_FOUND = "The model `%s` does not exist or you do not have access to it.";
    private static final String CODE_MODEL_NOT_FOUND = "model_not_found";
    private static final String MESSAGE_MODEL_ORDER =
        "The 'model' parameter must precede 'input' in a request that does not fit the decode buffer.";
    private static final String MESSAGE_ROLE_MISSING = "Missing required parameter: 'role'.";
    private static final String MESSAGE_ROLE_ORDER = "The 'role' parameter must precede 'content' in each input item.";
    private static final String MESSAGE_PART_TYPE_ORDER = "The 'type' parameter must be the first member of each content part.";
    private static final String MESSAGE_FORMAT_INVALID =
        "The 'text.format' parameter is invalid, or its 'type' follows members that are too large.";
    private static final String MESSAGE_ITEM_UNSUPPORTED = "Unsupported input item type: '%s'.";
    private static final String MESSAGE_PART_UNSUPPORTED = "Unsupported content part type: '%s'.";
    private static final String MESSAGE_CALL_ID_ORDER =
        "The 'call_id' parameter must precede 'output' in a function call output item.";
    private static final String MESSAGE_ATTRIBUTE_TOO_LARGE = "A tool attribute value is too large.";
    private static final String MESSAGE_TYPE_ORDER =
        "The 'type' parameter must precede 'summary' and 'encrypted_content' in each reasoning item.";
    private static final String MESSAGE_ENCRYPTED_BOUND =
        "The 'encrypted_content' of a reasoning item that precedes its 'summary' is too large.";
    private static final String MESSAGE_ANNOTATIONS_BOUND = "The 'annotations' of an output text part are too large.";
    private static final String MESSAGE_TOOL_TYPE_MISSING = "Missing required parameter: 'type'.";
    private static final int BLOCK_BUFFER_MAX = 4096;
    private static final int OBFUSCATION_MIN = 4;
    private static final int OBFUSCATION_RANGE = 28;
    private static final int ELEMENT_MAX = 4096;
    private static final Map<String, StringEventPositions> PARSER_CONFIG =
        Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED);

    private static final int REPLY_FLAG_END = 0x40;
    private static final int REPLY_FLAG_ERROR = 0x20;
    private static final int RESPONSE_GROWTH = 2;
    private static final int ARGS_HEADROOM = 16;
    private static final int RECORD_REFS = 64;
    private static final int REF_STRIDE = 4;
    private static final int REF_ACCUMULATOR = 0;
    private static final int REF_HOLD = 1;
    private static final int OP_TEXT_DONE = 0;
    private static final int OP_PART_DONE = 1;
    private static final int OP_ITEM_DONE = 2;
    private static final int OP_ITEM_ADDED = 3;
    private static final int OP_PART_ADDED = 4;
    private static final int OP_CREATED = 5;
    private static final int OP_IN_PROGRESS = 6;
    private static final int OP_CALL_ADDED = 7;
    private static final int OP_VALUE_DONE = 8;
    private static final int OP_STATUS = 9;
    private static final int OP_CALL_ITEM_DONE = 10;
    private static final int OP_INIT_STATUS = 11;
    private static final int OP_SUMMARY_PART_ADDED = 12;
    private static final int OP_FINISH_HEAD = 17;
    private static final int OP_ERROR = 20;
    private static final int OP_PROVIDER_HEAD = 25;
    private static final int OP_PROVIDER_TAIL = 26;
    private static final int OP_ANNOTATION_ADDED = 24;
    private static final int OP_REASONING_HEAD = 22;
    private static final int OP_REASONING_TAIL = 23;
    private static final int OP_FAILED = 21;
    private static final int OP_FINISH_ITEM = 18;
    private static final int OP_FINISH_TAIL = 19;
    private static final int OP_SUMMARY_TEXT_DONE = 13;
    private static final int OP_SUMMARY_PART_DONE = 14;
    private static final int OP_REASONING_ADDED = 15;
    private static final DirectBufferEx NO_FRAMES = new UnsafeBufferEx(new byte[0]);
    private static final int STEP_CONTINUE = 0;
    private static final int STEP_WINDOW = 1;
    private static final int STEP_WAIT = 2;
    private static final int REPLY_PADDING_FRACTION = 8;
    private static final int ESCAPE_EXPANSION = 6;
    private static final int MIN_TEXT_INPUT = "\"content\":\"\"".length();
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_REASONING = "reasoning";
    private static final String MEMBER_REFUSAL = "refusal";
    private static final String MEMBER_SUMMARY = "summary";
    private static final String MEMBER_ENCRYPTED = "encrypted_content";
    private static final String TYPE_REFUSAL = "refusal";
    private static final String TYPE_INPUT_IMAGE = "input_image";
    private static final String TYPE_INPUT_FILE = "input_file";
    private static final String TYPE_INPUT_AUDIO = "input_audio";
    private static final byte[] ENCRYPTED_KEY = ("\"" + NAME + ":encrypted_content\":\"").getBytes(UTF_8);
    private static final String ID_MEMBER = DIALECT_PREFIX + "id";
    private static final int EMPTY_ARRAY_LENGTH = 2;
    private static final byte[] TEXT_PREFIX = "{\"text\":\"".getBytes(UTF_8);
    private static final byte[] TEXT_SUFFIX = "\"}".getBytes(UTF_8);

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);
    private static final DirectBufferEx STREAM_OPTIONS_SECTION =
        new UnsafeBufferEx("{\"stream_options\":{\"include_usage\":true}}".getBytes(UTF_8));
    private static final DirectBufferEx CONTROL_CLOSE = new UnsafeBufferEx("}".getBytes(UTF_8));
    private static final DirectBufferEx CONTROL_BEGIN = new UnsafeBufferEx("{".getBytes(UTF_8));
    private static final DirectBufferEx CONTROL_CONTINUE = new UnsafeBufferEx(",".getBytes(UTF_8));
    private static final String SHARED_PREFIX = "x:";
    private static final DirectBufferEx EMPTY_VALUE = new UnsafeBufferEx(new byte[0]);
    private static final DirectBufferEx OBJECT_OPEN = new UnsafeBufferEx("{".getBytes(UTF_8));
    private static final DirectBufferEx STRING_CLOSE = new UnsafeBufferEx("\"".getBytes(UTF_8));

    private static final int MIN_WINDOW = 64;
    private static final int CONTROL_GROWTH = 3;
    private static final int CONTROL_OPEN_CAPACITY = 8;
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;
    private static final int NONE = -1;

    private static final String MEMBER_STREAM = "stream";
    private static final String MEMBER_TOOL_CHOICE = "tool_choice";
    private static final String MEMBER_SERVICE_TIER = "service_tier";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_ROLE = "role";
    private static final String MEMBER_CONTENT = "content";
    private static final String TYPE_MESSAGE = "message";
    private static final String TYPE_FUNCTION = "function";
    private static final String TYPE_INPUT_TEXT = "input_text";
    private static final String TYPE_OUTPUT_TEXT = "output_text";
    private static final String MEMBER_OUTPUT = "output";
    private static final String MEMBER_ID = "id";
    private static final String MEMBER_NAME = "name";
    private static final String MEMBER_CALL_ID = "call_id";
    private static final String MEMBER_OUTPUT_ITEM = "output";
    private static final String MEMBER_OBJECT = "object";
    private static final String MEMBER_MODEL = "model";
    private static final String MEMBER_STATUS = "status";
    private static final String MEMBER_ITEM = "item";
    private static final String MEMBER_ITEM_ID = "item_id";
    private static final String MEMBER_PART = "part";
    private static final String MEMBER_DELTA = "delta";
    private static final String MEMBER_CONTENT_INDEX = "content_index";
    private static final String MEMBER_OUTPUT_INDEX = "output_index";
    private static final String MEMBER_SEQUENCE_NUMBER = "sequence_number";
    private static final String MEMBER_ANNOTATIONS = "annotations";
    private static final String MEMBER_LOGPROBS = "logprobs";
    private static final String MEMBER_INCOMPLETE_DETAILS = "incomplete_details";
    private static final String MEMBER_REASON = "reason";
    private static final String MEMBER_CODE = "code";
    private static final String MEMBER_MESSAGE = "message";
    private static final String MEMBER_PARAM = "param";
    private static final String MEMBER_ERROR = "error";
    private static final String ROLE_ASSISTANT = "assistant";

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
    private final LlmQueueEntryFW queueEntryRO = new LlmQueueEntryFW();
    private final LlmQueueEntryFW.Builder queueEntryRW = new LlmQueueEntryFW.Builder();
    private final LlmReplyFrameFW replyFrameRO = new LlmReplyFrameFW();
    private final LlmReplyFrameFW.Builder replyFrameRW = new LlmReplyFrameFW.Builder();
    private final Array32FW<HttpHeaderFW> eventHeadersRO = new Array32FW<>(new HttpHeaderFW());
    private final OctetsFW eventAttributesRO = new OctetsFW();
    private String beginStatus;
    private String beginContentType;
    private final Consumer<HttpHeaderFW.Builder> statusHeader = h -> h.name(HEADER_STATUS).value(beginStatus);
    private final Consumer<HttpHeaderFW.Builder> contentTypeHeader =
        h -> h.name(HEADER_CONTENT_TYPE).value(beginContentType);
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

    private final EngineContext context;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx copyBuffer;
    private final MutableDirectBufferEx transformBuffer;
    private final MutableDirectBufferEx argsBuffer;
    private final MutableDirectBufferEx memberBuffer;
    private final MutableDirectBufferEx citationBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final BufferPool accumulatePool;
    private final BufferPool holdPool;
    private final int elementMax;
    private final Long2ObjectHashMap<LlmBindingConfig> bindings;
    private final int decodeMax;
    private final int replyPadding;
    private final int encodeMax;
    private final int responseMax;
    private final int llmTypeId;
    private final int httpTypeId;

    private final LlmOpenaiResponsesServerDecoder decodeStart = this::decodeStart;
    private final LlmOpenaiResponsesServerDecoder decodeRootStart = this::decodeRootStart;
    private final LlmOpenaiResponsesServerDecoder decodeLookahead = this::decodeLookahead;
    private final LlmOpenaiResponsesServerDecoder decodeRoot = this::decodeRoot;
    private final LlmOpenaiResponsesServerDecoder decodeRootControl = this::decodeRootControl;
    private final LlmOpenaiResponsesServerDecoder decodeInstructions = this::decodeInstructions;
    private final LlmOpenaiResponsesServerDecoder decodeInput = this::decodeInput;
    private final LlmOpenaiResponsesServerDecoder decodeItems = this::decodeItems;
    private final LlmOpenaiResponsesServerDecoder decodeItem = this::decodeItem;
    private final LlmOpenaiResponsesServerDecoder decodeItemType = this::decodeItemType;
    private final LlmOpenaiResponsesServerDecoder decodeItemRole = this::decodeItemRole;
    private final LlmOpenaiResponsesServerDecoder decodeItemContent = this::decodeItemContent;
    private final LlmOpenaiResponsesServerDecoder decodeParts = this::decodeParts;
    private final LlmOpenaiResponsesServerDecoder decodePart = this::decodePart;
    private final LlmOpenaiResponsesServerDecoder decodeRefusalPart = this::decodeRefusalPart;
    private final LlmOpenaiResponsesServerDecoder decodeAnnotationsStart = this::decodeAnnotationsStart;
    private final LlmOpenaiResponsesServerDecoder decodeReasoning = this::decodeReasoning;
    private final LlmOpenaiResponsesServerDecoder decodeReasoningId = this::decodeReasoningId;
    private final LlmOpenaiResponsesServerDecoder decodeSummaryStart = this::decodeSummaryStart;
    private final LlmOpenaiResponsesServerDecoder decodeSummary = this::decodeSummary;
    private final LlmOpenaiResponsesServerDecoder decodeReasoningContentStart = this::decodeReasoningContentStart;
    private final LlmOpenaiResponsesServerDecoder decodeReasoningContent = this::decodeReasoningContent;
    private final LlmOpenaiResponsesServerDecoder decodeSummaryPart = this::decodeSummaryPart;
    private final LlmOpenaiResponsesServerDecoder decodeEncryptedStream = this::decodeEncryptedStream;
    private final LlmOpenaiResponsesServerDecoder decodeEncryptedHold = this::decodeEncryptedHold;
    private final LlmOpenaiResponsesServerDecoder decodeTail = this::decodeTail;
    private final LlmOpenaiResponsesServerDecoder decodeToolsStart = this::decodeToolsStart;
    private final LlmOpenaiResponsesServerDecoder decodeTools = this::decodeTools;
    private final LlmOpenaiResponsesServerDecoder decodeToolCapture = this::decodeToolCapture;
    private final LlmOpenaiResponsesServerDecoder decodeItemId = this::decodeItemId;
    private final LlmOpenaiResponsesServerDecoder decodeItemStart = this::decodeItemStart;
    private final LlmOpenaiResponsesServerDecoder decodeItemToolStart = this::decodeItemToolStart;
    private final LlmOpenaiResponsesServerDecoder decodePartStart = this::decodePartStart;
    private final LlmOpenaiResponsesServerDecoder decodeToolStart = this::decodeToolStart;
    private final LlmOpenaiResponsesServerDecoder decodeItemCapture = this::decodeItemCapture;
    private final LlmOpenaiResponsesServerDecoder decodeOutputItem = this::decodeOutputItem;
    private final LlmOpenaiResponsesServerDecoder decodeOutputCallId = this::decodeOutputCallId;
    private final LlmOpenaiResponsesServerDecoder decodeOutputValue = this::decodeOutputValue;
    private final LlmOpenaiResponsesServerDecoder decodeText = this::decodeText;
    private final LlmOpenaiResponsesServerDecoder decodeSkip = this::decodeSkip;
    private final LlmOpenaiResponsesServerDecoder decodeControlValue = this::decodeControlValue;
    private final LlmOpenaiResponsesServerDecoder decodeControlSeparator = this::decodeControlSeparator;
    private final LlmOpenaiResponsesServerDecoder decodeMaxOutputTokensValue = this::decodeMaxOutputTokensValue;
    private final LlmOpenaiResponsesServerDecoder decodeEnd = this::decodeEnd;
    private final LlmOpenaiResponsesServerDecoder decodeIgnore = this::decodeIgnore;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyFrame = this::decodeReplyFrame;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyNext = this::decodeReplyNext;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyFinish = this::decodeReplyFinish;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyError = this::decodeReplyError;
    private final LlmOpenaiResponsesReplyDecoder decodeReplySkipped = this::decodeReplySkipped;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyTextRoot = this::decodeReplyTextRoot;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyTextObject = this::decodeReplyTextObject;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyTextKey = this::decodeReplyTextKey;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyTextValue = this::decodeReplyTextValue;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyTextContinue = this::decodeReplyTextContinue;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyTextHeld = this::decodeReplyTextHeld;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyArgs = this::decodeReplyArgs;
    private final LlmOpenaiResponsesReplyDecoder decodeTextBegin = this::decodeTextBegin;
    private final LlmOpenaiResponsesReplyDecoder decodeCallBegin = this::decodeCallBegin;
    private final LlmOpenaiResponsesReplyDecoder decodeCallOpen = this::decodeCallOpen;
    private final LlmOpenaiResponsesReplyDecoder decodeCallAdded = this::decodeCallAdded;
    private final LlmOpenaiResponsesReplyDecoder decodeCallInit = this::decodeCallInit;
    private final LlmOpenaiResponsesReplyDecoder decodeCallEnd = this::decodeCallEnd;
    private final LlmOpenaiResponsesReplyDecoder decodeCallValueDone = this::decodeCallValueDone;
    private final LlmOpenaiResponsesReplyDecoder decodeResultEnd = this::decodeResultEnd;
    private final LlmOpenaiResponsesReplyDecoder decodeProviderPrepare = this::decodeProviderPrepare;
    private final LlmOpenaiResponsesReplyDecoder decodeProviderHead = this::decodeProviderHead;
    private final LlmOpenaiResponsesReplyDecoder decodeProviderEnd = this::decodeProviderEnd;
    private final LlmOpenaiResponsesReplyDecoder decodeProviderTail = this::decodeProviderTail;
    private final LlmOpenaiResponsesReplyDecoder decodeCitationElements = this::decodeCitationElements;
    private final LlmOpenaiResponsesReplyDecoder decodeAnnotationAdded = this::decodeAnnotationAdded;
    private final LlmOpenaiResponsesReplyDecoder decodePartEnd = this::decodePartEnd;
    private final LlmOpenaiResponsesReplyDecoder decodeSectionTail = this::decodeSectionTail;
    private final LlmOpenaiResponsesReplyDecoder decodeReasoningMembers = this::decodeReasoningMembers;
    private final LlmOpenaiResponsesReplyDecoder decodeMembersFlush = this::decodeMembersFlush;
    private final LlmOpenaiResponsesReplyDecoder decodeMembersClose = this::decodeMembersClose;
    private final LlmOpenaiResponsesReplyDecoder decodeReasoningHead = this::decodeReasoningHead;
    private final LlmOpenaiResponsesReplyDecoder decodeErrorEvent = this::decodeErrorEvent;
    private final LlmOpenaiResponsesReplyDecoder decodeFailedEvent = this::decodeFailedEvent;
    private final LlmOpenaiResponsesReplyDecoder decodeFinishClose = this::decodeFinishClose;
    private final LlmOpenaiResponsesReplyDecoder decodeFinishHead = this::decodeFinishHead;
    private final LlmOpenaiResponsesReplyDecoder decodeFinishItems = this::decodeFinishItems;
    private final LlmOpenaiResponsesReplyDecoder decodeFinishTail = this::decodeFinishTail;
    private final LlmOpenaiResponsesReplyDecoder decodeReasoningBegin = this::decodeReasoningBegin;
    private final LlmOpenaiResponsesReplyDecoder decodeReasoningOpen = this::decodeReasoningOpen;
    private final LlmOpenaiResponsesReplyDecoder decodeReasoningAdded = this::decodeReasoningAdded;
    private final LlmOpenaiResponsesReplyDecoder decodeReasoningSection = this::decodeReasoningSection;
    private final LlmOpenaiResponsesReplyDecoder decodeReasoningEnd = this::decodeReasoningEnd;
    private final LlmOpenaiResponsesReplyDecoder decodeSummaryTextDone = this::decodeSummaryTextDone;
    private final LlmOpenaiResponsesReplyDecoder decodeSummaryPartDone = this::decodeSummaryPartDone;
    private final LlmOpenaiResponsesReplyDecoder decodeReasoningTail = this::decodeReasoningTail;
    private final LlmOpenaiResponsesReplyDecoder decodeItemClose = this::decodeItemClose;
    private final LlmOpenaiResponsesReplyDecoder decodeSplitItemClose = this::decodeSplitItemClose;
    private final LlmOpenaiResponsesReplyDecoder decodeReasoningItemClose = this::decodeReasoningItemClose;
    private final LlmOpenaiResponsesReplyDecoder decodeCallStatus = this::decodeCallStatus;
    private final LlmOpenaiResponsesReplyDecoder decodeCallItemDone = this::decodeCallItemDone;
    private final LlmOpenaiResponsesReplyDecoder decodeResponseCreated = this::decodeResponseCreated;
    private final LlmOpenaiResponsesReplyDecoder decodeResponseInProgress = this::decodeResponseInProgress;
    private final LlmOpenaiResponsesReplyDecoder decodeTextPartClose = this::decodeTextPartClose;
    private final LlmOpenaiResponsesReplyDecoder decodeTextDone = this::decodeTextDone;
    private final LlmOpenaiResponsesReplyDecoder decodeTextPartDone = this::decodeTextPartDone;
    private final LlmOpenaiResponsesReplyDecoder decodeTextItemClose = this::decodeTextItemClose;
    private final LlmOpenaiResponsesReplyDecoder decodeTextItemOpen = this::decodeTextItemOpen;
    private final LlmOpenaiResponsesReplyDecoder decodeTextItemAdded = this::decodeTextItemAdded;
    private final LlmOpenaiResponsesReplyDecoder decodeTextPartOpen = this::decodeTextPartOpen;
    private final LlmOpenaiResponsesReplyDecoder decodeTextPartAdded = this::decodeTextPartAdded;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyTextDescend = this::decodeReplyTextDescend;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyArgsContinue = this::decodeReplyArgsContinue;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyTextSkip = this::decodeReplyTextSkip;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyTextDone = this::decodeReplyTextDone;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyValue = this::decodeReplyValue;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyCitations = this::decodeReplyCitations;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyControl = this::decodeReplyControl;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyRest = this::decodeReplyRest;
    private final LlmOpenaiResponsesReplyDecoder decodeReplyDelta = this::decodeReplyDelta;


    private final SegmentTransferTarget pieceTarget = new SegmentTransferTarget();
    private final JsonStringBody body = new JsonStringBody();

    private byte[] pieceBytes;
    private UnsafeBufferEx pieceBuffer;
    private final byte[] obfuscationBytes;
    private final UnsafeBufferEx obfuscationBuffer;

    public LlmOpenaiResponsesServerFactory(
        EngineContext context)
    {
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.accumulatePool = context.bufferPool().duplicate();
        this.holdPool = context.bufferPool().duplicate();
        this.elementMax = Math.min(ELEMENT_MAX, holdPool.slotCapacity());
        this.decodeMax = decodePool.slotCapacity();
        this.replyPadding = decodePool.slotCapacity() / REPLY_PADDING_FRACTION;
        this.responseMax = (decodeMax - replyPadding) / RESPONSE_GROWTH;
        this.argsBuffer = new UnsafeBufferEx(new byte[responseMax]);
        this.memberBuffer = new UnsafeBufferEx(new byte[responseMax]);
        this.citationBuffer = new UnsafeBufferEx(new byte[elementMax + responseMax]);
        this.encodeMax = encodePool.slotCapacity();
        this.copyBuffer = new UnsafeBufferEx(new byte[encodeMax]);
        this.transformBuffer = new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]);
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.bindings = new Long2ObjectHashMap<>();
        this.pieceBytes = new byte[1024];
        this.pieceBuffer = new UnsafeBufferEx(pieceBytes);
        this.obfuscationBytes = new byte[OBFUSCATION_MIN + OBFUSCATION_RANGE];
        this.obfuscationBuffer = new UnsafeBufferEx(obfuscationBytes);
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
            LlmHeaderBytes.equalTo(headers.get(HEADER_PATH, 0), PATH_RESPONSES) &&
            LlmHeaderBytes.isMediaType(headers.get(HEADER_CONTENT_TYPE, 0), CONTENT_TYPE_JSON);
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

            if (!LlmHeaderBytes.isMediaType(headers.get(HEADER_CONTENT_TYPE, 0), CONTENT_TYPE_JSON))
            {
                newStream = new LlmOpenaiRejectHandler(network, originId, routedId, initialId,
                    Integer.toString(STATUS_UNSUPPORTED_MEDIA_TYPE),
                    errorBody(STATUS_UNSUPPORTED_MEDIA_TYPE, null, null, null, null))::onNetMessage;
            }
            else
            {
                final LlmAuthorizationResult authResult = binding.authorize(
                    begin.traceId(), routedId, initialId, authorization, headers, HEADER_AUTHORIZATION);

                if (!authResult.authorized())
                {
                    newStream = new LlmOpenaiRejectHandler(network, originId, routedId, initialId,
                        STATUS_UNAUTHORIZED, UNAUTHORIZED_BODY)::onNetMessage;
                }
                else
                {
                    final LlmRouteConfig route = binding.resolve(authResult.authorization());

                    if (route != null)
                    {
                        newStream = new LlmOpenaiResponsesServer(
                                network,
                                originId,
                                routedId,
                                initialId,
                                binding,
                                authResult.authorization(),
                                contentType,
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
        String param,
        String code,
        String message)
    {
        final JsonObjectBuilder error = Json.createObjectBuilder()
            .add("message", message != null ? message : reason(status))
            .add("type", type != null ? type : errorType(status));

        if (param != null)
        {
            error.add("param", param);
        }
        else
        {
            error.addNull("param");
        }

        if (code != null)
        {
            error.add("code", code);
        }
        else if (status == 429)
        {
            error.add("code", "rate_limit_exceeded");
        }
        else
        {
            error.addNull("code");
        }

        return Json.createObjectBuilder()
            .add("error", error)
            .build()
            .toString();
    }

    private static String errorType(
        int status)
    {
        return status == 429 ? "requests" : status >= 500 ? "server_error" : "invalid_request_error";
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

    @FunctionalInterface
    private interface LlmOpenaiResponsesServerDecoder
    {
        int decode(
            LlmOpenaiResponsesServer server,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int progress,
            int limit);
    }

    @FunctionalInterface
    private interface LlmOpenaiResponsesReplyDecoder
    {
        int decode(
            LlmOpenaiResponsesStream stream,
            DirectBufferEx buffer,
            int offset,
            int limit);
    }

    private int decodeReplyFrame(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final LlmReplyFrameFW frame = offset < limit ? replyFrameRO.tryWrap(buffer, offset, limit) : null;

        if (frame != null && stream.server.admits(stream.worstCase(frame)))
        {
            if ((frame.flags() & REPLY_FLAG_END) != 0)
            {
                stream.decoder = decodeReplyFinish;
            }
            else if ((frame.flags() & REPLY_FLAG_ERROR) != 0)
            {
                stream.decoder = decodeReplyError;
            }
            else
            {
                stream.beginReplyFrame(buffer, frame);
                stream.decoder = stream.dataDecoder;
            }
        }

        return offset;
    }

    private int decodeReplyFinish(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final LlmReplyFrameFW frame = replyFrameRO.tryWrap(buffer, offset, limit);
        int progress = offset;

        if (stream.server.streaming)
        {
            stream.beginFinish(frame);
            stream.decoder = decodeFinishClose;
        }
        else
        {
            stream.decoder = decodeReplyFrame;
            stream.finishReply(frame);
            progress = frame.limit();
        }

        return progress;
    }

    private int decodeReplyError(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.beginFail(replyFrameRO.tryWrap(buffer, offset, limit));
        stream.decoder = decodeErrorEvent;

        return offset;
    }

    private int decodeReplyNext(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        if (Flags.hasFin(stream.replyFlags) && stream.valued())
        {
            stream.decoder = decodeCallEnd;
        }
        else if (Flags.hasFin(stream.replyFlags) && stream.members())
        {
            stream.decoder = decodeProviderEnd;
        }
        else if (Flags.hasFin(stream.replyFlags) && stream.resulting())
        {
            stream.decoder = decodeResultEnd;
        }
        else if (Flags.hasFin(stream.replyFlags) && stream.server.streaming &&
            (stream.kind == LlmOpenaiResponsesStream.KIND_CITATIONS ||
                stream.kind == LlmOpenaiResponsesStream.KIND_REFUSAL))
        {
            stream.decoder = decodePartEnd;
        }
        else if (Flags.hasFin(stream.replyFlags) && stream.kind == LlmOpenaiResponsesStream.KIND_REASONING &&
            stream.server.streaming)
        {
            stream.decoder = decodeReasoningEnd;
        }
        else
        {
            stream.endReplyFrame();
            stream.decoder = decodeReplyFrame;
            progress = offset + stream.replyFrameLength;
        }

        return progress;
    }

    private int decodeReplySkipped(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.decoder = decodeReplyNext;

        return offset;
    }

    private int decodeReplyValue(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.decoder = decodeReplyRest;

        if (stream.valuePipeline != null &&
            stream.advanceFanOut(buffer, offset, stream.valuePipeline, stream.valueOutput, stream.valueTail,
                stream.valueLimit()))
        {
            stream.flushValue();
            stream.decoder = decodeReplyValue;
        }

        return offset;
    }

    private int decodeReplyCitations(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.server.streaming)
        {
            final boolean suspended = stream.advanceFanOut(buffer, offset, stream.citationsPipeline,
                stream.citationsOutput, stream.valueTail, citationBuffer.capacity() - stream.citationLength);

            stream.advanceFollow = suspended ? decodeReplyCitations : decodeReplyNext;
            stream.decoder = decodeCitationElements;
        }
        else
        {
            stream.decoder = decodeReplyNext;
            stream.advanceFanOut(buffer, offset, stream.citationsPipeline, stream.citationsOutput, stream.valueTail,
                Integer.MAX_VALUE);
        }

        return offset;
    }

    private int decodeReplyRest(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.server.streaming &&
            (stream.kind == LlmOpenaiResponsesStream.KIND_REASONING || stream.members()))
        {
            stream.decoder = decodeReasoningMembers;
        }
        else
        {
            stream.decoder = decodeReplyDelta;
            stream.advanceFanOut(buffer, offset, stream.restPipeline, stream.restEdges, stream.restTail, Integer.MAX_VALUE);
        }

        return offset;
    }

    private int decodeReplyDelta(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.decoder = decodeReplyNext;

        return offset;
    }

    private int decodeReplyControl(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.decoder = decodeReplyNext;
        stream.putControl(buffer, offset);

        return offset;
    }

    private int decodeReplyTextRoot(
        LlmOpenaiResponsesStream stream,
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
        LlmOpenaiResponsesStream stream,
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
        LlmOpenaiResponsesStream stream,
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
                    stream.decoder = stream.replyMember.contentEquals(parser.getStringView())
                        ? stream.replyMemberState
                        : decodeReplyTextSkip;
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
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyTextPiece(stream, buffer, offset, decodeReplyTextValue, true, false);
    }

    private int decodeReplyTextHeld(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyTextPiece(stream, buffer, offset, decodeReplyTextHeld, false, true);
    }

    private int decodeReplyTextContinue(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyTextPiece(stream, buffer, offset, decodeReplyTextContinue, false, false);
    }

    private int decodeReplyTextPiece(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        LlmOpenaiResponsesReplyDecoder state,
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
                        final int taken = stream.onReplyText(chars, length);

                        if (taken < length)
                        {
                            if (first)
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
                            stream.decoder = decodeReplyTextDone;
                        }
                    }
                    else
                    {
                        stream.decoder = event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY
                            ? decodeReplyTextSkip
                            : decodeReplyTextKey;
                        stream.replySkipDepth = stream.decoder == decodeReplyTextSkip ? 1 : 0;
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

    private int decodeResponseCreated(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_CREATED))
        {
            stream.decoder = decodeResponseInProgress;
        }

        return offset;
    }

    private int decodeResponseInProgress(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_IN_PROGRESS))
        {
            stream.decoder = decodeReplyFrame;
        }

        return offset;
    }

    private int decodeCallBegin(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.openItem == LlmOpenaiResponsesStream.ITEM_NONE)
        {
            stream.decoder = decodeCallOpen;
        }
        else if (stream.openItem == LlmOpenaiResponsesStream.ITEM_MESSAGE)
        {
            stream.textFollow = decodeTextItemClose;
            stream.itemFollow = decodeCallOpen;
            stream.decoder = decodeTextPartClose;
        }
        else
        {
            stream.itemFollow = decodeCallOpen;
            stream.decoder = decodeItemClose;
        }

        return offset;
    }

    private int decodeReasoningBegin(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.openItem == LlmOpenaiResponsesStream.ITEM_REASONING)
        {
            stream.decoder = decodeReasoningSection;
        }
        else if (stream.openItem == LlmOpenaiResponsesStream.ITEM_NONE)
        {
            stream.decoder = decodeReasoningOpen;
        }
        else if (stream.openItem == LlmOpenaiResponsesStream.ITEM_MESSAGE)
        {
            stream.textFollow = decodeTextItemClose;
            stream.itemFollow = decodeReasoningOpen;
            stream.decoder = decodeTextPartClose;
        }
        else
        {
            stream.itemFollow = decodeReasoningOpen;
            stream.decoder = decodeItemClose;
        }

        return offset;
    }

    private int decodeReasoningOpen(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.openReasoningRecord();
        stream.decoder = decodeReasoningAdded;

        return offset;
    }

    private int decodeReasoningAdded(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_REASONING_ADDED))
        {
            stream.decoder = decodeReasoningSection;
        }

        return offset;
    }

    private int decodeReasoningSection(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.prepareReasoningSection();
        stream.decoder = decodeReplyTextRoot;

        return offset;
    }

    private int decodeReasoningEnd(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.prepareReasoningEnd();
        stream.summaryFollow = decodeReasoningTail;
        stream.decoder = decodeSummaryTextDone;

        return offset;
    }

    private int decodeSummaryTextDone(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.partMode != LlmOpenaiResponsesStream.PART_CLOSING)
        {
            stream.decoder = stream.summaryFollow;
        }
        else if (stream.emitStep(OP_SUMMARY_TEXT_DONE))
        {
            stream.decoder = decodeSummaryPartDone;
        }

        return offset;
    }

    private int decodeSummaryPartDone(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_SUMMARY_PART_DONE))
        {
            stream.partMode = LlmOpenaiResponsesStream.PART_NONE;
            stream.decoder = stream.summaryFollow;
        }

        return offset;
    }

    private int decodeReasoningTail(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.eventSent == 0)
        {
            stream.kind = LlmOpenaiResponsesStream.KIND_NONE;
            stream.decoder = decodeReplyNext;
        }
        else if (stream.emitStep(OP_REASONING_TAIL))
        {
            stream.closeItemRecord();
            stream.membersStarted = false;
            stream.openItem = LlmOpenaiResponsesStream.ITEM_NONE;
            stream.kind = LlmOpenaiResponsesStream.KIND_NONE;
            stream.decoder = decodeReplyNext;
        }

        return offset;
    }

    private int decodeReasoningMembers(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final boolean suspended = stream.advanceFanOut(buffer, offset, stream.restPipeline, stream.restEdges,
            stream.restTail, memberBuffer.capacity() - 1);

        stream.advanceFollow = suspended ? decodeReasoningMembers : decodeReplyDelta;
        stream.decoder = decodeMembersFlush;

        return offset;
    }

    private int decodeMembersFlush(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.memberLength == 0)
        {
            stream.decoder = stream.advanceFollow;
        }
        else if (stream.eventSent == 0)
        {
            stream.summaryFollow = decodeReasoningHead;
            stream.decoder = decodeMembersClose;
        }
        else if (stream.flushMembers())
        {
            stream.decoder = stream.advanceFollow;
        }

        return offset;
    }

    private int decodeMembersClose(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.prepareReasoningEnd();
        stream.sealItem();
        stream.decoder = decodeSummaryTextDone;

        return offset;
    }

    private int decodeReasoningHead(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_REASONING_HEAD))
        {
            stream.decoder = decodeMembersFlush;
        }

        return offset;
    }

    private int decodeItemClose(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.openItem == LlmOpenaiResponsesStream.ITEM_REASONING)
        {
            stream.decoder = decodeReasoningItemClose;
        }
        else if (stream.splitPending())
        {
            stream.decoder = decodeSplitItemClose;
        }
        else
        {
            stream.closeOpenItem(stream.closeStatus);
            stream.decoder = stream.itemFollow;
        }

        return offset;
    }

    private int decodeSplitItemClose(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.prepareResultEnd();

        if (stream.emitStep(OP_CALL_ITEM_DONE))
        {
            stream.closeItemRecord();
            stream.openItem = LlmOpenaiResponsesStream.ITEM_NONE;
            stream.decoder = stream.itemFollow;
        }

        return offset;
    }

    private int decodeReasoningItemClose(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.sealItem();

        if (stream.emitStep(OP_ITEM_DONE))
        {
            stream.closeItemRecord();
            stream.openItem = LlmOpenaiResponsesStream.ITEM_NONE;
            stream.decoder = stream.itemFollow;
        }

        return offset;
    }

    private int decodeFinishClose(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.openItem == LlmOpenaiResponsesStream.ITEM_NONE)
        {
            stream.decoder = decodeFinishHead;
        }
        else if (stream.openItem == LlmOpenaiResponsesStream.ITEM_MESSAGE)
        {
            stream.textFollow = decodeTextItemClose;
            stream.itemFollow = decodeFinishHead;
            stream.decoder = decodeTextPartClose;
        }
        else
        {
            stream.itemFollow = decodeFinishHead;
            stream.decoder = decodeItemClose;
        }

        return offset;
    }

    private int decodeFinishHead(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_FINISH_HEAD))
        {
            stream.finishIndex = 0;
            stream.decoder = decodeFinishItems;
        }

        return offset;
    }

    private int decodeFinishItems(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.finishIndex == stream.itemCount)
        {
            stream.decoder = decodeFinishTail;
        }
        else if (stream.emitStep(OP_FINISH_ITEM))
        {
            stream.finishIndex++;
        }

        return offset;
    }

    private int decodeFinishTail(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final LlmReplyFrameFW frame = replyFrameRO.tryWrap(buffer, offset, limit);

        stream.finishEnd = frame.extension().get(llmEndExRO::tryWrap);

        final boolean complete = stream.emitStep(OP_FINISH_TAIL);

        stream.finishEnd = null;

        if (complete)
        {
            stream.completeFinish(frame);
            stream.decoder = decodeReplyFrame;
        }

        return complete ? frame.limit() : offset;
    }

    private int decodeErrorEvent(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_ERROR))
        {
            stream.decoder = decodeFailedEvent;
        }

        return offset;
    }

    private int decodeFailedEvent(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final LlmReplyFrameFW frame = replyFrameRO.tryWrap(buffer, offset, limit);
        final boolean complete = stream.emitStep(OP_FAILED);

        if (complete)
        {
            stream.completeFail(frame);
            stream.decoder = decodeReplyFrame;
        }

        return complete ? frame.limit() : offset;
    }

    private int decodeCitationElements(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.scanCitations())
        {
            stream.prepareAnnotation();
            stream.decoder = decodeAnnotationAdded;
        }
        else
        {
            stream.compactCitations();
            stream.decoder = stream.advanceFollow;
        }

        return offset;
    }

    private int decodeAnnotationAdded(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_ANNOTATION_ADDED))
        {
            stream.completeAnnotation();
            stream.decoder = decodeCitationElements;
        }

        return offset;
    }

    private int decodePartEnd(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.textFollow = decodeSectionTail;
        stream.decoder = decodeTextPartClose;

        return offset;
    }

    private int decodeSectionTail(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.kind = LlmOpenaiResponsesStream.KIND_NONE;
        stream.decoder = decodeReplyNext;

        return offset;
    }

    private int decodeProviderPrepare(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.prepareProviderMembers();
        stream.decoder = decodeProviderHead;

        return offset;
    }

    private int decodeProviderHead(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_PROVIDER_HEAD))
        {
            stream.dataDecoder = decodeReplyRest;
            stream.decoder = decodeReplyRest;
        }

        return offset;
    }

    private int decodeProviderEnd(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.prepareProviderEnd();
        stream.decoder = decodeProviderTail;

        return offset;
    }

    private int decodeProviderTail(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_PROVIDER_TAIL))
        {
            stream.closeItemRecord();
            stream.membersStarted = false;
            stream.openItem = LlmOpenaiResponsesStream.ITEM_NONE;
            stream.kind = LlmOpenaiResponsesStream.KIND_NONE;
            stream.decoder = decodeReplyNext;
        }

        return offset;
    }

    private int decodeCallOpen(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.openCallRecord();
        stream.decoder = decodeCallAdded;

        return offset;
    }

    private int decodeCallAdded(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_CALL_ADDED))
        {
            stream.statusIndex = 0;
            stream.decoder = decodeCallInit;
        }

        return offset;
    }

    private int decodeCallInit(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final String[] names = LlmOpenaiResponsesItems.initEvents(stream.itemType);

        if (stream.statusIndex == names.length)
        {
            stream.decoder = stream.members() ? decodeProviderPrepare : decodeReplyTextRoot;
        }
        else if (stream.emitStep(OP_INIT_STATUS))
        {
            stream.statusIndex++;
        }

        return offset;
    }

    private int decodeCallEnd(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.prepareCallEnd();
        stream.decoder = decodeCallValueDone;

        return offset;
    }

    private int decodeResultEnd(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.prepareResultEnd();
        stream.decoder = decodeCallItemDone;

        return offset;
    }

    private int decodeCallValueDone(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_VALUE_DONE))
        {
            stream.statusIndex = 0;
            stream.decoder = decodeCallStatus;
        }

        return offset;
    }

    private int decodeCallStatus(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final String[] names = LlmOpenaiResponsesItems.doneEvents(stream.itemType);

        if (stream.statusIndex == names.length && stream.awaitsResult())
        {
            stream.kind = LlmOpenaiResponsesStream.KIND_NONE;
            stream.decoder = decodeReplyNext;
        }
        else if (stream.statusIndex == names.length)
        {
            stream.decoder = decodeCallItemDone;
        }
        else if (stream.emitStep(OP_STATUS))
        {
            stream.statusIndex++;
        }

        return offset;
    }

    private int decodeCallItemDone(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_CALL_ITEM_DONE))
        {
            stream.finishCallRecord();
            stream.decoder = decodeReplyNext;
        }

        return offset;
    }

    private int decodeTextBegin(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final boolean continuing = stream.openItem == LlmOpenaiResponsesStream.ITEM_MESSAGE &&
            stream.itemMessage == stream.sectionMessage;

        if (stream.openItem == LlmOpenaiResponsesStream.ITEM_NONE)
        {
            stream.decoder = decodeTextItemOpen;
        }
        else if (continuing)
        {
            stream.textFollow = decodeTextPartOpen;
            stream.decoder = decodeTextPartClose;
        }
        else if (stream.openItem == LlmOpenaiResponsesStream.ITEM_MESSAGE)
        {
            stream.textFollow = decodeTextItemClose;
            stream.itemFollow = decodeTextItemOpen;
            stream.decoder = decodeTextPartClose;
        }
        else
        {
            stream.itemFollow = decodeTextItemOpen;
            stream.decoder = decodeItemClose;
        }

        return offset;
    }

    private int decodeTextPartClose(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.partMode == LlmOpenaiResponsesStream.PART_NONE)
        {
            stream.decoder = stream.textFollow;
        }
        else
        {
            stream.preparePartClose();
            stream.decoder = decodeTextDone;
        }

        return offset;
    }

    private int decodeTextDone(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_TEXT_DONE))
        {
            stream.decoder = decodeTextPartDone;
        }

        return offset;
    }

    private int decodeTextPartDone(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_PART_DONE))
        {
            stream.partMode = LlmOpenaiResponsesStream.PART_NONE;
            stream.decoder = stream.textFollow;
        }

        return offset;
    }

    private int decodeTextItemClose(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.sealItem();

        if (stream.emitStep(OP_ITEM_DONE))
        {
            stream.itemMessage = NONE;
            stream.openItem = LlmOpenaiResponsesStream.ITEM_NONE;
            stream.decoder = stream.itemFollow;
        }

        return offset;
    }

    private int decodeTextItemOpen(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.openMessageRecord();
        stream.decoder = decodeTextItemAdded;

        return offset;
    }

    private int decodeTextItemAdded(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_ITEM_ADDED))
        {
            stream.decoder = decodeTextPartOpen;
        }

        return offset;
    }

    private int decodeTextPartOpen(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.openPartRecord();
        stream.decoder = decodeTextPartAdded;

        return offset;
    }

    private int decodeTextPartAdded(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (stream.emitStep(OP_PART_ADDED))
        {
            stream.decoder = decodeReplyTextRoot;
        }

        return offset;
    }

    private int decodeReplyTextDescend(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final JsonParserEx parser = stream.wrapReply(buffer, offset);

        try
        {
            while (stream.decoder == decodeReplyTextDescend && parser.hasNextEvent())
            {
                if (parser.nextEvent() == JsonEvent.START_OBJECT)
                {
                    stream.replyMember = stream.valueMember;
                    stream.replyMemberState = decodeReplyTextValue;
                    stream.decoder = decodeReplyTextKey;
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

        return stream.positionReply(buffer, offset, decodeReplyTextDescend);
    }

    private int decodeReplyArgs(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyArgsStep(stream, buffer, offset, decodeReplyArgs, true);
    }

    private int decodeReplyArgsContinue(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return decodeReplyArgsStep(stream, buffer, offset, decodeReplyArgsContinue, false);
    }

    private int decodeReplyArgsStep(
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        LlmOpenaiResponsesReplyDecoder state,
        boolean boundary)
    {
        final JsonParserEx parser = stream.wrapReply(buffer, offset);

        int outcome = STEP_CONTINUE;

        try
        {
            while (stream.decoder == state && outcome == STEP_CONTINUE)
            {
                outcome = stream.stepArgs(parser, boundary);
            }
        }
        catch (JsonException ex)
        {
            stream.rejectReply();
        }

        return outcome == STEP_WAIT ? stream.pauseReply(buffer, offset) : stream.positionReply(buffer, offset, state);
    }

    private int decodeReplyTextSkip(
        LlmOpenaiResponsesStream stream,
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
                final boolean deferred = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

                switch (event)
                {
                case START_OBJECT, START_ARRAY:
                    stream.replySkipDepth++;
                    break;
                case END_OBJECT, END_ARRAY:
                    stream.replySkipDepth--;
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

                if (stream.replySkipDepth == 0 && !deferred)
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
        LlmOpenaiResponsesStream stream,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        stream.replyTailLength = 0;
        stream.dataDecoder = decodeReplyTextDone;
        stream.decoder = stream.textThen;

        return offset;
    }

    private int decodeLookahead(
        LlmOpenaiResponsesServer server,
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
        LlmOpenaiResponsesServer server,
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
        LlmOpenaiResponsesServer server,
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
        LlmOpenaiResponsesServer server,
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
        LlmOpenaiResponsesServer server,
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

    private boolean isRootControl(
        CharSequence key)
    {
        return !matches(key, "instructions") &&
            !matches(key, "input") &&
            !matches(key, "tools") &&
            !matches(key, "max_output_tokens") &&
            !matches(key, "model") &&
            !matches(key, MEMBER_STREAM) &&
            !matches(key, MEMBER_STREAM_OPTIONS);
    }

    private int decodeInstructions(
        LlmOpenaiResponsesServer server,
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
                server.beginInstructions();
                server.text(LlmSectionType.SYSTEM_INSTRUCTION, decodeRoot);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
                break decode;
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

    private int decodeInput(
        LlmOpenaiResponsesServer server,
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
                server.beginMessage(null);
                server.text(LlmSectionType.USER_TEXT, decodeRoot);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
                break decode;
            case START_ARRAY:
                server.decoder = decodeItems;
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

    private int decodeItems(
        LlmOpenaiResponsesServer server,
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
                server.itemType = null;
                server.itemId = null;
                lookaheadItem(server, traceId, offset, limit);
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

    private void lookaheadItem(
        LlmOpenaiResponsesServer server,
        long traceId,
        int offset,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        LlmSectionType section = null;
        String type = null;
        boolean ordinary = false;
        boolean typeKey = false;
        int depth = 0;

        while (section == null && !ordinary && parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                ordinary = depth < 0;
                break;
            case KEY_NAME:
                typeKey = false;
                if (depth == 0)
                {
                    final CharSequence key = parser.getStringView();

                    typeKey = matches(key, MEMBER_TYPE);
                    ordinary = matches(key, MEMBER_ROLE) ||
                        matches(key, MEMBER_CONTENT) ||
                        matches(key, MEMBER_SUMMARY) ||
                        matches(key, MEMBER_ENCRYPTED);
                }
                break;
            case VALUE_STRING:
                if (parser.deferredBytes())
                {
                    parser.consumed(parser.getStringView().length());
                }
                else if (depth == 0 && typeKey)
                {
                    type = parser.getString();
                    section = itemSection(LlmOpenaiResponsesItems.kind(type));
                    ordinary = section == null;
                }
                break;
            default:
                break;
            }
        }

        parser.rewind();

        if (ordinary)
        {
            server.decoder = decodeItemStart;
        }
        else if (section != null)
        {
            server.itemType = type;
            server.probe.begin(section);
            server.probe.configure(type, null, null);

            if (server.probed(traceId, limit))
            {
                server.decoder = decodeItemToolStart;
            }
        }
        else if (limit - offset >= decodeMax)
        {
            server.onDecodeParseError(traceId);
        }
    }

    private int decodeItemStart(
        LlmOpenaiResponsesServer server,
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

            server.decoder = decodeItem;
        }

        return server.position();
    }

    private int decodeItemToolStart(
        LlmOpenaiResponsesServer server,
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

            final LlmSectionType section = server.probe.type();

            server.beginMessage(section == LlmSectionType.TOOL_CALL || section == LlmSectionType.SERVER_TOOL_CALL);
            server.capture(section, server.itemMessage, decodeItems);
            server.probing = true;
            server.decoder = decodeItemCapture;
        }

        return server.position();
    }

    private int decodeItem(
        LlmOpenaiResponsesServer server,
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
                if (matches(key, MEMBER_TYPE))
                {
                    server.decoder = decodeItemType;
                }
                else if (matches(key, MEMBER_ROLE))
                {
                    server.decoder = decodeItemRole;
                }
                else if (matches(key, MEMBER_ID))
                {
                    server.decoder = decodeItemId;
                }
                else if (matches(key, MEMBER_CONTENT))
                {
                    if (server.role == null)
                    {
                        server.onDecodeInvalidRequest(traceId, MESSAGE_ROLE_ORDER);
                        break decode;
                    }
                    server.decoder = decodeItemContent;
                }
                else if ((matches(key, MEMBER_SUMMARY) || matches(key, MEMBER_ENCRYPTED)) &&
                    server.itemType == null && server.role == null)
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_TYPE_ORDER);
                    break decode;
                }
                else
                {
                    server.skip(decodeItem);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                if (server.role == null)
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_ROLE_MISSING);
                    break decode;
                }
                server.decoder = decodeItems;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeItemType(
        LlmOpenaiResponsesServer server,
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
                final String type = parser.getString();
                final int kind = LlmOpenaiResponsesItems.kind(type);

                server.itemType = type;

                if (kind == LlmOpenaiResponsesItems.KIND_MESSAGE)
                {
                    server.decoder = decodeItem;
                }
                else if (kind == LlmOpenaiResponsesItems.KIND_REASONING || kind == LlmOpenaiResponsesItems.KIND_FUNCTION_OUTPUT)
                {
                    server.onDecodeItemType(type, kind);
                }
                else
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_ITEM_UNSUPPORTED.formatted(type));
                }
            }
        }

        return server.position();
    }

    private int decodeItemRole(
        LlmOpenaiResponsesServer server,
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
                final LlmSectionType roleType = roleSectionType(role);

                if (roleType != null)
                {
                    server.role = role;
                    server.roleType = roleType;
                    server.beginMessage(roleType);
                    server.decoder = decodeItem;
                }
                else
                {
                    server.skipDepth = 1;
                    server.skipThen = decodeItems;
                    server.decoder = decodeSkip;
                }
            }
        }

        return server.position();
    }

    private int decodeItemContent(
        LlmOpenaiResponsesServer server,
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
                server.text(server.roleType, decodeItem);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
                break decode;
            case START_ARRAY:
                server.partsThen = decodeItem;
                server.decoder = decodeParts;
                break;
            case VALUE_NULL:
                server.decoder = decodeItem;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeParts(
        LlmOpenaiResponsesServer server,
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
        LlmOpenaiResponsesServer server,
        long traceId,
        int offset,
        int limit)
    {
        final JsonParserEx parser = server.parser;

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

            if (!matches(parser.getStringView(), MEMBER_TYPE))
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
            final LlmSectionType mediaType = mediaSectionType(type);

            if (TYPE_INPUT_TEXT.equals(type) || TYPE_OUTPUT_TEXT.equals(type))
            {
                server.decoder = decodePart;
            }
            else if (TYPE_REFUSAL.equals(type) && server.roleType == LlmSectionType.ASSISTANT_TEXT)
            {
                server.decoder = decodeRefusalPart;
            }
            else if (mediaType != null && server.roleType == LlmSectionType.USER_TEXT)
            {
                server.probe.begin(mediaType);

                if (server.probed(traceId, limit))
                {
                    server.decoder = decodePartStart;
                }
            }
            else
            {
                server.onDecodeInvalidRequest(traceId, MESSAGE_PART_UNSUPPORTED.formatted(type));
            }
        }

        if (server.decoder == decodeParts || server.decoder == decodePartStart)
        {
            parser.rewind();

            if (server.decoder == decodeParts && limit - offset >= decodeMax)
            {
                server.onDecodeInvalidRequest(traceId, MESSAGE_PART_TYPE_ORDER);
            }
        }
    }

    private int decodePartStart(
        LlmOpenaiResponsesServer server,
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

            server.capture(server.probe.type(), server.itemMessage, decodeParts);
            server.probing = true;
            server.decoder = decodeToolCapture;
        }

        return server.position();
    }

    private int decodePart(
        LlmOpenaiResponsesServer server,
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
                if (matches(key, MEMBER_TEXT))
                {
                    server.text(server.roleType, decodePart);
                    server.decoder = decodeText;
                }
                else if (matches(key, MEMBER_ANNOTATIONS) && server.roleType == LlmSectionType.ASSISTANT_TEXT)
                {
                    server.decoder = decodeAnnotationsStart;
                }
                else
                {
                    server.skip(decodePart);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.onDecodePartEnd();
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeRefusalPart(
        LlmOpenaiResponsesServer server,
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
                if (matches(parser.getStringView(), MEMBER_REFUSAL))
                {
                    server.text(LlmSectionType.ASSISTANT_REFUSAL, decodeRefusalPart);
                    server.decoder = decodeText;
                }
                else
                {
                    server.skip(decodeRefusalPart);
                    server.decoder = decodeSkip;
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

    private int decodeAnnotationsStart(
        LlmOpenaiResponsesServer server,
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
                server.onDecodeAnnotations(decodePart);
                server.decoder = decodeControlValue;
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

    private int decodeReasoning(
        LlmOpenaiResponsesServer server,
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
                if (matches(key, MEMBER_ID))
                {
                    server.decoder = decodeReasoningId;
                }
                else if (matches(key, MEMBER_SUMMARY))
                {
                    server.decoder = decodeSummaryStart;
                }
                else if (matches(key, MEMBER_CONTENT))
                {
                    server.decoder = decodeReasoningContentStart;
                }
                else if (matches(key, MEMBER_ENCRYPTED))
                {
                    server.onDecodeEncrypted(traceId);
                    server.decoder = server.reasoningSummaries > 0 ? decodeEncryptedStream : decodeEncryptedHold;
                }
                else
                {
                    server.skip(decodeReasoning);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.onDecodeReasoningEnd(traceId);
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeReasoningId(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final String value = server.decodeString(traceId, decodeReasoning);

        if (value != null)
        {
            server.reasoningId = value;
        }

        return server.position();
    }

    private int decodeSummaryStart(
        LlmOpenaiResponsesServer server,
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

            if (event == JsonEvent.START_ARRAY)
            {
                server.partsThen = decodeSummary;
                server.decoder = decodeSummary;
            }
            else
            {
                server.onDecodeNonArray(event, decodeReasoning);
                server.reasoningSummaries++;
            }
        }

        return server.position();
    }

    private int decodeSummary(
        LlmOpenaiResponsesServer server,
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
                server.decoder = decodeSummaryPart;
                break;
            case END_ARRAY:
                server.reasoningSummaries++;
                server.decoder = decodeReasoning;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeReasoningContentStart(
        LlmOpenaiResponsesServer server,
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

            if (event == JsonEvent.START_ARRAY)
            {
                server.partsThen = decodeReasoningContent;
                server.decoder = decodeReasoningContent;
            }
            else
            {
                server.onDecodeNonArray(event, decodeReasoning);
            }
        }

        return server.position();
    }

    private int decodeReasoningContent(
        LlmOpenaiResponsesServer server,
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
                server.decoder = decodeSummaryPart;
                break;
            case END_ARRAY:
                server.decoder = decodeReasoning;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeSummaryPart(
        LlmOpenaiResponsesServer server,
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
                if (matches(parser.getStringView(), MEMBER_TEXT))
                {
                    server.onDecodeReasoningPart(traceId, authorization);
                    server.decoder = decodeText;
                }
                else
                {
                    server.skip(decodeSummaryPart);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.decoder = server.partsThen;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeEncryptedStream(
        LlmOpenaiResponsesServer server,
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

        while (server.decoder == decodeEncryptedStream && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (event == JsonEvent.VALUE_STRING)
            {
                final boolean deferred = parser.deferredBytes();
                final JsonStringView chars = parser.getStringView();
                final int length = chars.length();
                final int take = server.onDecodeEncryptedStream(traceId, authorization, chars, !deferred);

                if (take < length)
                {
                    server.onDecodeParseError(traceId);
                }
                else if (deferred)
                {
                    parser.consumed(take);
                }
                else
                {
                    server.onDecodeEncryptedEnd(traceId, authorization);
                    server.decoder = decodeReasoning;
                }
            }
            else if (event == JsonEvent.VALUE_NULL)
            {
                server.onDecodeEncryptedEnd(traceId, authorization);
                server.decoder = decodeReasoning;
            }
            else
            {
                server.onDecodeParseError(traceId);
            }
        }

        return server.decoder == decodeIgnore ? limit : server.position();
    }

    private int decodeEncryptedHold(
        LlmOpenaiResponsesServer server,
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

        while (server.decoder == decodeEncryptedHold && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (event == JsonEvent.VALUE_STRING)
            {
                final boolean deferred = parser.deferredBytes();
                final JsonStringView chars = parser.getStringView();
                final int length = chars.length();

                server.onDecodeEncryptedHold(traceId, chars, !deferred);

                if (deferred)
                {
                    parser.consumed(length);
                }
                else
                {
                    server.onDecodeEncryptedHoldEnd(traceId);
                    server.decoder = decodeReasoning;
                }
            }
            else if (event == JsonEvent.VALUE_NULL)
            {
                server.onDecodeEncryptedHoldEnd(traceId);
                server.decoder = decodeReasoning;
            }
            else
            {
                server.onDecodeParseError(traceId);
            }
        }

        return server.decoder == decodeIgnore ? limit : server.position();
    }

    private int decodeTail(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        server.onDecodeTail(traceId, authorization);

        return server.position();
    }

    private int decodeToolsStart(
        LlmOpenaiResponsesServer server,
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
        LlmOpenaiResponsesServer server,
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
                server.itemType = null;
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
        LlmOpenaiResponsesServer server,
        long traceId,
        int offset,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        String type = null;
        boolean typeKey = false;
        boolean missing = false;
        int depth = 0;

        while (type == null && !missing && parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                missing = depth < 0;
                break;
            case KEY_NAME:
                typeKey = depth == 0 && matches(parser.getStringView(), MEMBER_TYPE);
                break;
            case VALUE_STRING:
                if (parser.deferredBytes())
                {
                    parser.consumed(parser.getStringView().length());
                }
                else if (depth == 0 && typeKey)
                {
                    type = parser.getString();
                }
                break;
            default:
                break;
            }
        }

        parser.rewind();

        if (missing)
        {
            server.onDecodeInvalidRequest(traceId, MESSAGE_TOOL_TYPE_MISSING);
        }
        else if (type != null)
        {
            server.itemType = type;
            server.probe.begin(TYPE_FUNCTION.equals(type)
                ? LlmSectionType.TOOL_DEFINITION
                : LlmSectionType.SERVER_TOOL_DEFINITION);
            server.probe.configure(type, null, null);

            if (server.probed(traceId, limit))
            {
                server.decoder = decodeToolStart;
            }
        }
        else if (limit - offset >= decodeMax)
        {
            server.onDecodeInvalidRequest(traceId, MESSAGE_TOOL_TYPE_MISSING);
        }
    }

    private int decodeToolStart(
        LlmOpenaiResponsesServer server,
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

            server.capture(server.probe.type(), NONE, decodeTools);
            server.probing = true;
            server.decoder = decodeToolCapture;
        }

        return server.position();
    }

    private int decodeItemId(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final String value = server.decodeString(traceId, decodeItem);

        if (value != null)
        {
            server.itemId = value;
        }

        return server.position();
    }

    private int decodeItemCapture(
        LlmOpenaiResponsesServer server,
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

        while (server.decoder == decodeItemCapture && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

            if (event == JsonEvent.KEY_NAME &&
                server.probe.depth() == 1 &&
                server.blockSection == LlmSectionType.SERVER_TOOL_CALL &&
                LlmOpenaiResponsesItems.resultMember(server.itemType, parser.getStringView()))
            {
                server.onDecodeItemResult(traceId, authorization, eventFrom);
            }

            server.probe.onEvent(event, parser);
            server.captureDepth = server.probe.depth();

            if (server.captureDepth == 0 && !deferred)
            {
                server.endCapture(traceId, authorization);
            }

            eventFrom = server.position();
        }

        return server.position();
    }

    private int decodeToolCapture(
        LlmOpenaiResponsesServer server,
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

        while (server.decoder == decodeToolCapture && parser.hasNextEvent())
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

    private int decodeOutputItem(
        LlmOpenaiResponsesServer server,
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
                if (matches(key, MEMBER_CALL_ID))
                {
                    server.decoder = decodeOutputCallId;
                }
                else if (matches(key, MEMBER_OUTPUT_ITEM))
                {
                    if (server.outputCallId == null)
                    {
                        server.onDecodeInvalidRequest(traceId, MESSAGE_CALL_ID_ORDER);
                        break decode;
                    }
                    server.decoder = decodeOutputValue;
                }
                else
                {
                    server.skip(decodeOutputItem);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.decoder = decodeItems;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeOutputCallId(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final String value = server.decodeString(traceId, decodeOutputItem);

        if (value != null)
        {
            server.outputCallId = value;
        }

        return server.position();
    }

    private int decodeOutputValue(
        LlmOpenaiResponsesServer server,
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
                server.text(LlmSectionType.TOOL_RESULT, decodeOutputItem);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
                break decode;
            case START_ARRAY:
                server.roleType = LlmSectionType.TOOL_RESULT;
                server.partsThen = decodeOutputItem;
                server.decoder = decodeParts;
                break;
            case VALUE_NULL:
                server.decoder = decodeOutputItem;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeText(
        LlmOpenaiResponsesServer server,
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

        while (server.decoder == decodeText && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);

            if (progress < 0)
            {
                break;
            }

            eventFrom = server.position();
        }

        return server.position();
    }

    private int decodeTextValue(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int progress,
        int limit,
        JsonEvent event,
        int eventFrom)
    {
        final JsonParserEx parser = server.parser;

        boolean stalled = false;

        server.decoder = decodeText;

        if (event == JsonEvent.VALUE_STRING)
        {
            final boolean deferred = parser.deferredBytes();
            final JsonStringView chars = parser.getStringView();
            final int length = chars.length();
            final int take = server.onDecodeText(traceId, authorization, chars, !deferred, eventFrom);

            if (!deferred && take == length)
            {
                server.textOpen = false;
                server.decoder = server.textThen;
            }
            else if (take == length)
            {
                parser.consumed(take);
            }
            else
            {
                server.onDecodeParseError(traceId);
            }

            stalled = take < length;
        }
        else if (event == JsonEvent.VALUE_NULL && !server.textOpen)
        {
            server.decoder = server.textThen;
        }
        else
        {
            server.onDecodeParseError(traceId);
        }

        return stalled ? -1 : server.decoder == decodeIgnore ? limit : server.position();
    }

    private int decodeMaxOutputTokensValue(
        LlmOpenaiResponsesServer server,
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

    private int decodeControlSeparator(
        LlmOpenaiResponsesServer server,
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
        LlmOpenaiResponsesServer server,
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

    private int decodeSkip(
        LlmOpenaiResponsesServer server,
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

    private static LlmSectionType itemSection(
        int kind)
    {
        return switch (kind)
        {
        case LlmOpenaiResponsesItems.KIND_FUNCTION_CALL -> LlmSectionType.TOOL_CALL;
        case LlmOpenaiResponsesItems.KIND_CALL -> LlmSectionType.SERVER_TOOL_CALL;
        case LlmOpenaiResponsesItems.KIND_RESULT -> LlmSectionType.SERVER_TOOL_RESULT;
        default -> null;
        };
    }

    private static LlmSectionType mediaSectionType(
        String type)
    {
        return switch (type)
        {
        case TYPE_INPUT_IMAGE -> LlmSectionType.USER_IMAGE;
        case TYPE_INPUT_FILE -> LlmSectionType.USER_DOCUMENT;
        case TYPE_INPUT_AUDIO -> LlmSectionType.USER_AUDIO;
        default -> null;
        };
    }

    private static LlmSectionType roleSectionType(
        String role)
    {
        return switch (role)
        {
        case "system", "developer" -> LlmSectionType.SYSTEM_INSTRUCTION;
        case "user" -> LlmSectionType.USER_TEXT;
        case "assistant" -> LlmSectionType.ASSISTANT_TEXT;
        default -> null;
        };
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
        LlmOpenaiResponsesServer server,
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
        LlmOpenaiResponsesServer server,
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

    private final class LlmOpenaiResponsesServer
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
        private final Runnable deauthorize;
        private final JsonParserEx parser;

        private LlmOpenaiResponsesStream stream;
        private LlmOpenaiResponsesServerDecoder decoder;

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

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private int encodeSent;
        private boolean encodeFlushing;

        private long pendingReplyEndTraceId;
        private long pendingReplyEndAuthorization;

        private int messages;
        private int itemMessage = NONE;
        private int assistantMessage = NONE;
        private String role;
        private LlmSectionType roleType;
        private String itemType;
        private String itemId;
        private String outputCallId;
        private LlmOpenaiResponsesServerDecoder partsThen;

        private final LlmHeldTail tail;
        private LlmSectionType tailSection;
        private int tailMessage;
        private LlmOpenaiResponsesServerDecoder tailThen;
        private String reasoningId;
        private int reasoningSections;
        private int reasoningSummaries;
        private LlmSectionPipeline imagePipeline;
        private LlmSectionPipeline documentPipeline;
        private LlmSectionPipeline citationsPipeline;

        private boolean probing;
        private final LlmOpenaiResponsesAttributeProbe probe;
        private final LlmSectionAttributes sentAttributes;
        private final LlmSectionAttributes finAttributes;
        private String blockExtension;
        private LlmSectionPipeline toolCallPipeline;
        private LlmSectionPipeline toolDefinitionPipeline;
        private LlmSectionPipeline serverToolCallPipeline;
        private LlmSectionPipeline serverToolResultPipeline;

        private LlmOpenaiResponsesServerDecoder skipThen;
        private int skipDepth;

        private boolean streaming;
        private boolean streamOptions;
        private boolean usageRequested;
        private boolean obfuscation = true;
        private int maxOutputTokens = NO_MAX_OUTPUT_TOKENS;

        private LlmOpenaiResponsesServerDecoder textThen;
        private boolean textOpen;

        private LlmOpenaiResponsesServerDecoder captureThen;
        private boolean captureOpen;
        private int captureDepth;
        private int captureFrom;


        private final LlmSectionAttributes blockAttributes;

        private long decodeTraceId;
        private long decodeAuthorization;

        private LlmSectionType blockSection;
        private int blockMessage;
        private int blockFlags;
        private final StringBuilder controlKey = new StringBuilder();
        private LlmSectionPipeline controlPipeline;
        private LlmOpenaiControlToCanonical controlTransform;
        private LlmSectionPipeline choicePipeline;
        private String choiceMember;
        private final JsonGeneratorEx generator;
        private LlmSectionPipeline blockPipeline;
        private LlmSectionPipeline.Output sectionSink;
        private final LlmSectionPipeline.Output emitSink = (buffer, offset, length, last) ->
            onDecodeSectionData(decodeTraceId, decodeAuthorization, buffer, offset, length, last);
        private final LlmSectionPipeline.Output holdSink = (buffer, offset, length, last) ->
            onDecodeHold(buffer, offset, length);
        private final LlmSectionPipeline.Output blockOutput = (buffer, offset, length, last) ->
            sectionSink.put(buffer, offset, length, last);
        private final LlmMembersOutput memberOutput = new LlmMembersOutput((buffer, offset, length) ->
            onDecodeSectionData(decodeTraceId, decodeAuthorization, buffer, offset, length, false));

        private LlmOpenaiResponsesServer(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            LlmBindingConfig binding,
            long authorization,
            String contentType,
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
            this.deauthorize = deauthorize;
            this.parser = JsonEx.createParser(PARSER_CONFIG);
            this.generator = JsonEx.createGenerator();
            this.blockAttributes = new LlmSectionAttributes();
            this.sentAttributes = new LlmSectionAttributes();
            this.finAttributes = new LlmSectionAttributes();
            this.probe = new LlmOpenaiResponsesAttributeProbe();
            this.tail = new LlmHeldTail(encodePool);
            this.sectionSink = emitSink;
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

                int previous;
                LlmOpenaiResponsesServerDecoder previousDecoder;
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
                final int window = stream != null ? Math.min(limit - offset, decodeRoom()) : limit - offset;

                if (window >= Math.min(MIN_WINDOW, limit - offset))
                {
                    final int windowLimit = offset + window;

                    final boolean windowLast = decodedLast && windowLimit == limit;

                    decodedWindowLimit = windowLimit;
                    decodeTraceId = traceId;
                    decodeAuthorization = authorization;
                    parser.wrap(buffer, offset, windowLimit, windowLast);

                    final LlmOpenaiResponsesServerDecoder previous = decoder;

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

                    if (stalled && windowLast && decoder != decodeEnd && decoder != decodeIgnore &&
                        decoder != decodeTail)
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

        private int decodeRoom()
        {
            final int framing = TEXT_PREFIX.length + TEXT_SUFFIX.length;
            final boolean inside = decoder == decodeText && !Flags.hasInit(blockFlags);
            final int reserve = inside ? TEXT_SUFFIX.length : framing;
            final int sections = 1 + (framing + MIN_TEXT_INPUT - 1) / MIN_TEXT_INPUT;
            final int growth = decoder == decodeText ? sections : Math.max(sections, CONTROL_GROWTH);

            return Math.max(available() - reserve, 0) / growth;
        }

        private int available()
        {
            final int available = stream != null ? (int) Math.max(stream.initialAvailable(), 0L) : 0;
            final int appendRoom = streaming && !streamOptions ? Math.min(STREAM_OPTIONS_SECTION.capacity(), available >> 1) : 0;

            return available - appendRoom;
        }

        private int position()
        {
            return decodedWindowLimit - parser.remaining();
        }

        private void skip(
            LlmOpenaiResponsesServerDecoder then)
        {
            skipThen = then;
            skipDepth = 0;
        }

        private void beginMessage(
            LlmSectionType type)
        {
            beginMessage(type == LlmSectionType.ASSISTANT_TEXT);
        }

        private void beginInstructions()
        {
            itemMessage = NONE;
            assistantMessage = NONE;
        }

        private void beginMessage(
            boolean assistant)
        {
            itemMessage = assistant && assistantMessage != NONE ? assistantMessage : messages++;
            assistantMessage = assistant ? itemMessage : NONE;
        }

        private String decodeString(
            long traceId,
            LlmOpenaiResponsesServerDecoder then)
        {
            String value = null;

            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                switch (event)
                {
                case VALUE_STRING:
                    if (parser.deferredBytes())
                    {
                        parser.consumed(parser.getStringView().length());
                        skip(then);
                        decoder = decodeSkip;
                    }
                    else
                    {
                        value = parser.getString();
                        decoder = then;
                    }
                    break;
                case START_OBJECT:
                case START_ARRAY:
                    skip(then);
                    skipDepth = 1;
                    decoder = decodeSkip;
                    break;
                default:
                    decoder = then;
                    break;
                }
            }

            return value;
        }

        private void onDecodeItemType(
            String type,
            int kind)
        {
            itemType = type;

            if (kind == LlmOpenaiResponsesItems.KIND_REASONING)
            {
                onDecodeReasoningItem();
            }
            else
            {
                beginMessage(false);
                outputCallId = null;
                decoder = decodeOutputItem;
            }
        }

        private void onDecodeReasoningItem()
        {
            beginMessage(true);
            reasoningId = itemId;
            reasoningSections = 0;
            reasoningSummaries = 0;
            tail.release();
            decoder = decodeReasoning;
        }

        private void onDecodeNonArray(
            JsonEvent event,
            LlmOpenaiResponsesServerDecoder then)
        {
            switch (event)
            {
            case START_OBJECT:
                skip(then);
                skipDepth = 1;
                decoder = decodeSkip;
                break;
            case VALUE_STRING:
                if (parser.deferredBytes())
                {
                    parser.consumed(parser.getStringView().length());
                    skip(then);
                    decoder = decodeSkip;
                }
                else
                {
                    decoder = then;
                }
                break;
            default:
                decoder = then;
                break;
            }
        }

        private void onDecodeReasoningPart(
            long traceId,
            long authorization)
        {
            if (reasoningSections > 0)
            {
                onDecodeData(traceId, authorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), true);
            }

            text(LlmSectionType.ASSISTANT_REASONING, decodeSummaryPart);
        }

        private void onDecodeEncrypted(
            long traceId)
        {
            final boolean stream = reasoningSummaries > 0;
            final boolean fresh = reasoningSections == 0;

            if (stream && fresh)
            {
                onDecodeBlock(LlmSectionType.ASSISTANT_REASONING, itemMessage);
            }

            final int length = encodeMemberHeader(stream && fresh ? '{' : ',');

            if (stream)
            {
                onDecodeData(traceId, decodeAuthorization, pieceBuffer, 0, length, false);
            }
            else if (!tail.append(initialId, pieceBuffer, 0, length))
            {
                onDecodeInvalidRequest(traceId, MESSAGE_ENCRYPTED_BOUND);
            }
        }

        private int encodeMemberHeader(
            int separator)
        {
            reservePiece(ENCRYPTED_KEY.length + (reasoningId != null ? reasoningId.length() * ESCAPE_EXPANSION : 0) +
                CONTROL_OPEN_CAPACITY * 4);

            int length = 1;

            if (reasoningId != null)
            {
                generator.wrap(pieceBuffer, 0, pieceBytes.length);
                generator.reset();
                generator.writeStartObject().write(ID_MEMBER, reasoningId);
                length = generator.length();
                reasoningId = null;
                pieceBytes[length++] = ',';
            }

            pieceBytes[0] = (byte) separator;
            System.arraycopy(ENCRYPTED_KEY, 0, pieceBytes, length, ENCRYPTED_KEY.length);

            return length + ENCRYPTED_KEY.length;
        }

        private int onDecodeEncryptedStream(
            long traceId,
            long authorization,
            JsonStringView chars,
            boolean fin)
        {
            final int length = chars.length();
            final int take = memberAllowance(chars, fin);

            if (take == length)
            {
                final int escaped = encodeMember(chars, 0, take);

                if (escaped > 0)
                {
                    onDecodeData(traceId, authorization, pieceBuffer, 0, escaped, false);
                }
            }

            return take;
        }

        private void onDecodeEncryptedEnd(
            long traceId,
            long authorization)
        {
            onDecodeData(traceId, authorization, STRING_CLOSE, 0, STRING_CLOSE.capacity(), false);
        }

        private void onDecodeEncryptedHold(
            long traceId,
            JsonStringView chars,
            boolean fin)
        {
            final int escaped = encodeMember(chars, 0, chars.length());

            if (!tail.append(initialId, pieceBuffer, 0, escaped))
            {
                onDecodeInvalidRequest(traceId, MESSAGE_ENCRYPTED_BOUND);
            }
        }

        private void onDecodeEncryptedHoldEnd(
            long traceId)
        {
            if (!tail.append(initialId, STRING_CLOSE, 0, STRING_CLOSE.capacity()))
            {
                onDecodeInvalidRequest(traceId, MESSAGE_ENCRYPTED_BOUND);
            }
        }

        private int encodeMember(
            JsonStringView chars,
            int from,
            int to)
        {
            reservePiece(body.length(chars, from, to));

            return body.transfer(chars, from, to, pieceTarget.wrap(pieceBuffer.segment(), pieceBuffer.wrapAdjustment()));
        }

        private int memberAllowance(
            JsonStringView chars,
            boolean fin)
        {
            final int length = chars.length();
            final int credit = available();

            int cost = fin ? STRING_CLOSE.capacity() : 0;
            int take = 0;
            int index = 0;
            boolean fits = true;

            if (cost + body.length(chars, 0, length) <= credit)
            {
                take = length;
                index = length;
            }

            while (fits && index < length)
            {
                final char c = chars.charAt(index);
                final boolean pair = Character.isHighSurrogate(c) && index + 1 < length &&
                    Character.isLowSurrogate(chars.charAt(index + 1));
                final int width = pair ? 2 : 1;

                cost += outputWidth(chars, index, width);
                fits = cost <= credit;

                if (fits)
                {
                    index += width;
                    take = index;
                }
            }

            return take;
        }

        private void onDecodeReasoningEnd(
            long traceId)
        {
            final boolean open = reasoningSections > 0;

            if (reasoningId != null)
            {
                appendPendingId(traceId);
            }

            if (open && !tail.held())
            {
                onDecodeData(traceId, decodeAuthorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), true);
                decoder = decodeItems;
            }
            else if (tail.held())
            {
                if (!tail.append(initialId, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity()))
                {
                    onDecodeInvalidRequest(traceId, MESSAGE_ENCRYPTED_BOUND);
                }
                else
                {
                    if (!open)
                    {
                        tail.buffer().putByte(0, (byte) '{');
                    }

                    beginTail(open ? null : LlmSectionType.ASSISTANT_REASONING, decodeItems);
                }
            }
            else
            {
                decoder = decodeItems;
            }

            reasoningId = null;
            reasoningSections = 0;
            reasoningSummaries = 0;
        }

        private void appendPendingId(
            long traceId)
        {
            reservePiece(reasoningId.length() * ESCAPE_EXPANSION + CONTROL_OPEN_CAPACITY * 4);

            generator.wrap(pieceBuffer, 0, pieceBytes.length);
            generator.reset();
            generator.writeStartObject().write(ID_MEMBER, reasoningId);
            pieceBytes[0] = (byte) ',';

            if (!tail.append(initialId, pieceBuffer, 0, generator.length()))
            {
                onDecodeInvalidRequest(traceId, MESSAGE_ENCRYPTED_BOUND);
            }
        }

        private void beginTail(
            LlmSectionType type,
            LlmOpenaiResponsesServerDecoder then)
        {
            tailSection = type;
            tailMessage = itemMessage;
            tailThen = then;
            decoder = decodeTail;
        }

        private void onDecodeTail(
            long traceId,
            long authorization)
        {
            boolean progress = true;

            while (progress && tail.remaining() > 0)
            {
                final int take = (int) Math.min(tail.remaining(), Math.max(available(), 0));

                progress = take > 0;

                if (progress)
                {
                    final boolean first = tail.offset() == 0;
                    final int init = first && tailSection != null ? Flags.INIT : Flags.NONE;
                    final int flags = take == tail.remaining() ? Flags.fin(init) : init;

                    stream.doAppData(traceId, authorization, flags, first ? tailSection : null, tailMessage, null, null,
                        tail.buffer(), tail.offset(), take);
                    tail.consumed(take);
                }
            }

            if (tail.remaining() == 0)
            {
                tail.release();
                decoder = tailThen;
            }
        }

        private void onDecodeAnnotations(
            LlmOpenaiResponsesServerDecoder then)
        {
            onDecodeBlock(LlmSectionType.CITATIONS, itemMessage);
            tail.release();
            sectionSink = holdSink;
            captureThen = then;
            captureOpen = true;
            captureDepth = 1;
            captureFrom = position() - 1;
        }

        private void onDecodeHold(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (!tail.append(initialId, buffer, offset, length))
            {
                onDecodeInvalidRequest(decodeTraceId, MESSAGE_ANNOTATIONS_BOUND);
            }
        }

        private void onDecodePartEnd()
        {
            if (tail.length() > EMPTY_ARRAY_LENGTH)
            {
                beginTail(LlmSectionType.CITATIONS, decodeParts);
            }
            else
            {
                tail.release();
                decoder = decodeParts;
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

        private void capture(
            LlmSectionType type,
            int message,
            LlmOpenaiResponsesServerDecoder then)
        {
            final int from = position() - 1;

            onDecodeBlock(type, message);

            if (type == LlmSectionType.SERVER_TOOL_CALL || type == LlmSectionType.SERVER_TOOL_RESULT)
            {
                blockExtension = itemType;
            }

            captureThen = then;
            captureOpen = true;
            captureDepth = 1;
            captureFrom = from;
        }

        private void onDecodeItemResult(
            long traceId,
            long authorization,
            int eventFrom)
        {
            int keyFrom = eventFrom;

            while (decodedInput.getByte(keyFrom) != '"')
            {
                keyFrom++;
            }

            if (eventFrom > captureFrom)
            {
                onDecodeData(traceId, authorization, decodedInput, captureFrom, eventFrom - captureFrom, false);
            }

            onDecodeData(traceId, authorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), true);

            probing = false;
            onDecodeBlock(LlmSectionType.SERVER_TOOL_RESULT, blockMessage);
            blockExtension = itemType;
            blockAttributes.toolCallId(probe.attributes().toolCallId());

            onDecodeData(traceId, authorization, OBJECT_OPEN, 0, OBJECT_OPEN.capacity(), false);

            captureFrom = keyFrom;
        }

        private void text(
            LlmSectionType type,
            LlmOpenaiResponsesServerDecoder then)
        {
            textThen = then;
            textOpen = false;
            onDecodeBlock(type, itemMessage);
        }

        private void onDecodeRootKey(
            CharSequence key)
        {
            if (matches(key, "instructions"))
            {
                decoder = decodeInstructions;
            }
            else if (matches(key, "input"))
            {
                decoder = decodeInput;
            }
            else if (matches(key, "tools"))
            {
                decoder = decodeToolsStart;
            }
            else if (matches(key, "max_output_tokens"))
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
            LlmOpenaiResponsesServerDecoder then)
        {
            captureThen = then;

            if (matches(key, MEMBER_TOOL_CHOICE) || matches(key, MEMBER_TEXT) || matches(key, MEMBER_REASONING) ||
                matches(key, MEMBER_SERVICE_TIER))
            {
                controlConverted(key, CONTROL_CONTINUE);
            }
            else
            {
                onDecodeControlKey(key, true, ',');
            }

            captureOpen = true;
            captureDepth = 0;
            captureFrom = position();
            decoder = decodeControlSeparator;
        }

        private void controlConverted(
            CharSequence key,
            DirectBufferEx separator)
        {
            onDecodeSectionData(decodeTraceId, decodeAuthorization, separator, 0, separator.capacity(), false);

            choiceMember = matches(key, MEMBER_TEXT)
                ? MEMBER_TEXT
                : matches(key, MEMBER_REASONING) ? MEMBER_REASONING : MEMBER_TOOL_CHOICE;
            blockPipeline = choicePipeline();
            blockPipeline.begin(blockAttributes);
            memberOutput.begin();

            onDecodeControlKey(key, false, '{');
        }

        private void closeControl(
            long traceId,
            long authorization)
        {
            onDecodeData(traceId, authorization, EMPTY_VALUE, 0, 0, true);
        }

        private void control(
            CharSequence key,
            int message,
            LlmOpenaiResponsesServerDecoder then)
        {
            onDecodeBlock(LlmSectionType.CONTROL, message);

            captureThen = then;

            if (matches(key, MEMBER_STREAM) || matches(key, MEMBER_STREAM_OPTIONS))
            {
                blockPipeline = controlPipeline();
                blockPipeline.begin(blockAttributes);
                onDecodeControlKey(key, false, '{');
            }
            else if (matches(key, MEMBER_TOOL_CHOICE) || matches(key, MEMBER_TEXT) || matches(key, MEMBER_REASONING) ||
                matches(key, MEMBER_SERVICE_TIER))
            {
                controlConverted(key, CONTROL_BEGIN);
            }
            else
            {
                onDecodeControlKey(key, true, '{');
            }

            captureOpen = true;
            captureDepth = 0;
            captureFrom = position();
            decoder = decodeControlSeparator;
        }

        private void endCapture(
            long traceId,
            long authorization)
        {
            flushCapture(traceId, authorization, captureThen != decodeRootControl);

            if (blockSection == LlmSectionType.CONTROL && blockPipeline != null)
            {
                if (captureThen == decodeRootControl &&
                    !onDecodeBlockData(traceId, authorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), true))
                {
                    onDecodeBlockRejected(traceId);
                }

                if (blockPipeline == controlPipeline)
                {
                    onDecodeControl();
                }
            }

            blockPipeline = null;
            sectionSink = emitSink;
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
                stream = new LlmOpenaiResponsesStream(this);
                stream.doAppBegin(traceId, authorization, model);

                decoder = decodeStart;
            }
            else
            {
                onDecodeModelNotFound(traceId, model);
            }
        }

        private int onDecodeText(
            long traceId,
            long authorization,
            JsonStringView chars,
            boolean fin,
            int eventFrom)
        {
            textOpen = true;

            final int length = chars.length();
            final int take = textAllowance(chars, fin);
            final boolean finished = fin && take == length;

            if (take > 0)
            {
                final int encoded = encodePiece(chars, 0, take);

                onDecodeData(traceId, authorization, pieceBuffer, 0, encoded, false);
            }

            if (finished)
            {
                final boolean reasoning = blockSection == LlmSectionType.ASSISTANT_REASONING;
                final int encoded = encodePieceEnd(reasoning);

                onDecodeData(traceId, authorization, pieceBuffer, 0, encoded, !reasoning);
            }

            return take;
        }

        private int textAllowance(
            JsonStringView chars,
            boolean fin)
        {
            final int length = chars.length();
            final int credit = available();

            int cost = Flags.hasInit(blockFlags) ? TEXT_PREFIX.length : 0;
            int take = 0;
            int index = 0;
            boolean fits = true;

            if (cost + body.length(chars, 0, length) +
                (fin ? TEXT_SUFFIX.length : 0) <= credit)
            {
                take = length;
                index = length;
            }

            while (fits && index < length)
            {
                final char c = chars.charAt(index);
                final boolean pair = Character.isHighSurrogate(c) && index + 1 < length &&
                    Character.isLowSurrogate(chars.charAt(index + 1));
                final int width = pair ? 2 : 1;
                final int next = cost + outputWidth(chars, index, width);
                final boolean last = index + width == length;

                fits = next + (last && fin ? TEXT_SUFFIX.length : 0) <= credit;

                if (fits)
                {
                    cost = next;
                    index += width;
                    take = index;
                }
            }

            if (length == 0 && fin && cost + TEXT_SUFFIX.length > credit)
            {
                take = -1;
            }

            return take;
        }

        private void reservePiece(
            int capacity)
        {
            if (pieceBytes.length < capacity)
            {
                pieceBytes = Arrays.copyOf(pieceBytes, capacity);
                pieceBuffer = new UnsafeBufferEx(pieceBytes);
            }
        }

        private int encodePiece(
            JsonStringView chars,
            int from,
            int to)
        {
            final int prefix = Flags.hasInit(blockFlags) ? TEXT_PREFIX.length : 0;

            reservePiece(prefix + body.length(chars, from, to));

            final int length = prefix + body.transfer(chars, from, to,
                pieceTarget.wrap(pieceBuffer.segment(), pieceBuffer.wrapAdjustment() + prefix));

            System.arraycopy(TEXT_PREFIX, 0, pieceBytes, 0, prefix);

            return length;
        }

        private int encodePieceEnd(
            boolean open)
        {
            final int prefix = Flags.hasInit(blockFlags) ? TEXT_PREFIX.length : 0;
            final int suffix = open ? 1 : TEXT_SUFFIX.length;

            reservePiece(prefix + suffix);

            System.arraycopy(TEXT_PREFIX, 0, pieceBytes, 0, prefix);
            System.arraycopy(TEXT_SUFFIX, 0, pieceBytes, prefix, suffix);

            return prefix + suffix;
        }

        private int outputWidth(
            JsonStringView chars,
            int index,
            int width)
        {
            return body.length(chars, index, index + width);
        }

        private void onDecodeBlock(
            LlmSectionType type,
            int message)
        {
            blockSection = type;
            blockMessage = message;
            blockFlags = Flags.INIT;

            blockAttributes.clear();
            blockExtension = null;

            if (type == LlmSectionType.TOOL_RESULT)
            {
                blockAttributes.toolCallId(outputCallId);
            }

            blockPipeline = blockPipelineFor(type);

            if (blockPipeline != null)
            {
                blockPipeline.begin(blockAttributes);
            }
        }

        private LlmSectionPipeline blockPipelineFor(
            LlmSectionType type)
        {
            LlmSectionPipeline pipeline = null;

            switch (type)
            {
            case TOOL_CALL:
                if (toolCallPipeline == null)
                {
                    toolCallPipeline = new LlmSectionPipeline(new LlmOpenaiResponsesToolCallToCanonical(), transformBuffer);
                }
                pipeline = toolCallPipeline;
                break;
            case TOOL_DEFINITION:
                if (toolDefinitionPipeline == null)
                {
                    toolDefinitionPipeline = new LlmSectionPipeline(new LlmOpenaiResponsesToolDefinitionToCanonical(),
                        transformBuffer);
                }
                pipeline = toolDefinitionPipeline;
                break;
            case SERVER_TOOL_CALL:
                if (serverToolCallPipeline == null)
                {
                    serverToolCallPipeline = new LlmSectionPipeline(new LlmOpenaiResponsesServerToolToCanonical(true),
                        transformBuffer);
                }
                pipeline = serverToolCallPipeline;
                break;
            case SERVER_TOOL_RESULT:
                if (serverToolResultPipeline == null)
                {
                    serverToolResultPipeline = new LlmSectionPipeline(new LlmOpenaiResponsesServerToolToCanonical(false),
                        transformBuffer);
                }
                pipeline = serverToolResultPipeline;
                break;
            case USER_IMAGE:
                if (imagePipeline == null)
                {
                    imagePipeline = new LlmSectionPipeline(LlmOpenaiResponsesMediaToCanonical.image(), transformBuffer);
                }
                pipeline = imagePipeline;
                break;
            case USER_DOCUMENT:
                if (documentPipeline == null)
                {
                    documentPipeline = new LlmSectionPipeline(LlmOpenaiResponsesMediaToCanonical.document(), transformBuffer);
                }
                pipeline = documentPipeline;
                break;
            case CITATIONS:
                if (citationsPipeline == null)
                {
                    citationsPipeline = new LlmSectionPipeline(LlmOpenaiResponsesCitationsToCanonical.array(), transformBuffer);
                }
                pipeline = citationsPipeline;
                break;
            default:
                break;
            }

            return pipeline;
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
                    onDecodeBlockRejected(traceId);
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
                    captureThen == decodeRootControl ? memberOutput : blockOutput);
            }

            return accepted;
        }

        private LlmSectionPipeline choicePipeline()
        {
            if (choicePipeline == null)
            {
                choicePipeline = new LlmSectionPipeline(new LlmOpenaiResponsesControlToCanonical(), transformBuffer);
            }

            return choicePipeline;
        }

        private LlmSectionPipeline controlPipeline()
        {
            if (controlPipeline == null)
            {
                controlTransform = new LlmOpenaiControlToCanonical(NAME);
                controlPipeline = new LlmSectionPipeline(controlTransform, transformBuffer);
            }

            return controlPipeline;
        }

        private void onDecodeControlKey(
            CharSequence key,
            boolean prefixed,
            char open)
        {
            controlKey.setLength(0);

            final String shared = prefixed ? LlmSharedMembers.openaiResponses(key) : null;

            if (shared != null)
            {
                controlKey.append(SHARED_PREFIX).append(shared);
            }
            else
            {
                if (prefixed)
                {
                    controlKey.append(LlmOpenaiMembers.scope(NAME, key)).append(':');
                }

                controlKey.append(key);
            }

            reservePiece(controlKey.length() * ESCAPE_EXPANSION + CONTROL_OPEN_CAPACITY);

            generator.wrap(pieceBuffer, 0, pieceBytes.length);
            generator.reset();
            generator.writeStartObject().writeKey(controlKey);

            pieceBuffer.putByte(0, (byte) open);

            onDecodeBlockData(decodeTraceId, decodeAuthorization, pieceBuffer, 0, generator.length(), false);
        }

        private void onDecodeControl()
        {
            if (controlTransform.streamSeen())
            {
                streaming = controlTransform.streaming();
            }

            if (controlTransform.optionsSeen())
            {
                streamOptions = true;
                usageRequested = controlTransform.usageRequested();
                obfuscation = !controlTransform.obfuscationDisabled();
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
                reasoningSections += blockSection == LlmSectionType.ASSISTANT_REASONING ? 1 : 0;

                if (probing)
                {
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

            stream.doAppData(traceId, authorization, flags, init ? blockSection : null, blockMessage, attributes,
                init ? blockExtension : null, buffer, offset, length);

            blockFlags = Flags.NONE;
        }

        private void onDecodeRootEnd(
            long traceId)
        {
            if (streaming && !streamOptions)
            {
                streamOptions = true;
                usageRequested = false;
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

        private void onDecodeBlockRejected(
            long traceId)
        {
            final boolean format = blockPipeline == choicePipeline && MEMBER_TEXT.equals(choiceMember);

            onDecodeInvalidRequest(traceId, format ? MESSAGE_FORMAT_INVALID : MESSAGE_PARSE_ERROR);
        }

        private void onDecodeInvalidRequest(
            long traceId,
            String message)
        {
            onDecodeRejected(traceId, STATUS_BAD_REQUEST, null, message);
        }

        private void onDecodeModelNotFound(
            long traceId,
            String model)
        {
            onDecodeRejected(traceId, STATUS_NOT_FOUND, CODE_MODEL_NOT_FOUND, MESSAGE_MODEL_NOT_FOUND.formatted(model));
        }

        private void onDecodeRejected(
            long traceId,
            int status,
            String code,
            String message)
        {
            decoder = decodeIgnore;

            initialAck = initialSeq;
            doNetWindow(traceId);
            doNetError(traceId, initialAuthorization, status, null, null, code, message);
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

            doNetError(traceId, authorization, status, error.type().asString(), error.param().asString(),
                error.code().asString(), error.message().asString());
        }

        private void doNetError(
            long traceId,
            long authorization,
            int status,
            String message)
        {
            doNetError(traceId, authorization, status, null, null, null, message);
        }

        private void doNetError(
            long traceId,
            long authorization,
            int status,
            String type,
            String param,
            String code,
            String message)
        {
            final DirectBufferEx body = asBuffer(errorBody(status, type, param, code, message));

            cleanupDecodeSlot();

            doNetBegin(traceId, authorization, 0L, Integer.toString(status), CONTENT_TYPE_JSON);
            doNetData(body, 0, body.capacity(), Flags.COMPLETE, EMPTY_OCTETS, 0L, traceId, authorization);

            if (encodeSlot != NO_SLOT)
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
            cleanupEncodeSlot();
            cleanupTail();
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

        private void cleanupTail()
        {
            tail.release();
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
                    replyDebit = context.supplyDebit(replyId, replyBudgetId, this::resumeEncode);
                }
                replyDebit.declare(traceId, replyPad, 0);
            }

            resumeEncode(traceId);
        }

        private void resumeEncode(
            long traceId)
        {
            flushEncodeSlot(traceId);

            if (stream != null)
            {
                stream.drainRecorded(traceId);
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
            OctetsFW payload,
            int flags,
            long budgetId,
            long traceId,
            long authorization)
        {
            doNetData(payload.buffer(), payload.offset(), payload.sizeof(), flags, EMPTY_OCTETS, budgetId, traceId,
                authorization);
        }

        private void doNetData(
            DirectBufferEx buffer,
            int offset,
            int length,
            int flags,
            OctetsFW attributes,
            long budgetId,
            long traceId,
            long authorization)
        {
            final long available = replyCredit();

            if (encodeSlot == NO_SLOT && available > 0 && length <= available &&
                claimReply(traceId, length, length) == length)
            {
                doNetData(traceId, authorization, flags, budgetId, length + replyPad, attributes, buffer, offset, length);
                if (stream != null)
                {
                    stream.ackReply(traceId);
                }
            }
            else
            {
                doNetEncode(buffer, offset, length, flags, attributes, budgetId, traceId, authorization);
            }
        }

        private void doNetEncode(
            DirectBufferEx buffer,
            int offset,
            int length,
            int flags,
            OctetsFW attributes,
            long budgetId,
            long traceId,
            long authorization)
        {
            final int entryLength = QUEUE_ENTRY_OVERHEAD + attributes.sizeof() + length;

            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(replyId);
            }

            if (encodeSlot == NO_SLOT || encodeSlotOffset + entryLength > encodeMax)
            {
                cleanup(traceId);
            }
            else
            {
                final MutableDirectBufferEx slotBuffer = encodePool.buffer(encodeSlot);
                final int limit = queueEntryRW.wrap(slotBuffer, encodeSlotOffset, slotBuffer.capacity())
                    .extension(attributes.buffer(), attributes.offset(), attributes.sizeof())
                    .traceId(traceId)
                    .authorization(authorization)
                    .budgetId(budgetId)
                    .reserved(0)
                    .flags(flags)
                    .sourceOffset(NONE)
                    .sourceLength(0)
                    .payload(buffer, offset, length)
                    .build()
                    .limit();

                encodeSlotOffset = limit;

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

        private int replyPendingAck()
        {
            return (int) (replySeq - replyAck);
        }

        private int replyWindow()
        {
            return replyMax - replyPendingAck();
        }

        private long replyCredit()
        {
            return replyWindow() - replyPad;
        }

        private boolean admits(
            int bytes)
        {
            return streaming || Math.min(bytes, encodeMax) <= encodeMax - encodeSlotOffset;
        }

        private int claimReply(
            long traceId,
            int minimum,
            int maximum)
        {
            return replyDebit != null ? replyDebit.claim(traceId, minimum, maximum) : maximum;
        }

        private void flushEncodeSlot(
            long traceId)
        {
            if (encodeSlot == NO_SLOT || encodeFlushing)
            {
                return;
            }

            encodeFlushing = true;

            final MutableDirectBufferEx buffer = encodePool.buffer(encodeSlot);

            boolean blocked = false;

            while (!blocked && encodeSlot != NO_SLOT && encodeSlotOffset > 0)
            {
                final long available = replyCredit();

                if (available <= 0)
                {
                    blocked = true;
                }
                else
                {
                    final LlmQueueEntryFW entry = queueEntryRO.wrap(buffer, 0, encodeSlotOffset);
                    final OctetsFW payload = entry.payload();
                    final int dataLength = payload.sizeof();
                    final int remaining = dataLength - encodeSent;
                    final int sliceMax = (int) Math.min(remaining, available);
                    final int sliceLength = sliceMax > 0 ? claimReply(traceId, 1, sliceMax) : sliceMax;

                    if (sliceLength <= 0 && remaining > 0)
                    {
                        blocked = true;
                    }
                    else
                    {
                        final int sliceOffset = payload.offset() + encodeSent;
                        final boolean init = encodeSent == 0 && Flags.hasInit(entry.flags());
                        encodeSent += sliceLength;

                        final int initFlags = init ? Flags.INIT : Flags.NONE;
                        final boolean last = encodeSent == dataLength && Flags.hasFin(entry.flags());
                        final int flags = last ? Flags.fin(initFlags) : initFlags;
                        final int size = entry.sizeof();
                        doNetData(entry.traceId(), entry.authorization(), flags, entry.budgetId(),
                            sliceLength + replyPad, init ? entry.extension() : EMPTY_OCTETS, buffer, sliceOffset,
                            sliceLength);

                        if (encodeSlot != NO_SLOT && encodeSent >= dataLength)
                        {
                            encodeSlotOffset -= size;
                            if (encodeSlotOffset > 0)
                            {
                                buffer.putBytes(0, buffer, size, encodeSlotOffset);
                            }
                            encodeSent = 0;
                        }
                    }
                }
            }

            if (!blocked && encodeSlot != NO_SLOT && encodeSlotOffset == 0)
            {
                encodePool.release(encodeSlot);

                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSent = 0;
            }

            encodeFlushing = false;

            if (stream != null)
            {
                stream.ackReply(traceId);
            }

            completeReplyEnd();
        }

        private void completeReplyEnd()
        {
            if (StreamState.replyClosing(state) && !StreamState.replyClosed(state) && encodeSlot == NO_SLOT &&
                (stream == null || stream.outboxSlot == NO_SLOT))
            {
                doNetEnd(pendingReplyEndTraceId, pendingReplyEndAuthorization, EMPTY_OCTETS);

                if (stream != null)
                {
                    stream.cleanupHeld();
                    stream.cleanupAccumulator();
                }
            }
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long affinity,
            String status,
            String responseContentType)
        {
            beginStatus = status;
            beginContentType = responseContentType;

            final HttpBeginExFW.Builder httpBeginExBuilder = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(statusHeader);

            if (responseContentType != null)
            {
                httpBeginExBuilder.headersItem(contentTypeHeader);
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

    private final class LlmOpenaiResponsesStream
    {
        private final LlmOpenaiResponsesServer server;

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

        private boolean flushed;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private long stagedReserved;
        private LlmOpenaiResponsesReplyDecoder decoder = decodeReplyFrame;
        private LlmOpenaiResponsesReplyDecoder dataDecoder = decodeReplySkipped;
        private LlmOpenaiResponsesReplyDecoder textThen = decodeReplyNext;
        private JsonParserEx replyParser;
        private byte[] replyTail;
        private int replyTailLength;
        private int replyFlags;
        private int replyReserved;
        private int replyFrameLength;
        private int replyPayloadFrom;
        private int replyWindowFrom;
        private int replySkipDepth;
        private int replyArgsDepth;
        private String replyMember = MEMBER_TEXT;
        private LlmOpenaiResponsesReplyDecoder replyMemberState = decodeReplyTextValue;
        private JsonGeneratorEx argsGenerator;
        private int stepDeltaOffset;
        private int stepSent;
        private int outboxSlot = NO_SLOT;
        private int stepDeltaLength;
        private LlmSectionPipeline.Output valueOutput;
        private LlmSectionPipeline.Output restOutput;
        private final LlmSectionPipeline.Output restEdges;
        private final LlmSectionPipeline.Output valueEdges;
        private LlmSectionPipeline.Output valueEdgeTarget;
        private int valueHeld = LlmEdgeTrim.BEFORE;
        private final LlmFrameTail valueTail = new LlmFrameTail();
        private final LlmFrameTail restTail = new LlmFrameTail();
        private int restHeld = LlmEdgeTrim.BEFORE;

        private LlmOpenaiResponsesStream(
            LlmOpenaiResponsesServer server)
        {
            this.server = server;
            this.generator = JsonEx.createGenerator();
            this.reasoningIdOutput = (buffer, offset, length, last) -> onReasoningId(buffer, offset, length, last);
            this.reasoningMembersOutput = (buffer, offset, length, last) -> onReasoningMembers(buffer, offset, length);
            this.citationsOutput = (buffer, offset, length, last) -> onCitations(buffer, offset, length);
            this.reasoningIdBytes = new byte[ID_CAPACITY];
            this.tail = new LlmHeldTail(holdPool);
            this.valueFilter = new LlmStringFilter((buffer, offset, length, last) -> onDelta(buffer, offset, length));
            this.tailOutput = (buffer, offset, length, last) -> onTail(buffer, offset, length);
            this.membersOutput = (buffer, offset, length, last) -> onMembers(buffer, offset, length);
            this.valueEdges = (buffer, offset, length, last) ->
                valueHeld = LlmEdgeTrim.put(valueHeld, valueEdgeTarget, buffer, offset, length, last);
            this.restEdges = (buffer, offset, length, last) ->
                restHeld = LlmEdgeTrim.put(restHeld, restOutput, buffer, offset, length, last);
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

            replyMax = server.streaming
                ? decodePool.slotCapacity()
                : responseMax + replyPadding;

            if (server.streaming && accumulator == null)
            {
                accumulator = new LlmContentAccumulator(accumulatePool, replyId);
            }

            responseId = llmBeginEx != null ? llmBeginEx.id().asString() : null;
            responseModel = llmBeginEx != null ? llmBeginEx.model().asString() : null;
            responseDialect = llmBeginEx != null ? llmBeginEx.dialect().asString() : null;

            server.doNetBegin(traceId, authorization, affinity, STATUS_OK,
                server.streaming ? CONTENT_TYPE_EVENT_STREAM : CONTENT_TYPE_JSON);

            encodeBudgetId = 0L;
            encodeTraceId = traceId;
            encodeAuthorization = authorization;

            beginEncode();

            if (server.streaming)
            {
                decoder = decodeResponseCreated;
                decodeReply();
            }
            else
            {
                doEncodeBegin();
                doEncodeFlush();
            }

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
                decodeSlot = decodePool.acquire(replyId);
            }

            final int required = REPLY_FRAME_OVERHEAD + extension.sizeof() + payload.sizeof();

            if (decodeSlot == NO_SLOT || decodeSlotOffset + required > decodePool.slotCapacity())
            {
                cleanup(traceId);
            }
            else
            {
                final MutableDirectBufferEx buffer = decodePool.buffer(decodeSlot);

                final LlmReplyFrameFW frame = replyFrameRW.wrap(buffer, decodeSlotOffset, decodePool.slotCapacity())
                    .traceId(traceId)
                    .authorization(authorization)
                    .budgetId(budgetId)
                    .reserved(reserved)
                    .flags(flags)
                    .extension(extension.buffer(), extension.offset(), extension.sizeof())
                    .payload(payload.buffer(), payload.offset(), payload.sizeof())
                    .build();

                decodeSlotOffset = frame.limit();
            }
        }

        private void decodeReply()
        {
            final boolean staged = decodeSlot != NO_SLOT;
            final MutableDirectBufferEx slot = staged ? decodePool.buffer(decodeSlot) : null;
            final DirectBufferEx buffer = staged ? slot : NO_FRAMES;

            LlmOpenaiResponsesReplyDecoder previous = null;
            int limit = staged ? decodeSlotOffset : 0;
            int progress = 0;
            while (progress <= limit && previous != decoder && (!staged || decodeSlot != NO_SLOT))
            {
                previous = decoder;
                progress = decoder.decode(this, buffer, progress, limit);

                if (staged && decodeSlot != NO_SLOT && progress > 0)
                {
                    slot.putBytes(0, slot, progress, limit - progress);
                    limit -= progress;
                    progress = 0;
                    decodeSlotOffset = limit;
                }
            }

            if (staged && decodeSlot != NO_SLOT)
            {
                if (limit == 0)
                {
                    cleanupDecodeSlot();
                }
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

        private int worstCase(
            LlmReplyFrameFW frame)
        {
            final int flags = frame.flags();

            return (flags & REPLY_FLAG_END) != 0
                ? replyPadding + hold.held()
                : (flags & REPLY_FLAG_ERROR) != 0
                    ? 0
                    : replyPadding + frame.payload().sizeof() * RESPONSE_GROWTH;
        }

        private void beginReplyFrame(
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

            encodeBudgetId = frame.budgetId();
            encodeTraceId = traceId;
            encodeAuthorization = authorization;

            beginEncode();

            final LlmDataExFW llmDataEx = frame.extension().get(llmDataExRO::tryWrap);
            final LlmSectionDataExFW section = llmDataEx != null ? llmDataEx.section() : null;

            if (Flags.hasInit(flags))
            {
                final LlmSectionType type = section != null ? section.type().get() : LlmSectionType.NONE;
                final String extension = section != null && section.extension().sizeof() > 0
                    ? section.extension().get((b, o, l) -> b.getStringWithoutLengthUtf8(o, l - o))
                    : null;

                replyAttributes.readFrom(section);
                replyTailLength = 0;

                doEncodeSection(type, section != null ? section.message() : NONE, extension);

                textThen = kind == KIND_REASONING ? decodeReplyValue : decodeReplyNext;
                replyMember = MEMBER_TEXT;
                replyMemberState = decodeReplyTextValue;

                if (kind == KIND_PROVIDER && valued())
                {
                    replyMember = MEMBER_ARGUMENTS;
                    replyMemberState = decodeReplyTextDescend;
                    textThen = decodeReplyRest;
                }

                if (kind == KIND_CALL)
                {
                    replyMember = MEMBER_ARGUMENTS;
                    replyMemberState = decodeReplyArgs;
                    replyArgsDepth = 0;
                    textThen = decodeReplyRest;
                    stepSent = 0;
                    replyParser().reset();

                    if (argsGenerator == null)
                    {
                        argsGenerator = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true));
                    }

                    argsGenerator.reset();
                }

                dataDecoder = switch (kind)
                {
                case KIND_TEXT, KIND_REFUSAL -> server.streaming ? decodeTextBegin : decodeReplyTextRoot;
                case KIND_REASONING -> server.streaming ? decodeReasoningBegin : decodeReplyTextRoot;
                case KIND_CITATIONS -> decodeReplyCitations;
                case KIND_CONTROL -> decodeReplyControl;
                case KIND_CALL -> server.streaming ? decodeCallBegin : decodeReplyTextRoot;
                case KIND_PROVIDER -> server.streaming ? decodeCallBegin : decodeReplyValue;
                case KIND_RESULT -> decodeReplyRest;
                default -> decodeReplySkipped;
                };
            }
            else
            {
                replyAttributes.mergeFrom(section);
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
        }

        private void endReplyFrame()
        {
            stagedReserved -= replyReserved;

            if (Flags.hasFin(replyFlags))
            {
                doEncodeEnd();
            }

            doEncodeFlush();
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
            LlmOpenaiResponsesReplyDecoder state)
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
                    decoder = textThen;
                }
            }
            else if (decoder != decodeReplyNext && decoder != textThen)
            {
                replyWindowFrom = windowLimit - replyParser.remaining() - offset;
            }

            return offset;
        }

        private boolean advanceFanOut(
            DirectBufferEx buffer,
            int offset,
            LlmSectionPipeline pipeline,
            LlmSectionPipeline.Output output,
            LlmFrameTail tail,
            int outputLimit)
        {
            final int payloadFrom = offset + replyPayloadFrom;
            final int payloadLimit = offset + replyFrameLength;
            final int from = payloadFrom - tail.length();

            boolean suspended = false;

            if (tail.length() > replyPayloadFrom)
            {
                rejectReply();
            }
            else
            {
                tail.restore((MutableDirectBufferEx) buffer, payloadFrom);

                final Status status = pipeline.advance(buffer, from, payloadLimit, Flags.hasFin(replyFlags), output,
                    outputLimit);

                if (status == Status.REJECTED || status == Status.SUSPENDED && pipeline.produced() == 0)
                {
                    rejectReply();
                }
                else if (status == Status.SUSPENDED)
                {
                    suspended = true;
                }
                else
                {
                    final int unconsumed = payloadLimit - from - pipeline.consumed();

                    if ((status == Status.STARVED || status == Status.ADVANCED) && unconsumed > 0)
                    {
                        tail.keep(buffer, payloadLimit - unconsumed, unconsumed);
                    }
                    else
                    {
                        tail.clear();
                    }
                }
            }

            return suspended;
        }

        private int valueLimit()
        {
            return responseMax;
        }

        private void flushValue()
        {
            if (!server.streaming)
            {
                doEncodeFlush();
                beginEncode();
            }
        }

        private void putControl(
            DirectBufferEx buffer,
            int offset)
        {
            final int payloadFrom = offset + replyPayloadFrom;

            if (!splitter.put(buffer, payloadFrom, offset + replyFrameLength - payloadFrom, Flags.hasFin(replyFlags)) ||
                hold.failed())
            {
                rejectReply();
            }
        }

        private int pauseReply(
            DirectBufferEx buffer,
            int offset)
        {
            replyWindowFrom = offset + replyFrameLength - replyParser.remaining() - offset;

            return offset;
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
            final long replyAckMax = replySeq - stagedReserved - hold.held();

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
            decodeReply();
        }

        private void finishReply(
            LlmReplyFrameFW frame)
        {
            final long traceId = frame.traceId();
            final long authorization = frame.authorization();

            final LlmEndExFW llmEndEx = frame.extension().get(llmEndExRO::tryWrap);
            final LlmUsageFW usage = llmEndEx != null ? llmEndEx.usage() : null;

            encodeBudgetId = 0L;
            encodeTraceId = traceId;
            encodeAuthorization = authorization;

            beginEncode();

            doEncodeEnd();
            doEncodeFinish(
                llmEndEx != null ? llmEndEx.finishReason().asString() : null,
                usage != null ? usage.inputTokens() : NONE,
                usage != null ? usage.cacheReadTokens() : NONE,
                usage != null ? usage.cacheWriteTokens() : NONE,
                usage != null ? usage.outputTokens() : NONE,
                usage != null ? usage.reasoningTokens() : NONE,
                usage != null ? usage.totalTokens() : NONE);

            cleanupHeld();

            doEncodeFlush();

            endReply(traceId, authorization);
        }

        private void endReply(
            long traceId,
            long authorization)
        {
            if (server.encodeSlot != NO_SLOT || outboxSlot != NO_SLOT)
            {
                server.state = StreamState.closingReply(server.state);
                server.pendingReplyEndTraceId = traceId;
                server.pendingReplyEndAuthorization = authorization;
            }
            else
            {
                server.doNetEnd(traceId, authorization, EMPTY_OCTETS);
                cleanupHeld();
                cleanupAccumulator();
            }

            if (StreamState.initialClosing(state) && !StreamState.initialClosed(state))
            {
                server.onDecodeEnd(traceId);
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

            final LlmAbortExFW llmAbortEx = server.streaming ? extension.get(llmAbortExRO::tryWrap) : null;
            final LlmErrorFW error = llmAbortEx != null ? llmAbortEx.error() : null;

            if (error != null && (error.type().asString() != null || error.message().asString() != null))
            {
                stageReply(traceId, authorization, 0L, 0, REPLY_FLAG_ERROR, extension, EMPTY_OCTETS);
                decodeReply();
            }
            else
            {
                cleanupDecodeSlot();
                cleanupHeld();

                server.doNetAbort(traceId, authorization, extension);
                server.cleanupEncodeSlot();
                cleanupAccumulator();

                if (StreamState.initialClosing(state) && !StreamState.initialClosed(state))
                {
                    server.onDecodeEnd(traceId);
                }
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
            LlmSectionAttributes attributes,
            String extension,
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

        private static final int REPLY_FRAME_OVERHEAD = 64;
        private static final int REPLY_TAIL_MAX = 64;
        private static final int PART_CAPACITY = 8;

        private static final int KIND_NONE = 0;
        private static final int KIND_TEXT = 1;
        private static final int KIND_SKIPPED = 2;
        private static final int KIND_CONTROL = 3;

        private static final int CONTROL_DROP = 0;
        private static final int CONTROL_HELD = 1;
        private static final int CONTROL_INLINE = 2;
        private static final int TAIL_MAX = 4096;
        private static final int KIND_CALL = 4;
        private static final int KIND_PROVIDER = 5;
        private static final int KIND_RESULT = 6;
        private static final int KIND_REFUSAL = 7;
        private static final int KIND_CITATIONS = 8;
        private static final int KIND_REASONING = 9;
        private static final int ITEM_REASONING = 4;
        private static final int PART_NONE = 0;
        private static final int PART_ACTIVE = 1;
        private static final int PART_PENDING = 2;
        private static final int PART_OPENING = 3;
        private static final int PART_CLOSING = 4;
        private static final int PART_DIRECT = 3;
        private static final int PART_KIND_TEXT = 0;
        private static final int PART_KIND_REFUSAL = 1;
        private static final int PART_VALUES = 2;
        private static final int RECORD_ITEM = 0x10;
        private static final int RECORD_TRAILER = 0x11;
        private static final int ID_CAPACITY = 64;
        private static final int LEX_NONE = 0;
        private static final int LEX_STRING = 1;
        private static final int LEX_ESCAPE = 2;
        private static final String TYPE_REASONING = LlmOpenaiResponsesItems.TYPE_REASONING;
        private static final String TYPE_SUMMARY_TEXT = "summary_text";
        private static final String MEMBER_SUMMARY_INDEX = "summary_index";
        private static final String MEMBER_ANNOTATION = "annotation";
        private static final String MEMBER_ANNOTATION_INDEX = "annotation_index";
        private static final String EVENT_SUMMARY_PART_ADDED = "response.reasoning_summary_part.added";
        private static final String EVENT_SUMMARY_PART_DONE = "response.reasoning_summary_part.done";
        private static final String EVENT_SUMMARY_TEXT_DELTA = "response.reasoning_summary_text.delta";
        private static final String EVENT_SUMMARY_TEXT_DONE = "response.reasoning_summary_text.done";
        private static final String EVENT_REFUSAL_DELTA = "response.refusal.delta";
        private static final String EVENT_REFUSAL_DONE = "response.refusal.done";
        private static final String EVENT_ANNOTATION_ADDED = "response.output_text.annotation.added";
        private static final DirectBufferEx ARRAY_OPEN = new UnsafeBufferEx("[".getBytes(UTF_8));
        private static final DirectBufferEx ARRAY_CLOSE = new UnsafeBufferEx("]".getBytes(UTF_8));
        private static final int ITEM_NONE = 0;
        private static final int ITEM_MESSAGE = 1;
        private static final int ITEM_CALL = 2;
        private static final int ITEM_PROVIDER = 3;
        private static final int VALUE_DELTA = 0;
        private static final int VALUE_TAIL = 1;
        private static final int VALUE_RESULT = 2;
        private static final String TYPE_FUNCTION_CALL = "function_call";
        private static final String MEMBER_ARGUMENTS = "arguments";
        private static final DirectBufferEx QUOTE = new UnsafeBufferEx("\"".getBytes(UTF_8));
        private static final DirectBufferEx COMMA = new UnsafeBufferEx(",".getBytes(UTF_8));
        private static final String MEMBER_USAGE = "usage";
        private static final Predicate<String> ALL_MEMBERS = name -> true;
        private static final String MEMBER_OBFUSCATION = "obfuscation";
        private static final String OBFUSCATION_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        private static final String OBJECT_RESPONSE = "response";
        private static final String STATUS_COMPLETED = "completed";
        private static final String STATUS_INCOMPLETE = "incomplete";
        private static final String STATUS_IN_PROGRESS = "in_progress";
        private static final String STATUS_FAILED = "failed";
        private static final String REASON_MAX_OUTPUT_TOKENS = "max_output_tokens";
        private static final String REASON_CONTENT_FILTER = "content_filter";
        private static final String ERROR_TYPE_SERVER = "server_error";

        private static final String EVENT_CREATED = "response.created";
        private static final String EVENT_IN_PROGRESS = "response.in_progress";
        private static final String EVENT_ITEM_ADDED = "response.output_item.added";
        private static final String EVENT_PART_ADDED = "response.content_part.added";
        private static final String EVENT_TEXT_DELTA = "response.output_text.delta";
        private static final String EVENT_TEXT_DONE = "response.output_text.done";
        private static final String EVENT_PART_DONE = "response.content_part.done";
        private static final String EVENT_ITEM_DONE = "response.output_item.done";
        private static final String EVENT_COMPLETED = "response.completed";
        private static final String EVENT_INCOMPLETE = "response.incomplete";
        private static final String EVENT_FAILED = "response.failed";
        private static final String EVENT_ERROR = "error";
        private static final Map<String, DirectBufferEx> EVENT_ATTRIBUTES = eventAttributes();

        private static Map<String, DirectBufferEx> eventAttributes()
        {
            final Map<String, DirectBufferEx> attributes = new HashMap<>();

            for (String name : List.of(EVENT_CREATED, EVENT_IN_PROGRESS, EVENT_ITEM_ADDED, EVENT_PART_ADDED, EVENT_TEXT_DELTA,
                EVENT_TEXT_DONE, EVENT_PART_DONE, EVENT_ITEM_DONE, EVENT_COMPLETED, EVENT_INCOMPLETE, EVENT_FAILED, EVENT_ERROR))
            {
                attributes.put(name, eventType(name));
            }

            for (String name : LlmOpenaiResponsesItems.events())
            {
                attributes.put(name, eventType(name));
            }

            return attributes;
        }

        private final JsonGeneratorEx generator;

        private String responseId;
        private String responseModel;
        private String responseDialect;

        private long encodeBudgetId;
        private String recordName;
        private int recordTotal;
        private int recordFrom;
        private int recordLength;
        private int eventSent;
        private boolean stepFinal;
        private LlmEndExFW finishEnd;
        private int finishIndex;
        private int memberLength;
        private int citationLength;
        private int citationScan;
        private int elementStart = NONE;
        private int elementEnd;
        private LlmOpenaiResponsesReplyDecoder summaryFollow;
        private LlmOpenaiResponsesReplyDecoder advanceFollow;
        private String errorCode;
        private String errorMessage;
        private String errorParam;
        private String closeStatus = STATUS_COMPLETED;
        private int recordRefCount;
        private int[] recordRefs;
        private int sectionMessage = NONE;
        private int sectionPartKind;
        private LlmOpenaiResponsesReplyDecoder textFollow;
        private LlmOpenaiResponsesReplyDecoder itemFollow;
        private int statusIndex;

        private LlmSectionPipeline controlPipeline;
        private LlmOpenaiControlFromCanonical controlTransform;
        private final LlmSectionPipeline.Output encodeControl = (buffer, offset, length, last) ->
            onEncodeControl(buffer, offset, length);
        private int controlMessage;
        private int controlMode;
        private boolean controlOpen;
        private final LlmControlHold hold = new LlmControlHold(this::heldBuffer);
        private final LlmMemberSplitter splitter = new LlmMemberSplitter(this::onEncodeMember);
        private int heldSlot = NO_SLOT;
        private String controlName;
        private boolean usageVerbatim;

        private int kind;
        private int sequence;
        private int itemCount;
        private int itemMessage = NONE;
        private int itemParts;
        private long encodeTraceId;
        private long encodeAuthorization;
        private String responseStatus = STATUS_COMPLETED;
        private String incompleteReason;

        private LlmContentAccumulator accumulator;
        private int openItem;
        private String itemType;
        private String itemId;
        private String itemCallId;
        private String itemName;
        private String itemOriginal;
        private int itemHeader = NONE;
        private int walkIndex;
        private int walkHeader;
        private int partValue;
        private int partMode;
        private int partKind;
        private int partAnnotations = NONE;
        private final LlmSectionPipeline.Output reasoningIdOutput;
        private final LlmSectionPipeline.Output reasoningMembersOutput;
        private final LlmSectionPipeline.Output citationsOutput;
        private LlmSectionPipeline reasoningIdPipeline;
        private LlmSectionPipeline reasoningMembersPipeline;
        private LlmSectionPipeline citationsPipeline;
        private String reasoningId;
        private byte[] reasoningIdBytes;
        private int reasoningIdLength;
        private int elementSlot = NO_SLOT;
        private MutableDirectBufferEx elementBuffer;
        private int elementLength;
        private int elementDepth;
        private int elementLex;
        private int annotationCount;
        private String sectionItemType;
        private String valueMember;
        private LlmSectionPipeline valuePipeline;
        private LlmSectionPipeline restPipeline;
        private LlmSectionPipeline extrasPipeline;
        private LlmSectionPipeline membersPipeline;
        private LlmSectionPipeline resultPipeline;
        private LlmOpenaiResponsesServerToolFromCanonical membersTransform;
        private final Map<String, LlmSectionPipeline> valuePipelines = new HashMap<>();
        private final LlmStringFilter valueFilter;
        private final LlmSectionPipeline.Output tailOutput;
        private final LlmSectionPipeline.Output membersOutput;
        private final LlmHeldTail tail;
        private boolean membersStarted;

        private void beginEncode()
        {
            generator.wrap(copyBuffer, 0, copyBuffer.capacity());
        }

        private void doEncodeFlush()
        {
            if (!server.streaming && encodeLength() > 0)
            {
                server.doNetData(copyBuffer, 0, encodeLength(), Flags.COMPLETE, EMPTY_OCTETS, encodeBudgetId,
                    encodeTraceId, encodeAuthorization);
            }

            ackReply(encodeTraceId);
        }

        private void doEncodeBegin()
        {
            if (server.streaming)
            {
                eventResponse(EVENT_CREATED);
                eventResponse(EVENT_IN_PROGRESS);
            }
            else
            {
                generator.writeStartObject();
                writeEnvelope();
            }
        }

        private void doEncodeSection(
            LlmSectionType type,
            int message,
            String extension)
        {
            sectionItemType = extension;

            kind = switch (type)
            {
            case CONTROL -> KIND_CONTROL;
            case ASSISTANT_TEXT -> KIND_TEXT;
            case ASSISTANT_REFUSAL -> KIND_REFUSAL;
            case ASSISTANT_REASONING -> NAME.equals(responseDialect) ? KIND_REASONING : KIND_SKIPPED;
            case CITATIONS -> citable(message) ? KIND_CITATIONS : KIND_SKIPPED;
            case TOOL_CALL -> KIND_CALL;
            case SERVER_TOOL_CALL -> providerCall(extension) ? KIND_PROVIDER : KIND_SKIPPED;
            case SERVER_TOOL_RESULT -> awaitingResult(extension) ? KIND_RESULT : KIND_SKIPPED;
            default -> KIND_SKIPPED;
            };

            switch (kind)
            {
            case KIND_TEXT:
                sectionMessage = message;
                sectionPartKind = PART_KIND_TEXT;

                if (!server.streaming)
                {
                    sectionPart(message, PART_KIND_TEXT);
                }

                replyParser().reset();
                break;
            case KIND_REFUSAL:
                sectionMessage = message;
                sectionPartKind = PART_KIND_REFUSAL;

                if (!server.streaming)
                {
                    sectionPart(message, PART_KIND_REFUSAL);
                }

                replyParser().reset();
                break;
            case KIND_REASONING:
                if (!server.streaming)
                {
                    sectionReasoning();
                }
                break;
            case KIND_CITATIONS:
                sectionCitations();
                break;
            case KIND_CONTROL:
                controlMessage = message;
                controlMode = CONTROL_DROP;
                splitter.begin();
                break;
            case KIND_CALL:
                if (!server.streaming)
                {
                    sectionCall();
                }
                break;
            case KIND_PROVIDER:
                if (!server.streaming)
                {
                    sectionProvider();
                }
                break;
            case KIND_RESULT:
                sectionResult();
                break;
            default:
                break;
            }
        }

        private boolean citable(
            int message)
        {
            return partMode == PART_PENDING &&
                partKind == PART_KIND_TEXT &&
                openItem == ITEM_MESSAGE &&
                itemMessage == message;
        }

        private boolean providerCall(
            String extension)
        {
            return NAME.equals(responseDialect) &&
                extension != null &&
                LlmOpenaiResponsesItems.kind(extension) == LlmOpenaiResponsesItems.KIND_CALL;
        }

        private boolean awaitingResult(
            String extension)
        {
            return NAME.equals(responseDialect) &&
                openItem == ITEM_PROVIDER &&
                extension != null &&
                extension.equals(itemType) &&
                LlmOpenaiResponsesItems.split(extension);
        }

        private JsonParserEx replyParser()
        {
            if (replyParser == null)
            {
                replyParser = JsonEx.createParser();
            }

            return replyParser;
        }

        private LlmSectionPipeline controlPipeline()
        {
            if (controlPipeline == null)
            {
                controlTransform = new LlmOpenaiControlFromCanonical(NAME, NAME.equals(responseDialect));
                controlPipeline = new LlmSectionPipeline(controlTransform, transformBuffer);
            }

            return controlPipeline;
        }

        private void sectionPart(
            int message,
            int kind)
        {
            if (openItem != ITEM_MESSAGE || itemMessage != message)
            {
                closeOpenItem(STATUS_COMPLETED);
            }

            if (openItem == ITEM_NONE)
            {
                openMessageItem(message);
            }

            closePart();
            openPart(kind);
        }

        private void sectionReasoning()
        {
            if (openItem != ITEM_REASONING)
            {
                closeOpenItem(STATUS_COMPLETED);
                openReasoningItem();
            }

            prepareReasoningSection();
        }

        private void openReasoningRecord()
        {
            registerItem(TYPE_REASONING);

            openItem = ITEM_REASONING;
            itemParts = 0;
            partMode = PART_NONE;
        }

        private boolean splitPending()
        {
            return openItem == ITEM_PROVIDER &&
                server.streaming &&
                valueMember != null &&
                LlmOpenaiResponsesItems.split(itemType);
        }

        private void writeReasoningHead()
        {
            recordName = EVENT_ITEM_DONE;
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_ITEM_DONE)
                .writeStartObject(MEMBER_ITEM)
                .write(MEMBER_TYPE, TYPE_REASONING);
            writeSummary(itemCount - 1);
            generator.writeSegment(COMMA, 0, 1);
            recordTotal = generator.length();
        }

        private void writeProviderTail()
        {
            final int index = itemCount - 1;

            generator.writeStartObject().writeStartObject(MEMBER_ITEM);

            final int prefix = generator.length();

            writeFooter(index);

            final boolean footer = generator.length() > prefix;

            generator.writeEnd();
            generator.write(MEMBER_OUTPUT_INDEX, index).write(MEMBER_SEQUENCE_NUMBER, sequence++).writeEnd();
            recordTotal = generator.length();

            if (footer)
            {
                copyBuffer.putByte(prefix - 1, (byte) ',');
                recordFrom = prefix - 1;
            }
            else
            {
                recordFrom = prefix;
            }
        }

        private void writeReasoningTail()
        {
            final int index = itemCount - 1;
            final boolean identified = itemOriginal == null;

            generator.writeStartObject().writeStartObject(MEMBER_ITEM);

            final int prefix = generator.length();

            if (identified)
            {
                generator.write(MEMBER_ID, itemId(index));
            }

            generator.writeEnd();
            generator.write(MEMBER_OUTPUT_INDEX, index).write(MEMBER_SEQUENCE_NUMBER, sequence++).writeEnd();
            recordTotal = generator.length();

            if (identified)
            {
                copyBuffer.putByte(prefix - 1, (byte) ',');
                recordFrom = prefix - 1;
            }
            else
            {
                recordFrom = prefix;
            }
        }

        private boolean flushMembers()
        {
            boolean flushed = memberLength == 0;

            if (!flushed && writable())
            {
                copyBuffer.putBytes(0, memberBuffer, 0, memberLength);

                recordRefCount = 0;
                recordFrom = 0;
                recordTotal = memberLength;
                stepFinal = false;

                flushed = sendRecorded();

                if (flushed)
                {
                    memberLength = 0;
                }
            }

            return flushed;
        }

        private void beginFail(
            LlmReplyFrameFW frame)
        {
            final LlmAbortExFW llmAbortEx = frame.extension().get(llmAbortExRO::tryWrap);
            final LlmErrorFW error = llmAbortEx.error();
            final String code = error.code().asString();
            final String type = error.type().asString();
            final String message = error.message().asString();

            cleanupHeld();

            encodeBudgetId = 0L;
            encodeTraceId = frame.traceId();
            encodeAuthorization = frame.authorization();

            beginEncode();

            errorCode = code != null ? code : type != null ? type : ERROR_TYPE_SERVER;
            errorMessage = message != null ? message : "";
            errorParam = error.param().asString();
        }

        private void completeFail(
            LlmReplyFrameFW frame)
        {
            doEncodeFlush();
            endReply(frame.traceId(), frame.authorization());
        }

        private void beginFinish(
            LlmReplyFrameFW frame)
        {
            final LlmEndExFW end = frame.extension().get(llmEndExRO::tryWrap);

            encodeBudgetId = 0L;
            encodeTraceId = frame.traceId();
            encodeAuthorization = frame.authorization();

            beginEncode();
            doEncodeEnd();
            finishStatus(end != null ? end.finishReason().asString() : null);
            closeStatus = responseStatus;
        }

        private void writeFinishHead()
        {
            final String name = STATUS_INCOMPLETE.equals(responseStatus) ? EVENT_INCOMPLETE : EVENT_COMPLETED;

            recordName = name;
            generator.writeStartObject().write(MEMBER_TYPE, name).writeKey(OBJECT_RESPONSE);
            generator.writeStartObject();
            writeEnvelope();
            generator.writeStartArray(MEMBER_OUTPUT);
            recordTotal = generator.length();
        }

        private void writeFinishItem()
        {
            generator.writeStartArray();

            final int prefix = generator.length();

            writeItem(finishIndex, finishIndex == itemCount - 1 ? responseStatus : STATUS_COMPLETED);
            recordTotal = generator.length();

            if (finishIndex > 0)
            {
                copyBuffer.putByte(0, (byte) ',');
                recordFrom = 0;
            }
            else
            {
                recordFrom = prefix;
            }
        }

        private void writeFinishTail()
        {
            final LlmUsageFW usage = finishEnd != null ? finishEnd.usage() : null;

            generator.writeStartObject().writeStartObject(OBJECT_RESPONSE);

            final int prefix = generator.length();

            generator.write(MEMBER_STATUS, responseStatus);

            if (incompleteReason != null)
            {
                generator.writeStartObject(MEMBER_INCOMPLETE_DETAILS).write(MEMBER_REASON, incompleteReason).writeEnd();
            }

            hold.reference(generator, 0, ALL_MEMBERS, this::recordHeld);

            if (!usageVerbatim)
            {
                putUsage(
                    usage != null ? usage.inputTokens() : NONE,
                    usage != null ? usage.cacheReadTokens() : NONE,
                    usage != null ? usage.cacheWriteTokens() : NONE,
                    usage != null ? usage.outputTokens() : NONE,
                    usage != null ? usage.reasoningTokens() : NONE,
                    usage != null ? usage.totalTokens() : NONE);
            }

            generator.writeEnd();
            generator.write(MEMBER_SEQUENCE_NUMBER, sequence++).writeEnd();
            recordTotal = generator.length();
            copyBuffer.putByte(prefix - 2, (byte) ']');
            copyBuffer.putByte(prefix - 1, (byte) ',');
            recordFrom = prefix - 2;
        }

        private void recordHeld(
            int offset,
            int length)
        {
            recordReference(offset, length, REF_HOLD);
        }

        private void completeFinish(
            LlmReplyFrameFW frame)
        {
            doEncodeFlush();
            endReply(frame.traceId(), frame.authorization());
        }

        private void prepareReasoningEnd()
        {
            if (partMode == PART_ACTIVE)
            {
                accumulator.close();
                partMode = PART_CLOSING;
            }
        }

        private void prepareReasoningSection()
        {
            membersStarted = false;
            replyParser().reset();
            valuePipeline = reasoningIdPipeline();
            valuePipeline.begin(replyAttributes);
            valueOutput = valueEdges;
            valueEdgeTarget = reasoningIdOutput;
            restPipeline = reasoningMembersPipeline();
            restPipeline.begin(replyAttributes);
            restOutput = reasoningMembersOutput;
            beginFanOuts();
            reasoningIdLength = 0;
        }

        private void sectionCitations()
        {
            annotationCount = 0;
            elementDepth = 0;
            cleanupElement();
            elementLex = LEX_NONE;
            citationLength = 0;
            citationScan = 0;
            elementStart = NONE;
            citationsPipeline().begin(replyAttributes);
            beginFanOuts();

            if (server.streaming)
            {
                partAnnotations = accumulator.open();
            }
        }

        private int registerItem(
            String type)
        {
            if (itemCount == 0 && !server.streaming)
            {
                generator.writeStartArray(MEMBER_OUTPUT);
            }

            final int index = itemCount++;

            itemType = type;
            itemId = LlmOpenaiResponsesItems.prefix(type) + "_" + (index + 1);
            itemCallId = null;
            itemName = null;
            itemOriginal = null;

            if (server.streaming)
            {
                openItemRecord(type);
            }

            return index;
        }

        private void openItemRecord(
            String type)
        {
            itemHeader = accumulator.open(RECORD_ITEM);
            accumulator.appendInt(0);
            accumulator.appendText(type);
            accumulator.close();

            if (accumulator.truncated(itemHeader))
            {
                server.cleanup(encodeTraceId);
            }
        }

        private void sealItem()
        {
            accumulator.putInt(itemHeader, 0, accumulator.count() - itemHeader - 1);
        }

        private void closeItemRecord()
        {
            if (itemCallId != null || itemName != null || itemOriginal != null)
            {
                accumulator.open(RECORD_TRAILER);
                accumulator.appendText(itemCallId);
                accumulator.appendText(itemName);
                accumulator.appendText(itemOriginal);
                accumulator.close();
            }
        }

        private int itemFirst(
            int index)
        {
            return headerOf(index) + 1;
        }

        private int itemValues(
            int index)
        {
            return accumulator.getInt(headerOf(index), 0);
        }

        private int headerOf(
            int index)
        {
            int header = itemHeader;

            if (index != itemCount - 1)
            {
                int walked = walkIndex;
                header = walkHeader;

                if (index < walked)
                {
                    walked = 0;
                    header = 0;
                }

                while (walked < index)
                {
                    final int next = header + 1 + accumulator.getInt(header, 0);

                    header = accumulator.kind(next) == RECORD_TRAILER ? next + 1 : next;
                    walked++;
                }

                walkIndex = walked;
                walkHeader = header;
            }

            return header;
        }

        private String itemType(
            int index)
        {
            return index == itemCount - 1
                ? itemType
                : textAt(accumulator.buffer(), accumulator.offset(headerOf(index)) + Integer.BYTES);
        }

        private String itemCallId(
            int index)
        {
            return index == itemCount - 1 ? itemCallId : trailerText(index, 0);
        }

        private String itemName(
            int index)
        {
            return index == itemCount - 1 ? itemName : trailerText(index, 1);
        }

        private String itemOriginal(
            int index)
        {
            return index == itemCount - 1 ? itemOriginal : trailerText(index, 2);
        }

        private String trailerText(
            int index,
            int field)
        {
            final int header = headerOf(index);
            final int trailer = header + 1 + accumulator.getInt(header, 0);

            String text = null;

            if (accumulator.kind(trailer) == RECORD_TRAILER)
            {
                final DirectBufferEx buffer = accumulator.buffer();

                int position = accumulator.offset(trailer);

                for (int skip = 0; skip < field; skip++)
                {
                    position += Integer.BYTES + Math.max(buffer.getInt(position), 0);
                }

                text = textAt(buffer, position);
            }

            return text;
        }

        private static String textAt(
            DirectBufferEx buffer,
            int position)
        {
            final int length = buffer.getInt(position);

            return length >= 0 ? buffer.getStringWithoutLengthUtf8(position + Integer.BYTES, length) : null;
        }

        private void bindAttributes()
        {
            itemCallId = replyAttributes.toolCallId();
            itemName = replyAttributes.toolName();
        }

        private void closeOpenItem(
            String status)
        {
            switch (openItem)
            {
            case ITEM_MESSAGE:
                closePart();
                closeItem(status);
                break;
            case ITEM_REASONING:
                finishReasoningItem();
                break;
            case ITEM_PROVIDER:
                finishItem(itemCount - 1);
                break;
            default:
                break;
            }
        }

        private void openMessageItem(
            int message)
        {
            final int index = registerItem(TYPE_MESSAGE);

            openItem = ITEM_MESSAGE;
            itemMessage = message;
            itemParts = 0;

            if (server.streaming)
            {
                eventItemAdded();
            }
            else
            {
                generator.writeStartObject()
                    .write(MEMBER_ID, itemId(index))
                    .write(MEMBER_TYPE, TYPE_MESSAGE)
                    .writeStartArray(MEMBER_CONTENT);
            }
        }

        private void openPart(
            int kind)
        {
            itemParts++;
            partKind = kind;
            partMode = PART_ACTIVE;
            partAnnotations = NONE;
            annotationCount = 0;

            if (server.streaming)
            {
                partValue = accumulator.open(kind);
                eventPartAdded();
            }
            else if (kind == PART_KIND_REFUSAL)
            {
                generator.writeStartObject()
                    .write(MEMBER_TYPE, TYPE_REFUSAL)
                    .writeKey(MEMBER_REFUSAL)
                    .writeStartEscaped();
                partMode = PART_DIRECT;
            }
            else
            {
                generator.writeStartObject()
                    .write(MEMBER_TYPE, TYPE_OUTPUT_TEXT)
                    .writeKey(MEMBER_TEXT)
                    .writeStartEscaped();
            }
        }

        private void endPart()
        {
            if (server.streaming)
            {
                accumulator.close();
                partMode = PART_PENDING;
            }
            else if (partMode == PART_DIRECT)
            {
                closePart();
            }
            else
            {
                generator.writeEndEscaped();
                partMode = PART_PENDING;
            }
        }

        private void closePart()
        {
            if (partMode != PART_NONE)
            {
                if (partMode == PART_DIRECT)
                {
                    generator.writeEndEscaped().writeEnd();
                }
                else
                {
                    closeTextPart();
                }

                partMode = PART_NONE;
            }
        }

        private void closeTextPart()
        {
            if (partMode == PART_ACTIVE)
            {
                generator.writeEndEscaped();
            }

            if (annotationCount > 0)
            {
                generator.writeSegment(ARRAY_CLOSE, 0, 1, Completion.COMPLETE);
            }
            else
            {
                generator.writeStartArray(MEMBER_ANNOTATIONS).writeEnd();
            }

            generator.writeStartArray(MEMBER_LOGPROBS).writeEnd().writeEnd();
        }

        private void ensureEncodeRoom(
            int length)
        {
            if (copyBuffer.capacity() - encodeLength() - replyPadding < length)
            {
                doEncodeFlush();
                beginEncode();
            }
        }

        private void closeItem(
            String status)
        {
            if (server.streaming)
            {
                sealItem();
                eventItemDone(status);
            }
            else
            {
                generator.writeEnd()
                    .write(MEMBER_ROLE, ROLE_ASSISTANT)
                    .write(MEMBER_STATUS, status)
                    .writeEnd();
            }

            itemMessage = NONE;
            openItem = ITEM_NONE;
        }

        private int emitTextDelta(
            JsonStringView chars,
            int length)
        {
            int taken = 0;

            if (writable())
            {
                final String name = textEventName();
                final int written = generateTextDelta(name, chars, length, server.obfuscation ? fillObfuscation() : 0);

                if (sendDelta(name, written))
                {
                    accumulator.append(copyBuffer, stepDeltaOffset, stepDeltaLength);
                    sequence++;
                    taken = length;
                }
            }

            return taken;
        }

        private int emitSummaryText(
            JsonStringView chars,
            int length)
        {
            int taken = length;

            if (length > 0 && !membersStarted)
            {
                if (partMode == PART_NONE)
                {
                    itemParts++;
                    partValue = accumulator.open();
                    partMode = PART_OPENING;
                }

                if (partMode == PART_OPENING && emitStep(OP_SUMMARY_PART_ADDED))
                {
                    partMode = PART_ACTIVE;
                }

                taken = partMode == PART_ACTIVE ? emitTextDelta(chars, length) : 0;
            }

            return taken;
        }

        private String textEventName()
        {
            return kind == KIND_PROVIDER
                ? LlmOpenaiResponsesItems.deltaEvent(itemType)
                : kind == KIND_REASONING
                    ? EVENT_SUMMARY_TEXT_DELTA
                    : partKind == PART_KIND_REFUSAL ? EVENT_REFUSAL_DELTA : EVENT_TEXT_DELTA;
        }

        private boolean members()
        {
            return server.streaming &&
                kind == KIND_PROVIDER &&
                LlmOpenaiResponsesItems.deltaMember(sectionItemType) == null;
        }

        private boolean valued()
        {
            return server.streaming &&
                (kind == KIND_CALL || kind == KIND_PROVIDER && LlmOpenaiResponsesItems.deltaMember(sectionItemType) != null);
        }

        private void openCallRecord()
        {
            if (kind == KIND_CALL)
            {
                registerItem(TYPE_FUNCTION_CALL);

                openItem = ITEM_CALL;
                bindAttributes();
                valueMember = MEMBER_ARGUMENTS;
                restPipeline = extrasPipeline();
                restPipeline.begin(replyAttributes);
                restOutput = tailOutput;
            }
            else
            {
                registerItem(sectionItemType);

                openItem = ITEM_PROVIDER;
                bindAttributes();
                valueMember = LlmOpenaiResponsesItems.deltaMember(sectionItemType);
                restPipeline = membersPipeline(valueMember);
                restPipeline.begin(replyAttributes);
                restOutput = valueMember != null ? tailOutput : membersOutput;
                membersStarted = false;
            }

            beginFanOuts();
            tail.release();
            accumulator.open();
        }

        private boolean awaitsResult()
        {
            return kind == KIND_PROVIDER && LlmOpenaiResponsesItems.split(itemType);
        }

        private boolean resulting()
        {
            return server.streaming && kind == KIND_RESULT;
        }

        private void prepareProviderMembers()
        {
            accumulator.close();
            accumulator.open();
        }

        private void prepareProviderEnd()
        {
            accumulator.close();
            sealItem();
        }

        private void prepareCallEnd()
        {
            bindAttributes();

            accumulator.close();
            accumulator.open();
            accumulator.append(tail.buffer(), 0, tail.length());
            accumulator.close();
            sealItem();
        }

        private void prepareResultEnd()
        {
            accumulator.close();
            sealItem();
        }

        private void writeToolItemDone(
            int index)
        {
            final int first = itemFirst(index);
            final int count = itemValues(index);

            beginItemDone(index);
            generator.writeKey(valueMember);
            repeatString(first + VALUE_DELTA);

            if (count > VALUE_TAIL)
            {
                repeatMembers(first + VALUE_TAIL);
            }

            writeFooter(index);

            if (count > VALUE_RESULT)
            {
                repeatMembers(first + VALUE_RESULT);
            }

            endItemDone(index);
        }

        private void finishCallRecord()
        {
            closeItemRecord();
            openItem = ITEM_NONE;
            kind = KIND_NONE;
        }

        private void preparePartClose()
        {
            accumulator.close();

            if (partAnnotations == NONE)
            {
                partAnnotations = accumulator.open();
                accumulator.close();
            }
        }

        private void openMessageRecord()
        {
            final int index = registerItem(TYPE_MESSAGE);

            openItem = ITEM_MESSAGE;
            itemMessage = sectionMessage;
            itemParts = 0;
        }

        private void openPartRecord()
        {
            itemParts++;
            partKind = sectionPartKind;
            partMode = PART_ACTIVE;
            partAnnotations = NONE;
            annotationCount = 0;
            partValue = accumulator.open(partKind);
        }

        private boolean emitStep(
            int op)
        {
            boolean complete = false;

            if (writable())
            {
                final int restored = sequence;

                recordRefCount = 0;
                recordFrom = 0;
                stepFinal = op != OP_FINISH_HEAD && op != OP_FINISH_ITEM && op != OP_REASONING_HEAD && op != OP_PROVIDER_HEAD;
                generator.reset();
                generator.wrap(copyBuffer, 0, copyBuffer.capacity());

                switch (op)
                {
                case OP_TEXT_DONE:
                    if (partKind == PART_KIND_REFUSAL)
                    {
                        eventRefusalDone();
                    }
                    else
                    {
                        eventTextDone();
                    }
                    break;
                case OP_PART_DONE:
                    eventPartDone();
                    break;
                case OP_ITEM_DONE:
                    eventItemDone(closeStatus);
                    break;
                case OP_ITEM_ADDED:
                    eventItemAdded();
                    break;
                case OP_CALL_ADDED:
                    eventItemAdded(itemCount - 1, valueMember);
                    break;
                case OP_VALUE_DONE:
                    eventValueDone(LlmOpenaiResponsesItems.doneEvent(itemType), valueMember, itemCount - 1,
                        itemFirst(itemCount - 1) + VALUE_DELTA);
                    break;
                case OP_REASONING_ADDED:
                    eventReasoningAdded(itemCount - 1);
                    break;
                case OP_REASONING_HEAD:
                    writeReasoningHead();
                    break;
                case OP_REASONING_TAIL:
                    writeReasoningTail();
                    break;
                case OP_PROVIDER_HEAD:
                    beginItemDone(itemCount - 1);
                    recordTotal = generator.length();
                    break;
                case OP_PROVIDER_TAIL:
                    writeProviderTail();
                    break;
                case OP_ANNOTATION_ADDED:
                    eventAnnotationAdded(annotationCount);
                    break;
                case OP_ERROR:
                    eventError(errorCode, errorMessage, errorParam);
                    break;
                case OP_FAILED:
                    eventFailed(errorCode, errorMessage);
                    break;
                case OP_FINISH_HEAD:
                    writeFinishHead();
                    break;
                case OP_FINISH_ITEM:
                    writeFinishItem();
                    break;
                case OP_FINISH_TAIL:
                    writeFinishTail();
                    break;
                case OP_SUMMARY_PART_ADDED:
                    eventSummaryPartAdded();
                    break;
                case OP_SUMMARY_TEXT_DONE:
                    eventSummaryTextDone();
                    break;
                case OP_SUMMARY_PART_DONE:
                    eventSummaryPartDone();
                    break;
                case OP_INIT_STATUS:
                    eventStatus(LlmOpenaiResponsesItems.initEvents(itemType)[statusIndex], itemCount - 1);
                    break;
                case OP_STATUS:
                    eventStatus(LlmOpenaiResponsesItems.doneEvents(itemType)[statusIndex], itemCount - 1);
                    break;
                case OP_CALL_ITEM_DONE:
                    writeToolItemDone(itemCount - 1);
                    break;
                case OP_CREATED:
                    eventResponse(EVENT_CREATED);
                    break;
                case OP_IN_PROGRESS:
                    eventResponse(EVENT_IN_PROGRESS);
                    break;
                default:
                    eventPartAdded();
                    break;
                }

                generator.reset();

                complete = sendRecorded();

                if (!complete)
                {
                    sequence = restored;
                }
            }

            return complete;
        }

        private boolean sendRecorded()
        {
            final boolean sent = writeRecorded();

            if (sent)
            {
                commitRecorded();
            }

            return sent || parkRecorded();
        }

        private boolean sendDelta(
            String name,
            int written)
        {
            recordName = name;
            recordRefCount = 0;
            recordFrom = 0;
            recordTotal = written;
            stepFinal = true;

            return sendRecorded();
        }

        private void commitRecorded()
        {
            eventSent = stepFinal ? 0 : eventSent + recordLength;
            stepSent = 0;
        }

        private boolean parkRecorded()
        {
            outboxSlot = encodePool.acquire(server.replyId);

            final boolean parked = outboxSlot != NO_SLOT;

            if (parked)
            {
                encodePool.buffer(outboxSlot).putBytes(recordFrom, copyBuffer, recordFrom, recordTotal - recordFrom);
            }
            else
            {
                server.cleanup(encodeTraceId);
            }

            return parked;
        }

        private void drainRecorded(
            long traceId)
        {
            if (outboxSlot != NO_SLOT && writeRecorded())
            {
                commitRecorded();
                cleanupOutbox();

                ackReply(traceId);
                server.completeReplyEnd();
            }
        }

        private boolean writable()
        {
            return outboxSlot == NO_SLOT && server.encodeSlot == NO_SLOT;
        }

        private void cleanupOutbox()
        {
            if (outboxSlot != NO_SLOT)
            {
                encodePool.release(outboxSlot);
                outboxSlot = NO_SLOT;
            }
        }

        private boolean writeRecorded()
        {
            int total = recordTotal - recordFrom;

            for (int index = 0; index < recordRefCount; index++)
            {
                total += recordRefs[index * REF_STRIDE + 2];
            }

            recordLength = total;

            final DirectBufferEx generated = outboxSlot != NO_SLOT ? encodePool.buffer(outboxSlot) : copyBuffer;

            int from = recordFrom;
            int virtual = 0;
            boolean progress = true;

            for (int segment = 0; progress && segment <= recordRefCount; segment++)
            {
                final int to = segment < recordRefCount ? recordRefs[segment * REF_STRIDE] : recordTotal;

                progress = writeRecordedSegment(generated, from, to - from, virtual, total);
                virtual += to - from;

                if (progress && segment < recordRefCount)
                {
                    final int at = segment * REF_STRIDE;
                    final int length = recordRefs[at + 2];
                    final DirectBufferEx source = recordRefs[at + 3] == REF_HOLD ? hold.buffer() : accumulator.buffer();

                    progress = writeRecordedSegment(source, recordRefs[at + 1], length, virtual, total);
                    virtual += length;
                }

                from = to;
            }

            return stepSent == total;
        }

        private boolean writeRecordedSegment(
            DirectBufferEx source,
            int offset,
            int length,
            int virtual,
            int total)
        {
            boolean progress = true;

            while (progress && stepSent < virtual + length)
            {
                final int sent = Math.max(stepSent - virtual, 0);
                final int wanted = (int) Math.min(length - sent, server.replyCredit());
                final int slice = wanted > 0 ? server.claimReply(encodeTraceId, 1, wanted) : 0;

                progress = slice > 0;

                if (progress)
                {
                    final boolean init = eventSent + stepSent == 0;
                    final boolean last = stepFinal && stepSent + slice == total;
                    final int flags = (init ? Flags.INIT : Flags.NONE) | (last ? Flags.FIN : Flags.NONE);
                    final DirectBufferEx attribute = EVENT_ATTRIBUTES.get(recordName);

                    server.doNetData(encodeTraceId, encodeAuthorization, flags, encodeBudgetId, slice + server.replyPad,
                        init ? eventAttributesRO.wrap(attribute, 0, attribute.capacity()) : EMPTY_OCTETS, source,
                        offset + sent, slice);

                    stepSent += slice;
                }
            }

            return progress || stepSent >= virtual + length;
        }

        private int stepArgs(
            JsonParserEx parser,
            boolean boundary)
        {
            return server.streaming && !writable() ? STEP_WAIT : pullArgs(parser, boundary);
        }

        private int pullArgs(
            JsonParserEx parser,
            boolean boundary)
        {
            argsGenerator.wrap(argsBuffer, 0, argsBuffer.capacity());

            boolean more = true;
            boolean stop = false;
            boolean complete = false;
            boolean dropped = false;
            int pulled = 0;
            int pending = 0;

            while (more && !stop && argsGenerator.remaining() >= ARGS_HEADROOM)
            {
                more = parser.hasNextEvent();

                if (more)
                {
                    final JsonEvent event = parser.nextEvent();

                    pulled++;
                    stop = !boundary;

                    if (replyArgsDepth == 0 && event != JsonEvent.START_OBJECT)
                    {
                        dropped = true;
                        stop = true;
                        dropArgs(parser, event);
                    }
                    else
                    {
                        switch (event)
                        {
                        case START_OBJECT:
                            argsGenerator.writeStartObject();
                            replyArgsDepth++;
                            break;
                        case START_ARRAY:
                            argsGenerator.writeStartArray();
                            replyArgsDepth++;
                            break;
                        case END_OBJECT, END_ARRAY:
                            argsGenerator.writeEnd();
                            complete = --replyArgsDepth == 0;
                            stop |= complete;
                            break;
                        case VALUE_TRUE:
                            argsGenerator.write(true);
                            break;
                        case VALUE_FALSE:
                            argsGenerator.write(false);
                            break;
                        case VALUE_NULL:
                            argsGenerator.writeNull();
                            break;
                        default:
                            final boolean deferred = parser.deferredBytes();
                            final JsonStringView chars = parser.getStringView();
                            final Completion completion = deferred ? Completion.INCOMPLETE : Completion.COMPLETE;
                            final int before = argsGenerator.consumed();

                            if (event == JsonEvent.KEY_NAME)
                            {
                                argsGenerator.writeKey(chars, completion);
                            }
                            else if (event == JsonEvent.VALUE_NUMBER)
                            {
                                argsGenerator.writeNumber(chars, completion);
                            }
                            else
                            {
                                argsGenerator.write(chars, completion);
                            }

                            final int consumed = argsGenerator.consumed() - before;

                            if (consumed < chars.length() || deferred)
                            {
                                pending = consumed;
                                stop = true;
                            }
                            break;
                        }
                    }
                }
            }

            int outcome = STEP_WINDOW;

            if (dropped)
            {
                outcome = STEP_CONTINUE;
            }
            else if (pulled > 0)
            {
                final int length = argsGenerator.length();

                if (boundary && pending > 0)
                {
                    parser.consumed(pending);
                }

                final boolean written = server.streaming ? emitArgsDelta(length) : writeArgs(length);

                if (written)
                {
                    if (!boundary && pending > 0)
                    {
                        parser.consumed(pending);
                    }

                    decoder = complete ? decodeReplyTextKey : pending > 0 ? decodeReplyArgsContinue : decodeReplyArgs;
                    outcome = more || pending > 0 || complete ? STEP_CONTINUE : STEP_WINDOW;
                }
                else
                {
                    outcome = STEP_WAIT;
                }
            }

            return outcome;
        }

        private boolean emitArgsDelta(
            int length)
        {
            final String name = LlmOpenaiResponsesItems.deltaEvent(itemType);
            final int written = generateArgsDelta(name, length, server.obfuscation ? fillObfuscation() : 0);
            final boolean sent = sendDelta(name, written);

            if (sent)
            {
                accumulator.append(argsBuffer, 0, length);
                sequence++;
            }

            return sent;
        }

        private boolean writeArgs(
            int length)
        {
            ensureEncodeRoom(length);
            generator.writeSegment(argsBuffer, 0, length, Completion.INCOMPLETE);

            return true;
        }

        private void dropArgs(
            JsonParserEx parser,
            JsonEvent event)
        {
            if (event == JsonEvent.START_ARRAY)
            {
                replySkipDepth = 1;
                decoder = decodeReplyTextSkip;
            }
            else if (event == JsonEvent.VALUE_STRING && parser.deferredBytes())
            {
                parser.consumed(parser.getStringView().length());
                replySkipDepth = 0;
                decoder = decodeReplyTextSkip;
            }
            else
            {
                decoder = decodeReplyTextKey;
            }
        }

        private int generateArgsDelta(
            String name,
            int length,
            int obfuscation)
        {
            final int index = itemCount - 1;

            generator.reset();
            generator.wrap(copyBuffer, 0, copyBuffer.capacity());
            generator.writeStartObject().write(MEMBER_TYPE, name).writeKey(MEMBER_DELTA);
            generator.writeSegment(QUOTE, 0, 1, Completion.INCOMPLETE);
            generator.writeSegment(argsBuffer, 0, length, Completion.INCOMPLETE);
            generator.writeSegment(QUOTE, 0, 1, Completion.COMPLETE);
            generator.write(MEMBER_ITEM_ID, itemId(index));

            if (server.obfuscation)
            {
                writeObfuscation(obfuscation);
            }

            generator.write(MEMBER_OUTPUT_INDEX, index)
                .write(MEMBER_SEQUENCE_NUMBER, sequence)
                .writeEnd();

            final int written = generator.length();

            generator.reset();

            return written;
        }

        private int generateTextDelta(
            String name,
            JsonStringView chars,
            int take,
            int obfuscation)
        {
            final boolean provider = kind == KIND_PROVIDER || kind == KIND_REASONING;
            final boolean refusal = !provider && partKind == PART_KIND_REFUSAL;

            generator.reset();
            generator.wrap(copyBuffer, 0, copyBuffer.capacity());
            generator.writeStartObject().write(MEMBER_TYPE, name);

            if (!provider)
            {
                generator.write(MEMBER_CONTENT_INDEX, itemParts - 1);
            }

            generator.writeKey(MEMBER_DELTA).writeStartEscaped();

            stepDeltaOffset = generator.length();
            generator.writeSegment(chars, 0, take);
            stepDeltaLength = generator.length() - stepDeltaOffset;

            generator.writeEndEscaped();
            generator.write(MEMBER_ITEM_ID, itemId(itemCount - 1));

            if (!provider && !refusal)
            {
                generator.writeStartArray(MEMBER_LOGPROBS).writeEnd();
            }

            if (server.obfuscation && !refusal)
            {
                writeObfuscation(obfuscation);
            }

            generator.write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence);

            if (kind == KIND_REASONING)
            {
                generator.write(MEMBER_SUMMARY_INDEX, itemParts - 1);
            }

            generator.writeEnd();

            final int written = generator.length();

            generator.reset();

            return written;
        }

        private int onReplyText(
            JsonStringView chars,
            int length)
        {
            int taken = length;

            if (kind == KIND_REASONING && !server.streaming)
            {
                onReasoningText(chars, 0, length);
            }
            else if (kind == KIND_REASONING)
            {
                taken = emitSummaryText(chars, length);
            }
            else if (server.streaming && length > 0)
            {
                taken = emitTextDelta(chars, length);
            }
            else
            {
                onEncodeText(chars, 0, length);
            }

            return taken;
        }

        private void onEncodeText(
            JsonStringView chars,
            int from,
            int to)
        {
            if (to > from)
            {
                ensureEncodeRoom(body.length(chars, from, to));
                generator.writeSegment(chars, from, to);
            }
        }

        private void doEncodeEnd()
        {
            switch (kind)
            {
            case KIND_TEXT:
                endPart();
                break;
            case KIND_REFUSAL, KIND_CITATIONS:
                closePart();
                break;
            case KIND_REASONING:
                endReasoning();
                break;
            case KIND_CALL:
                endCall();
                break;
            case KIND_PROVIDER:
                endProvider();
                break;
            case KIND_RESULT:
                finishItem(itemCount - 1);
                break;
            default:
                break;
            }

            kind = KIND_NONE;
        }

        private void beginFanOuts()
        {
            valueTail.clear();
            restTail.clear();
            restHeld = LlmEdgeTrim.BEFORE;
            valueHeld = LlmEdgeTrim.BEFORE;
        }

        private void sectionCall()
        {
            closeOpenItem(STATUS_COMPLETED);

            final int index = registerItem(TYPE_FUNCTION_CALL);

            openItem = ITEM_CALL;
            bindAttributes();
            valueMember = MEMBER_ARGUMENTS;
            replyParser().reset();
            restPipeline = extrasPipeline();
            restPipeline.begin(replyAttributes);
            restOutput = tailOutput;
            beginFanOuts();
            tail.release();

            beginItem(index);
        }

        private void sectionProvider()
        {
            closeOpenItem(STATUS_COMPLETED);

            final int index = registerItem(sectionItemType);

            openItem = ITEM_PROVIDER;
            bindAttributes();
            valueMember = LlmOpenaiResponsesItems.deltaMember(sectionItemType);
            replyParser().reset();
            valuePipeline = valueMember != null ? memberPipeline(valueMember) : null;
            restPipeline = membersPipeline(valueMember);

            if (valuePipeline != null)
            {
                valuePipeline.begin(replyAttributes);
                valueFilter.reset();
            }

            valueOutput = valueFilter;
            restPipeline.begin(replyAttributes);
            restOutput = valueMember != null ? tailOutput : membersOutput;
            beginFanOuts();
            membersStarted = false;
            tail.release();

            beginItem(index);
        }

        private void sectionResult()
        {
            restPipeline = resultPipeline();
            restPipeline.begin(replyAttributes);
            restOutput = membersOutput;
            beginFanOuts();
            membersStarted = false;

            if (server.streaming)
            {
                accumulator.open();
            }
        }

        private void beginItem(
            int index)
        {
            generator.writeStartObject()
                .write(MEMBER_ID, itemId(index))
                .write(MEMBER_TYPE, itemType(index));

            if (valueMember != null)
            {
                generator.writeKey(valueMember);
                generator.writeSegment(QUOTE, 0, 1, Completion.INCOMPLETE);
            }
        }

        private void endCall()
        {
            final int index = itemCount - 1;

            bindAttributes();
            endDelta(index);
            finishItem(index);
        }

        private void endProvider()
        {
            final int index = itemCount - 1;
            final String type = itemType(index);

            bindAttributes();

            if (valueMember != null)
            {
                endDelta(index);
            }
            else
            {
                writeFooter(index);
            }

            if (!LlmOpenaiResponsesItems.split(type))
            {
                finishItem(index);
            }
        }

        private void endDelta(
            int index)
        {
            generator.writeSegment(QUOTE, 0, 1, Completion.COMPLETE);
            writeTail();
            writeFooter(index);
        }

        private void finishItem(
            int index)
        {
            generator.write(MEMBER_STATUS, STATUS_COMPLETED).writeEnd();
            openItem = ITEM_NONE;
        }

        private void writeTail()
        {
            if (tail.held())
            {
                generator.writeSegment(COMMA, 0, 1);
                generator.writeSegment(tail.buffer(), 0, tail.length());
            }
        }

        private void writeFooter(
            int index)
        {
            final String type = itemType(index);

            if (LlmOpenaiResponsesItems.hasCallId(type))
            {
                final String callId = itemCallId(index);

                generator.write(MEMBER_CALL_ID, callId != null ? callId : "");
            }

            if (LlmOpenaiResponsesItems.hasName(type))
            {
                final String name = itemName(index);

                generator.write(MEMBER_NAME, name != null ? name : "");
            }
        }

        private void onDelta(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (length > 0)
            {
                ensureEncodeRoom(length);
                generator.writeSegment(buffer, offset, length, Completion.INCOMPLETE);
            }
        }

        private void onTail(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            tail.append(replyId, buffer, offset, length, TAIL_MAX);
        }

        private void onMembers(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (length > 0 && server.streaming && kind == KIND_RESULT)
            {
                accumulator.append(buffer, offset, length);
            }
            else if (length > 0 && server.streaming)
            {
                if (!membersStarted)
                {
                    membersStarted = true;
                    memberBuffer.putBytes(memberLength, COMMA, 0, 1);
                    memberLength++;
                }

                memberBuffer.putBytes(memberLength, buffer, offset, length);
                memberLength += length;
                accumulator.append(buffer, offset, length);
            }
            else if (length > 0)
            {
                ensureEncodeRoom(length + 1);

                if (!membersStarted)
                {
                    membersStarted = true;
                    generator.writeSegment(COMMA, 0, 1);
                }

                generator.writeSegment(buffer, offset, length);

                if (server.streaming)
                {
                    accumulator.append(buffer, offset, length);
                }
            }
        }

        private LlmSectionPipeline extrasPipeline()
        {
            if (extrasPipeline == null)
            {
                final LlmOpenaiResponsesServerToolFromCanonical transform =
                    new LlmOpenaiResponsesServerToolFromCanonical(NAME);

                transform.configure(LlmOpenaiResponsesServerToolFromCanonical.PART_EXTRAS, TYPE_FUNCTION_CALL, null);
                extrasPipeline = new LlmSectionPipeline(transform, transformBuffer);
            }

            return extrasPipeline;
        }

        private LlmSectionPipeline memberPipeline(
            String member)
        {
            LlmSectionPipeline pipeline = valuePipelines.get(member);

            if (pipeline == null)
            {
                pipeline = new LlmSectionPipeline(new LlmOpenaiResponsesMemberToValue(member), transformBuffer);
                valuePipelines.put(member, pipeline);
            }

            return pipeline;
        }

        private LlmSectionPipeline membersPipeline(
            String exclude)
        {
            if (membersPipeline == null)
            {
                membersTransform = new LlmOpenaiResponsesServerToolFromCanonical(NAME);
                membersPipeline = new LlmSectionPipeline(membersTransform, transformBuffer);
            }

            membersTransform.configure(LlmOpenaiResponsesServerToolFromCanonical.PART_MEMBERS, sectionItemType, exclude);

            return membersPipeline;
        }

        private LlmSectionPipeline resultPipeline()
        {
            if (resultPipeline == null)
            {
                final LlmOpenaiResponsesServerToolFromCanonical transform =
                    new LlmOpenaiResponsesServerToolFromCanonical(NAME);

                transform.configure(LlmOpenaiResponsesServerToolFromCanonical.PART_RESULT, sectionItemType, null);
                resultPipeline = new LlmSectionPipeline(transform, transformBuffer);
            }

            return resultPipeline;
        }

        private void repeatString(
            int value)
        {
            final int length = accumulator.length(value);

            generator.writeSegment(QUOTE, 0, 1, Completion.INCOMPLETE);

            if (length > 0)
            {
                repeatSegments(value, length);
            }

            generator.writeSegment(QUOTE, 0, 1, Completion.COMPLETE);
        }

        private void repeatArray(
            int value)
        {
            final int length = accumulator.length(value);

            generator.writeSegment(ARRAY_OPEN, 0, 1, Completion.INCOMPLETE);

            if (length > 0)
            {
                repeatSegments(value, length);
            }

            generator.writeSegment(ARRAY_CLOSE, 0, 1, Completion.COMPLETE);
        }

        private void repeatMembers(
            int value)
        {
            final int length = accumulator.length(value);

            if (length > 0)
            {
                generator.writeSegment(COMMA, 0, 1);
                repeatSegments(value, length);
            }
        }

        private void repeatSegments(
            int value,
            int length)
        {
            emitReference(accumulator.offset(value), length);
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
                else if (controlMode == CONTROL_INLINE && length > 0)
                {
                    ensureEncodeRoom(length);
                    generator.writeSegment(payload, offset, length, Completion.INCOMPLETE);
                }
            }
        }

        private void onEncodeControlBegin(
            String name)
        {
            if (controlMessage != NONE)
            {
                controlMode = CONTROL_DROP;
            }
            else
            {
                controlMode = server.streaming || itemCount > 0 ? CONTROL_HELD : CONTROL_INLINE;
            }

            if (controlMode == CONTROL_INLINE)
            {
                controlName = name;
                generator.writeKey(name);
            }
            else if (controlMode == CONTROL_HELD && !hold.begin(name))
            {
                controlMode = CONTROL_DROP;
            }
        }


        private boolean onEncodeMember(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean init,
            boolean last)
        {
            if (init)
            {
                controlOpen = false;
                controlPipeline().begin(replyAttributes);
            }

            final boolean accepted = controlPipeline.feed(buffer, offset, length, last, encodeControl);

            if (last)
            {
                onEncodeControlEnd();
            }

            return accepted;
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
                generator.writeSegment(EMPTY_VALUE, 0, 0, Completion.COMPLETE);
                usageVerbatim |= MEMBER_USAGE.equals(controlName);
            }
            else if (controlMode == CONTROL_HELD)
            {
                usageVerbatim |= MEMBER_USAGE.equals(hold.end());
            }

            controlMode = CONTROL_DROP;
        }


        private void writeControls()
        {
            hold.write(generator, 0, ALL_MEMBERS, copyBuffer.capacity() - replyPadding, this::ensureEncodeRoom);
        }

        private MutableDirectBufferEx heldBuffer()
        {
            if (heldSlot == NO_SLOT)
            {
                heldSlot = holdPool.acquire(replyId);
            }

            return heldSlot != NO_SLOT ? holdPool.buffer(heldSlot) : null;
        }

        private void cleanup(
            long traceId)
        {
            doAppAbort(traceId);
            doAppReset(traceId);
            cleanupDecodeSlot();
            cleanupAccumulator();
            cleanupHeld();
        }

        private void cleanupHeld()
        {
            hold.reset();

            if (heldSlot != NO_SLOT)
            {
                holdPool.release(heldSlot);
                heldSlot = NO_SLOT;
            }
        }


        private void writeObfuscation()
        {
            writeObfuscation(fillObfuscation());
        }

        private int fillObfuscation()
        {
            final ThreadLocalRandom random = ThreadLocalRandom.current();
            final int length = OBFUSCATION_MIN + random.nextInt(OBFUSCATION_RANGE);

            for (int index = 0; index < length; index++)
            {
                obfuscationBytes[index] = (byte) OBFUSCATION_ALPHABET.charAt(random.nextInt(OBFUSCATION_ALPHABET.length()));
            }

            return length;
        }

        private void writeObfuscation(
            int length)
        {
            generator.writeKey(MEMBER_OBFUSCATION).writeStartEscaped();
            generator.writeSegment(obfuscationBuffer, 0, length);
            generator.writeEndEscaped();
        }

        private void doEncodeFinish(
            String finishReason,
            int inputTokens,
            int cacheReadTokens,
            int cacheWriteTokens,
            int outputTokens,
            int reasoningTokens,
            int totalTokens)
        {
            finishStatus(finishReason);

            closeOpenItem(responseStatus);

            if (itemCount == 0)
            {
                generator.writeStartArray(MEMBER_OUTPUT);
            }

            generator.writeEnd();
            writeResponseTail(inputTokens, cacheReadTokens, cacheWriteTokens, outputTokens, reasoningTokens, totalTokens);
            generator.writeEnd();
        }

        private void finishStatus(
            String finishReason)
        {
            responseStatus = STATUS_COMPLETED;
            incompleteReason = null;

            if (finishReason != null)
            {
                switch (finishReason)
                {
                case "length":
                    responseStatus = STATUS_INCOMPLETE;
                    incompleteReason = REASON_MAX_OUTPUT_TOKENS;
                    break;
                case "content-filter":
                    responseStatus = STATUS_INCOMPLETE;
                    incompleteReason = REASON_CONTENT_FILTER;
                    break;
                default:
                    if (NAME.equals(responseDialect) && finishReason.startsWith(DIALECT_PREFIX))
                    {
                        responseStatus = STATUS_INCOMPLETE;
                        incompleteReason = finishReason.substring(DIALECT_PREFIX.length());
                    }
                    break;
                }
            }
        }

        private void writeEnvelope()
        {
            if (responseId != null)
            {
                generator.write(MEMBER_ID, responseId);
            }

            generator.write(MEMBER_OBJECT, OBJECT_RESPONSE);

            if (responseModel != null)
            {
                generator.write(MEMBER_MODEL, responseModel);
            }
        }

        private void writeResponseTail(
            int inputTokens,
            int cacheReadTokens,
            int cacheWriteTokens,
            int outputTokens,
            int reasoningTokens,
            int totalTokens)
        {
            generator.write(MEMBER_STATUS, responseStatus);

            if (incompleteReason != null)
            {
                generator.writeStartObject(MEMBER_INCOMPLETE_DETAILS).write(MEMBER_REASON, incompleteReason).writeEnd();
            }

            writeControls();

            if (!usageVerbatim)
            {
                putUsage(inputTokens, cacheReadTokens, cacheWriteTokens, outputTokens, reasoningTokens, totalTokens);
            }
        }

        private void writeItem(
            int index,
            String status)
        {
            if (TYPE_MESSAGE.equals(itemType(index)))
            {
                writeMessageItem(index, status);
            }
            else if (TYPE_REASONING.equals(itemType(index)))
            {
                writeReasoningItem(index);
            }
            else
            {
                writeToolItem(index);
            }
        }

        private void writeToolItem(
            int index)
        {
            final String type = itemType(index);
            final int first = itemFirst(index);
            final int count = itemValues(index);
            final String member = LlmOpenaiResponsesItems.valueMember(type);

            generator.writeStartObject()
                .write(MEMBER_ID, itemId(index))
                .write(MEMBER_TYPE, type)
                .write(MEMBER_STATUS, STATUS_COMPLETED);

            if (member != null)
            {
                generator.writeKey(member);
                repeatString(first + VALUE_DELTA);
            }

            if (count > VALUE_TAIL)
            {
                repeatMembers(first + VALUE_TAIL);
            }

            writeFooter(index);

            if (count > VALUE_RESULT)
            {
                repeatMembers(first + VALUE_RESULT);
            }

            generator.writeEnd();
        }

        private void writeMessageItem(
            int index,
            String status)
        {
            generator.writeStartObject()
                .write(MEMBER_ID, itemId(index))
                .write(MEMBER_TYPE, TYPE_MESSAGE)
                .write(MEMBER_STATUS, status)
                .writeStartArray(MEMBER_CONTENT);

            final int first = itemFirst(index);
            final int last = first + itemValues(index);

            for (int part = first; part < last; part += PART_VALUES)
            {
                writePart(part);
            }

            generator.writeEnd()
                .write(MEMBER_ROLE, ROLE_ASSISTANT)
                .writeEnd();
        }

        private void writePart(
            int part)
        {
            if (accumulator.kind(part) == PART_KIND_REFUSAL)
            {
                generator.writeStartObject()
                    .write(MEMBER_TYPE, TYPE_REFUSAL)
                    .writeKey(MEMBER_REFUSAL);
            }
            else
            {
                generator.writeStartObject()
                    .write(MEMBER_TYPE, TYPE_OUTPUT_TEXT)
                    .writeKey(MEMBER_ANNOTATIONS);
                repeatArray(part + 1);
                generator.writeStartArray(MEMBER_LOGPROBS).writeEnd()
                    .writeKey(MEMBER_TEXT);
            }

            writeCompleted(part);
            generator.writeEnd();
        }

        private void writeCompleted(
            int part)
        {
            repeatString(part);
        }

        private String itemId(
            int index)
        {
            return index == itemCount - 1
                ? itemId
                : LlmOpenaiResponsesItems.prefix(itemType(index)) + "_" + (index + 1);
        }

        private void eventResponse(
            String name)
        {
            eventBegin(name);
            generator.writeStartObject().write(MEMBER_TYPE, name).writeStartObject(OBJECT_RESPONSE);
            writeEnvelope();
            generator.write(MEMBER_STATUS, STATUS_IN_PROGRESS)
                .writeStartArray(MEMBER_OUTPUT).writeEnd()
                .writeEnd();
            generator.write(MEMBER_SEQUENCE_NUMBER, sequence++).writeEnd();
            eventEnd(generator.length());
        }

        private void eventItemAdded(
            int index,
            String member)
        {
            eventBegin(EVENT_ITEM_ADDED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_ITEM_ADDED)
                .writeStartObject(MEMBER_ITEM)
                .write(MEMBER_ID, itemId(index))
                .write(MEMBER_TYPE, itemType(index))
                .write(MEMBER_STATUS, STATUS_IN_PROGRESS);

            if (member != null)
            {
                generator.write(member, "");
            }

            writeFooter(index);

            generator.writeEnd()
                .write(MEMBER_OUTPUT_INDEX, index)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventStatus(
            String name,
            int index)
        {
            eventBegin(name);
            generator.writeStartObject().write(MEMBER_TYPE, name)
                .write(MEMBER_ITEM_ID, itemId(index))
                .write(MEMBER_OUTPUT_INDEX, index)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventValueDone(
            String name,
            String member,
            int index,
            int value)
        {
            eventBegin(name);
            generator.writeStartObject().write(MEMBER_TYPE, name).writeKey(member);
            repeatString(value);
            generator.write(MEMBER_ITEM_ID, itemId(index))
                .write(MEMBER_OUTPUT_INDEX, index)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void beginItemDone(
            int index)
        {
            eventBegin(EVENT_ITEM_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_ITEM_DONE)
                .writeStartObject(MEMBER_ITEM)
                .write(MEMBER_ID, itemId(index))
                .write(MEMBER_TYPE, itemType(index))
                .write(MEMBER_STATUS, STATUS_COMPLETED);
        }

        private void endItemDone(
            int index)
        {
            generator.writeEnd()
                .write(MEMBER_OUTPUT_INDEX, index)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventItemAdded()
        {
            eventBegin(EVENT_ITEM_ADDED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_ITEM_ADDED)
                .writeStartObject(MEMBER_ITEM)
                .write(MEMBER_ID, itemId(itemCount - 1))
                .write(MEMBER_TYPE, TYPE_MESSAGE)
                .write(MEMBER_STATUS, STATUS_IN_PROGRESS)
                .writeStartArray(MEMBER_CONTENT).writeEnd()
                .write(MEMBER_ROLE, ROLE_ASSISTANT)
                .writeEnd()
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventPartAdded()
        {
            eventBegin(EVENT_PART_ADDED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_PART_ADDED)
                .write(MEMBER_CONTENT_INDEX, itemParts - 1)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .writeStartObject(MEMBER_PART);

            if (partKind == PART_KIND_REFUSAL)
            {
                generator.write(MEMBER_TYPE, TYPE_REFUSAL).write(MEMBER_REFUSAL, "");
            }
            else
            {
                generator.write(MEMBER_TYPE, TYPE_OUTPUT_TEXT)
                    .writeStartArray(MEMBER_ANNOTATIONS).writeEnd()
                    .writeStartArray(MEMBER_LOGPROBS).writeEnd()
                    .write(MEMBER_TEXT, "");
            }

            generator.writeEnd()
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventTextDone()
        {
            eventBegin(EVENT_TEXT_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_TEXT_DONE)
                .write(MEMBER_CONTENT_INDEX, itemParts - 1)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .writeStartArray(MEMBER_LOGPROBS).writeEnd()
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeKey(MEMBER_TEXT);
            writeCompleted(partValue);
            generator.writeEnd();
            eventEnd(generator.length());
        }

        private void eventPartDone()
        {
            eventBegin(EVENT_PART_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_PART_DONE)
                .write(MEMBER_CONTENT_INDEX, itemParts - 1)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .writeKey(MEMBER_PART);
            writePart(partValue);
            generator.write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void openReasoningItem()
        {
            openReasoningRecord();

            generator.writeStartObject()
                .write(MEMBER_TYPE, TYPE_REASONING)
                .writeStartArray(MEMBER_SUMMARY);
        }

        private void openSummaryPart()
        {
            itemParts++;
            partMode = PART_ACTIVE;

            generator.writeStartObject()
                .write(MEMBER_TYPE, TYPE_SUMMARY_TEXT)
                .writeKey(MEMBER_TEXT)
                .writeStartEscaped();
        }

        private void closeSummaryPart()
        {
            if (partMode == PART_ACTIVE)
            {
                generator.writeEndEscaped().writeEnd();
                partMode = PART_NONE;
            }
        }

        private void onReasoningText(
            JsonStringView chars,
            int from,
            int to)
        {
            if (to > from && !membersStarted)
            {
                if (partMode != PART_ACTIVE)
                {
                    openSummaryPart();
                }

                generator.writeSegment(chars, from, to);
            }
        }

        private void onReasoningId(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            if (length > 0)
            {
                if (reasoningIdBytes.length < reasoningIdLength + length)
                {
                    reasoningIdBytes = Arrays.copyOf(reasoningIdBytes, Math.max(reasoningIdBytes.length << 1,
                        reasoningIdLength + length));
                }

                buffer.getBytes(offset, reasoningIdBytes, reasoningIdLength, length);
                reasoningIdLength += length;
            }

            if (last && reasoningIdLength > 0)
            {
                reasoningId = new String(reasoningIdBytes, 0, reasoningIdLength, UTF_8);
                itemOriginal = reasoningId;
            }
        }

        private void onReasoningMembers(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (length > 0 && server.streaming)
            {
                membersStarted = true;
                memberBuffer.putBytes(memberLength, buffer, offset, length);
                memberLength += length;
            }
            else if (length > 0)
            {
                if (!membersStarted)
                {
                    beginReasoningMembers();
                }

                ensureEncodeRoom(length);
                generator.writeSegment(buffer, offset, length);
            }
        }

        private void beginReasoningMembers()
        {
            closeSummaryPart();
            membersStarted = true;

            generator.writeEnd();
            generator.writeSegment(COMMA, 0, 1);
        }

        private void endReasoning()
        {
            if (membersStarted)
            {
                final int index = itemCount - 1;

                if (itemOriginal == null)
                {
                    generator.write(MEMBER_ID, itemId(index));
                }

                generator.writeEnd();
                membersStarted = false;
                openItem = ITEM_NONE;
            }
            else
            {
                closeSummaryPart();
            }
        }

        private void finishReasoningItem()
        {
            final int index = itemCount - 1;

            closeSummaryPart();

            if (server.streaming)
            {
                sealItem();
                eventItemDone(STATUS_COMPLETED);
                closeItemRecord();
            }
            else
            {
                generator.writeEnd()
                    .write(MEMBER_ID, reasoningItemId(index))
                    .writeEnd();
            }

            openItem = ITEM_NONE;
        }

        private String reasoningItemId(
            int index)
        {
            final String original = itemOriginal(index);

            return original != null ? original : itemId(index);
        }

        private void writeReasoningItem(
            int index)
        {
            generator.writeStartObject().write(MEMBER_TYPE, TYPE_REASONING);
            writeSummary(index);
            generator.write(MEMBER_ID, reasoningItemId(index)).writeEnd();
        }

        private void writeSummary(
            int index)
        {
            final int first = itemFirst(index);
            final int last = first + itemValues(index);

            generator.writeStartArray(MEMBER_SUMMARY);

            for (int part = first; part < last; part++)
            {
                generator.writeStartObject()
                    .write(MEMBER_TYPE, TYPE_SUMMARY_TEXT)
                    .writeKey(MEMBER_TEXT);
                repeatString(part);
                generator.writeEnd();
            }

            generator.writeEnd();
        }

        private void onCitations(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (server.streaming)
            {
                citationBuffer.putBytes(citationLength, buffer, offset, length);
                citationLength += length;
            }
            else
            {
                for (int index = 0; index < length; index++)
                {
                    scanElement(buffer.getByte(offset + index));
                }
            }
        }

        private boolean scanCitations()
        {
            boolean found = false;

            while (!found && citationScan < citationLength)
            {
                final byte value = citationBuffer.getByte(citationScan);

                switch (elementLex)
                {
                case LEX_STRING:
                    elementLex = value == '\\' ? LEX_ESCAPE : value == '"' ? LEX_NONE : LEX_STRING;
                    break;
                case LEX_ESCAPE:
                    elementLex = LEX_STRING;
                    break;
                default:
                    if (value == '"')
                    {
                        elementLex = LEX_STRING;
                    }
                    else if (value == '{' && elementDepth++ == 0)
                    {
                        elementStart = citationScan;
                    }
                    else if (value == '}' && --elementDepth == 0 && elementStart != NONE)
                    {
                        elementEnd = citationScan + 1;
                        found = true;
                    }
                    break;
                }

                if (elementDepth > 0 && elementStart != NONE && citationScan + 1 - elementStart > elementMax)
                {
                    elementStart = NONE;
                }

                citationScan++;
            }

            return found;
        }

        private void compactCitations()
        {
            if (elementStart != NONE)
            {
                citationLength -= elementStart;
                citationBuffer.putBytes(0, citationBuffer, elementStart, citationLength);
                elementStart = 0;
            }
            else
            {
                citationLength = 0;
            }

            citationScan = citationLength;
        }

        private void prepareAnnotation()
        {
            if (annotationCount > 0)
            {
                accumulator.append(COMMA, 0, 1);
            }

            accumulator.append(citationBuffer, elementStart, elementEnd - elementStart);
        }

        private void completeAnnotation()
        {
            annotationCount++;
            elementStart = NONE;
        }

        private void scanElement(
            byte value)
        {
            if (elementDepth > 0)
            {
                appendElement(value);
            }

            switch (elementLex)
            {
            case LEX_STRING:
                elementLex = value == '\\' ? LEX_ESCAPE : value == '"' ? LEX_NONE : LEX_STRING;
                break;
            case LEX_ESCAPE:
                elementLex = LEX_STRING;
                break;
            default:
                scanStructure(value);
                break;
            }
        }

        private void scanStructure(
            byte value)
        {
            if (value == '"')
            {
                elementLex = LEX_STRING;
            }
            else if (value == '{')
            {
                if (elementDepth++ == 0)
                {
                    openElement();
                    appendElement(value);
                }
            }
            else if (value == '}' && --elementDepth == 0)
            {
                if (elementLength > 0)
                {
                    onAnnotation();
                }

                cleanupElement();
            }
        }

        private void openElement()
        {
            if (elementSlot == NO_SLOT)
            {
                elementSlot = holdPool.acquire(replyId);
            }

            if (elementSlot != NO_SLOT)
            {
                elementBuffer = holdPool.buffer(elementSlot);
                elementLength = 0;
            }
            else
            {
                elementLength = NONE;
            }
        }

        private void appendElement(
            byte value)
        {
            if (elementLength >= 0 && elementLength < elementMax)
            {
                elementBuffer.putByte(elementLength++, value);
            }
            else
            {
                elementLength = NONE;
            }
        }

        private void cleanupElement()
        {
            if (elementSlot != NO_SLOT)
            {
                holdPool.release(elementSlot);
                elementSlot = NO_SLOT;
                elementBuffer = null;
            }

            elementLength = 0;
        }

        private void onAnnotation()
        {
            ensureEncodeRoom(elementLength + 1);

            if (annotationCount == 0)
            {
                generator.writeKey(MEMBER_ANNOTATIONS);
                generator.writeSegment(ARRAY_OPEN, 0, 1, Completion.INCOMPLETE);
            }
            else
            {
                generator.writeSegment(COMMA, 0, 1, Completion.INCOMPLETE);
            }

            generator.writeSegment(elementBuffer, 0, elementLength, Completion.INCOMPLETE);
            annotationCount++;
        }

        private LlmSectionPipeline reasoningIdPipeline()
        {
            if (reasoningIdPipeline == null)
            {
                reasoningIdPipeline = new LlmSectionPipeline(
                    new LlmOpenaiResponsesReasoningToValue(NAME, MEMBER_ID), transformBuffer);
            }

            return reasoningIdPipeline;
        }

        private LlmSectionPipeline reasoningMembersPipeline()
        {
            if (reasoningMembersPipeline == null)
            {
                reasoningMembersPipeline = new LlmSectionPipeline(
                    new LlmOpenaiResponsesReasoningFromCanonical(NAME), transformBuffer);
            }

            return reasoningMembersPipeline;
        }

        private LlmSectionPipeline citationsPipeline()
        {
            if (citationsPipeline == null)
            {
                citationsPipeline = new LlmSectionPipeline(
                    LlmOpenaiResponsesCitationsFromCanonical.array(NAME), transformBuffer);
            }

            return citationsPipeline;
        }

        private void eventReasoningAdded(
            int index)
        {
            eventBegin(EVENT_ITEM_ADDED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_ITEM_ADDED)
                .writeStartObject(MEMBER_ITEM)
                .write(MEMBER_ID, itemId(index))
                .write(MEMBER_TYPE, TYPE_REASONING)
                .writeStartArray(MEMBER_SUMMARY).writeEnd()
                .writeEnd()
                .write(MEMBER_OUTPUT_INDEX, index)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventSummaryPartAdded()
        {
            eventBegin(EVENT_SUMMARY_PART_ADDED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_SUMMARY_PART_ADDED)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .writeStartObject(MEMBER_PART)
                .write(MEMBER_TYPE, TYPE_SUMMARY_TEXT)
                .write(MEMBER_TEXT, "")
                .writeEnd()
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .write(MEMBER_SUMMARY_INDEX, itemParts - 1)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventSummaryTextDone()
        {
            eventBegin(EVENT_SUMMARY_TEXT_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_SUMMARY_TEXT_DONE)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .write(MEMBER_SUMMARY_INDEX, itemParts - 1)
                .writeKey(MEMBER_TEXT);
            writeCompleted(partValue);
            generator.writeEnd();
            eventEnd(generator.length());
        }

        private void eventSummaryPartDone()
        {
            eventBegin(EVENT_SUMMARY_PART_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_SUMMARY_PART_DONE)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .writeStartObject(MEMBER_PART)
                .write(MEMBER_TYPE, TYPE_SUMMARY_TEXT)
                .writeKey(MEMBER_TEXT);
            writeCompleted(partValue);
            generator.writeEnd()
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .write(MEMBER_SUMMARY_INDEX, itemParts - 1)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventRefusalDone()
        {
            eventBegin(EVENT_REFUSAL_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_REFUSAL_DONE)
                .write(MEMBER_CONTENT_INDEX, itemParts - 1)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .writeKey(MEMBER_REFUSAL);
            writeCompleted(partValue);
            generator.write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventAnnotationAdded(
            int annotationIndex)
        {
            eventBegin(EVENT_ANNOTATION_ADDED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_ANNOTATION_ADDED)
                .writeKey(MEMBER_ANNOTATION);
            generator.writeSegment(citationBuffer, elementStart, elementEnd - elementStart, Completion.COMPLETE);
            generator.write(MEMBER_ANNOTATION_INDEX, annotationIndex)
                .write(MEMBER_CONTENT_INDEX, itemParts - 1)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventItemDone(
            String status)
        {
            eventBegin(EVENT_ITEM_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_ITEM_DONE)
                .writeKey(MEMBER_ITEM);
            writeItem(itemCount - 1, status);
            generator.write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventError(
            String reported,
            String text,
            String param)
        {
            eventBegin(EVENT_ERROR);
            generator.writeStartObject()
                .write(MEMBER_TYPE, EVENT_ERROR)
                .write(MEMBER_CODE, reported)
                .write(MEMBER_MESSAGE, text);

            if (param != null)
            {
                generator.write(MEMBER_PARAM, param);
            }
            else
            {
                generator.writeNull(MEMBER_PARAM);
            }

            generator.write(MEMBER_SEQUENCE_NUMBER, sequence++).writeEnd();
            eventEnd(generator.length());
        }

        private void eventFailed(
            String reported,
            String text)
        {
            eventBegin(EVENT_FAILED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_FAILED).writeStartObject(OBJECT_RESPONSE);
            writeEnvelope();
            generator.writeStartArray(MEMBER_OUTPUT).writeEnd()
                .write(MEMBER_STATUS, STATUS_FAILED)
                .writeStartObject(MEMBER_ERROR).write(MEMBER_CODE, reported).write(MEMBER_MESSAGE, text).writeEnd()
                .writeEnd();
            generator.write(MEMBER_SEQUENCE_NUMBER, sequence++).writeEnd();
            eventEnd(generator.length());
        }

        private int encodeLength()
        {
            return generator.length();
        }

        private void eventBegin(
            String type)
        {
            this.recordName = type;
        }

        private void eventEnd(
            int position)
        {
            recordTotal = position;
            generator.reset();
        }

        private void emitReference(
            int sourceOffset,
            int length)
        {
            recordReference(sourceOffset, length, REF_ACCUMULATOR);
        }

        private void recordReference(
            int sourceOffset,
            int length,
            int source)
        {
            if (recordRefs == null)
            {
                recordRefs = new int[RECORD_REFS * REF_STRIDE];
            }
            else if (recordRefCount * REF_STRIDE == recordRefs.length)
            {
                recordRefs = Arrays.copyOf(recordRefs, recordRefs.length << 1);
            }

            final int at = recordRefCount++ * REF_STRIDE;

            recordRefs[at] = generator.length();
            recordRefs[at + 1] = sourceOffset;
            recordRefs[at + 2] = length;
            recordRefs[at + 3] = source;
        }

        private void putUsage(
            int inputTokens,
            int cacheReadTokens,
            int cacheWriteTokens,
            int outputTokens,
            int reasoningTokens,
            int totalTokens)
        {
            final int total = totalTokens != NONE || inputTokens == NONE || outputTokens == NONE
                ? totalTokens
                : inputTokens + outputTokens;

            if (inputTokens != NONE || outputTokens != NONE || total != NONE)
            {
                generator.writeStartObject(MEMBER_USAGE);

                if (inputTokens != NONE)
                {
                    generator.writeKey("input_tokens").write(inputTokens);
                }

                if (cacheWriteTokens != NONE || cacheReadTokens != NONE)
                {
                    generator.writeStartObject("input_tokens_details");

                    if (cacheWriteTokens != NONE)
                    {
                        generator.writeKey("cache_write_tokens").write(cacheWriteTokens);
                    }

                    if (cacheReadTokens != NONE)
                    {
                        generator.writeKey("cached_tokens").write(cacheReadTokens);
                    }

                    generator.writeEnd();
                }

                if (outputTokens != NONE)
                {
                    generator.writeKey("output_tokens").write(outputTokens);
                }

                if (reasoningTokens != NONE)
                {
                    generator.writeStartObject("output_tokens_details")
                        .writeKey("reasoning_tokens").write(reasoningTokens)
                        .writeEnd();
                }

                if (total != NONE)
                {
                    generator.writeKey("total_tokens").write(total);
                }

                generator.writeEnd();
            }
        }

        private void cleanupAccumulator()
        {
            cleanupOutbox();

            if (accumulator != null)
            {
                accumulator.release();
            }

            tail.release();
            cleanupElement();
        }
    }



    private final class LlmOpenaiRejectHandler
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

        private LlmOpenaiRejectHandler(
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
