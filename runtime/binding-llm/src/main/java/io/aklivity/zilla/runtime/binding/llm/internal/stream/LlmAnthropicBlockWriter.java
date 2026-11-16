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

import jakarta.json.JsonException;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

final class LlmAnthropicBlockWriter
{
    static final int MORE = 0;
    static final int VALUE = 1;
    static final int REJECT = 2;
    static final int SKIP = 3;
    static final int DESCEND = 4;

    static final int ACTION_DROP = 0;
    static final int ACTION_COPY = 1;
    static final int ACTION_STRING = 2;
    static final int ACTION_OBJECT = 3;
    static final int ACTION_OPTIONAL = 4;
    static final int ACTION_HOLD = 5;
    static final int ACTION_BREAKPOINT = 6;
    static final int ACTION_REJECT = 7;
    static final int ACTION_SOURCE = 8;
    static final int ACTION_CITATIONS = 9;
    static final int ACTION_DATA = 10;
    static final int ACTION_RANGE = 11;
    static final int ACTION_FORMAT = 12;
    static final int ACTION_FORMAT_TYPE = 13;
    static final int ACTION_TYPE = 14;

    static final int KIND_TOOL_CALL = 0;
    static final int KIND_TOOL_DEFINITION = 1;
    static final int KIND_REASONING = 2;
    static final int KIND_MEDIA = 3;
    static final int KIND_CITATIONS = 4;
    static final int KIND_CONTROL = 5;

    static final int CONTAINER_OUTPUT_CONFIG = 0;
    static final int CONTAINER_THINKING = 1;
    static final int CONTAINER_METADATA = 2;

    static final int RESIDUE_NONE = 0;
    static final int RESIDUE_OPEN = 1;
    static final int RESIDUE_LIFTED = 2;

    private static final int CITATION_NONE = 0;
    private static final int CITATION_URL = 1;
    private static final int CITATION_OTHER = 2;
    private static final int NEST_NONE = 0;
    private static final int NEST_OPEN = 1;
    private static final int NEST_CLOSED = 2;
    private static final int THINKING_UNSET = 0;
    private static final int THINKING_ENABLED = 1;
    private static final int THINKING_BUDGET = 2;
    private static final int CAPTURE_MAX = 2048;

    private static final String PREFIX = LlmAnthropicDialectFactorySpi.NAME + ":";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_ID = "id";
    private static final String MEMBER_NAME = "name";
    private static final String MEMBER_INPUT = "input";
    private static final String MEMBER_ARGUMENTS = "arguments";
    private static final String MEMBER_DESCRIPTION = "description";
    private static final String MEMBER_INPUT_SCHEMA = "input_schema";
    private static final String MEMBER_PARAMETERS = "parameters";
    private static final String MEMBER_STRICT = "strict";
    private static final String MEMBER_FUNCTION = "function";
    private static final String MEMBER_DATA_NAME = "data";
    private static final String SHARED_CITATIONS = LlmCitationsEnabled.SHARED_MEMBER;
    private static final String TYPE_CUSTOM = "custom";
    private static final String TYPE_FUNCTION = "function";
    private static final String MEMBER_THINKING = "thinking";
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_SIGNATURE = "signature";
    private static final String MEMBER_REDACTED = "data";
    private static final String SHARED_SIGNATURE = "x:signature";
    private static final String SHARED_REDACTED = "x:redacted_data";
    private static final String MEMBER_SOURCE = "source";
    private static final String MEMBER_MEDIA_TYPE = "media_type";
    private static final String MEMBER_URL = "url";
    private static final String MEMBER_FILE_ID = "file_id";
    private static final String MEMBER_TITLE = "title";
    private static final String MEMBER_ENABLED = "enabled";
    private static final String TYPE_URL_LOCATION = "web_search_result_location";
    private static final String TYPE_URL_CITATION = "url_citation";
    private static final String MEMBER_OTHER_TYPE = PREFIX + MEMBER_TYPE;
    private static final String DATA_URI_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";
    private static final String MEMBER_STREAM = "stream";
    private static final String MEMBER_DISABLE_PARALLEL = "disable_parallel_tool_use";
    private static final String MEMBER_NATIVE_TOOL_CHOICE = PREFIX + "tool_choice";
    private static final String MEMBER_SHARED_TOOL_CHOICE = "x:tool_choice";
    private static final String MEMBER_SHARED_PARALLEL = "x:parallel_tool_calls";
    private static final String MEMBER_SHARED_SERVICE_TIER = "x:service_tier";
    private static final String MEMBER_NATIVE_SERVICE_TIER = PREFIX + "service_tier";
    private static final String MEMBER_NATIVE_OUTPUT_CONFIG = PREFIX + "output_config";
    private static final String MEMBER_NATIVE_THINKING = PREFIX + "thinking";
    private static final String MEMBER_NATIVE_METADATA = PREFIX + "metadata";
    private static final String MEMBER_SHARED_RESPONSE_FORMAT = "x:response_format";
    private static final String MEMBER_SHARED_REASONING_EFFORT = "x:reasoning_effort";
    private static final String MEMBER_SHARED_THINKING_BUDGET = "x:thinking_budget_tokens";
    private static final String MEMBER_SHARED_USER = "x:user";
    private static final String MEMBER_FORMAT = "format";
    private static final String MEMBER_SCHEMA = "schema";
    private static final String MEMBER_JSON_SCHEMA = "json_schema";
    private static final String MEMBER_EFFORT = "effort";
    private static final String MEMBER_BUDGET_TOKENS = "budget_tokens";
    private static final String MEMBER_USER_ID = "user_id";
    private static final String TYPE_JSON_SCHEMA = "json_schema";
    private static final String TYPE_DISABLED = "disabled";
    private static final String TYPE_ENABLED = "enabled";
    private static final String TYPE_AUTO = "auto";
    private static final String TYPE_ANY = "any";
    private static final String TYPE_NONE = "none";
    private static final String TYPE_TOOL = "tool";
    private static final String EFFORT_NONE = "none";
    private static final String DEFAULT_FORMAT_NAME = "response";
    private static final int HEADROOM = 16;
    private static final int FORMAT_ROOM =
        "{\"type\":\"json_schema\",\"json_schema\":{\"name\":\"response\"".length() + HEADROOM;

    private final LlmOutputSink sink;
    private final JsonGeneratorEx generator;
    private final StringBuilder key = new StringBuilder();
    private final StringBuilder ttl = new StringBuilder();
    private final StringBuilder capture = new StringBuilder();
    private final LlmCapturedValue captured = new LlmCapturedValue();
    private final LlmToolChoice choice = new LlmToolChoice();

    private MutableDirectBufferEx buffer;
    private int origin;
    private int kind;
    private int depth;
    private boolean argumented;
    private boolean named;
    private String heldType;
    private String pendingKey;
    private int breakpointDepth;
    private boolean breakpointKeyType;
    private boolean breakpointKeyTtl;
    private boolean breakpointEphemeral;
    private String container;
    private String dataMember;
    private String urlMember;
    private String mediaType;
    private String pendingPrefix;
    private int captureDepth;
    private boolean captureKeyEnabled;
    private boolean captureEnabled;
    private int citation;
    private int nest;
    private int controlContainer;
    private int thinking;
    private int typeResidue;
    private boolean separating;
    private byte separator;

    LlmAnthropicBlockWriter(
        LlmOutputSink sink,
        JsonGeneratorEx generator)
    {
        this.sink = sink;
        this.generator = generator;
    }

    void resume(
        MutableDirectBufferEx buffer,
        int origin)
    {
        this.buffer = buffer;
        this.origin = origin;

        generator.wrap(buffer, origin, buffer.capacity());
    }

    void beginToolCall(
        MutableDirectBufferEx buffer,
        int origin)
    {
        begin(buffer, origin, KIND_TOOL_CALL);

        generator.writeStartObject();
    }

    void beginToolDefinition(
        MutableDirectBufferEx buffer,
        int origin)
    {
        begin(buffer, origin, KIND_TOOL_DEFINITION);

        generator.writeStartObject().write(MEMBER_TYPE, TYPE_FUNCTION).writeStartObject(MEMBER_FUNCTION);
    }

    void beginReasoning(
        MutableDirectBufferEx buffer,
        int origin)
    {
        begin(buffer, origin, KIND_REASONING);

        generator.writeStartObject();
    }

    void beginMedia(
        MutableDirectBufferEx buffer,
        int origin,
        boolean image,
        String mediaType)
    {
        begin(buffer, origin, KIND_MEDIA);

        this.container = image ? "image_url" : "file";
        this.dataMember = image ? MEMBER_URL : "file_data";
        this.urlMember = image ? MEMBER_URL : "file_url";
        this.mediaType = mediaType;

        generator.writeStartObject().write(MEMBER_TYPE, container);
    }

    void beginCitations(
        MutableDirectBufferEx buffer,
        int origin)
    {
        begin(buffer, origin, KIND_CITATIONS);

        generator.writeStartArray();
    }

    void beginElement()
    {
        generator.writeStartObject();

        heldType = null;
        citation = CITATION_NONE;
        nest = NEST_NONE;
    }

    boolean elements()
    {
        return kind == KIND_CITATIONS;
    }

    void endElement()
    {
        if (heldType != null)
        {
            resolveCitation();
        }

        closeNested();
        generator.writeEnd();
    }

    int action(
        CharSequence name)
    {
        return switch (kind)
        {
        case KIND_TOOL_CALL -> toolCallAction(name);
        case KIND_TOOL_DEFINITION -> toolDefinitionAction(name);
        case KIND_REASONING -> reasoningAction(name);
        case KIND_MEDIA -> mediaAction(name);
        default -> citationAction(name);
        };
    }

    int sourceAction(
        CharSequence name)
    {
        int action = ACTION_DROP;

        if (MEMBER_DATA_NAME.contentEquals(name))
        {
            if (mediaType != null)
            {
                generator.writeKey(dataMember);
                pendingPrefix = DATA_URI_PREFIX + mediaType + BASE64_MARKER;
                action = ACTION_DATA;
            }
            else
            {
                action = ACTION_REJECT;
            }
        }
        else if (MEMBER_URL.contentEquals(name))
        {
            generator.writeKey(urlMember);
            action = ACTION_STRING;
        }
        else if (MEMBER_FILE_ID.contentEquals(name))
        {
            generator.writeKey(MEMBER_FILE_ID);
            action = ACTION_STRING;
        }
        else if (!MEMBER_TYPE.contentEquals(name) && !MEMBER_MEDIA_TYPE.contentEquals(name))
        {
            writePrefixed(name);
            action = ACTION_COPY;
        }

        return action;
    }

    void closeSource()
    {
        generator.writeEnd();
    }

    int dataValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = REJECT;

        if (event == JsonEvent.VALUE_STRING)
        {
            if (pendingPrefix != null)
            {
                generator.write(pendingPrefix, Completion.INCOMPLETE);
                pendingPrefix = null;
            }

            outcome = copyValue(event, parser);
        }

        return outcome;
    }

    int citationsValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        switch (event)
        {
        case START_OBJECT, START_ARRAY:
            captureDepth++;
            break;
        case END_OBJECT, END_ARRAY:
            captureDepth--;
            break;
        case KEY_NAME:
            captureKeyEnabled = captureDepth == 1 && MEMBER_ENABLED.contentEquals(parser.getStringView());
            break;
        case VALUE_TRUE:
            captureEnabled |= captureDepth == 1 && captureKeyEnabled;
            break;
        default:
            break;
        }

        int outcome = MORE;

        if (captureDepth == 0)
        {
            if (captureEnabled)
            {
                generator.writeKey(SHARED_CITATIONS).writeStartObject().write(MEMBER_ENABLED, true).writeEnd();
            }

            outcome = VALUE;
        }

        return outcome;
    }

    boolean reserve(
        JsonEvent event)
    {
        int need = HEADROOM;

        if (event == JsonEvent.KEY_NAME || event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER)
        {
            need += PREFIX.length();
        }

        if (generator.remaining() < need)
        {
            flush(false);
        }

        return generator.remaining() >= need;
    }

    int pending()
    {
        return generator.length();
    }

    boolean finish()
    {
        boolean accepted = true;

        if (kind == KIND_TOOL_CALL)
        {
            if (!argumented)
            {
                generator.writeStartObject(MEMBER_ARGUMENTS).writeEnd();
            }
        }
        else if (kind == KIND_TOOL_DEFINITION)
        {
            accepted = named && (heldType == null || TYPE_CUSTOM.equals(heldType));

            if (accepted)
            {
                generator.writeEnd();
            }
        }

        if (accepted)
        {
            generator.writeEnd();
            flush(true);
        }

        return accepted;
    }

    int drop(
        JsonEvent event,
        JsonParserEx parser)
    {
        depth += step(event);

        return depth == 0 && !deferred(event, parser) ? VALUE : MORE;
    }

    int copyValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        copy(event, parser);

        return depth == 0 && !deferred(event, parser) ? VALUE : MORE;
    }

    int stringValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        return event == JsonEvent.VALUE_STRING ? copyValue(event, parser) : REJECT;
    }

    int objectValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        return depth > 0 || event == JsonEvent.START_OBJECT ? copyValue(event, parser) : REJECT;
    }

    int optionalValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome;

        if (pendingKey == null)
        {
            outcome = copyValue(event, parser);
        }
        else if (event == JsonEvent.VALUE_STRING)
        {
            generator.writeKey(pendingKey);
            pendingKey = null;
            outcome = copyValue(event, parser);
        }
        else
        {
            pendingKey = null;
            outcome = drop(event, parser);
            outcome = outcome == MORE ? SKIP : outcome;
        }

        return outcome;
    }

    int holdValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = REJECT;

        if (event == JsonEvent.VALUE_STRING && !parser.deferredBytes())
        {
            heldType = parser.getStringView().toString();
            outcome = VALUE;
        }

        return outcome;
    }

    int breakpointValue(
        JsonEvent event,
        JsonParserEx parser)
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
                final CharSequence name = parser.getStringView();

                breakpointKeyType = LlmCacheBreakpoint.NATIVE_TYPE.contentEquals(name);
                breakpointKeyTtl = LlmCacheBreakpoint.TTL.contentEquals(name);
            }
            break;
        case VALUE_STRING:
            if (!parser.deferredBytes() && breakpointDepth == 1 && breakpointKeyType)
            {
                breakpointEphemeral = LlmCacheBreakpoint.NATIVE_EPHEMERAL.contentEquals(parser.getStringView());
            }
            else if (!parser.deferredBytes() && breakpointDepth == 1 && breakpointKeyTtl)
            {
                ttl.append(parser.getStringView());
            }
            break;
        default:
            break;
        }

        int outcome = MORE;

        if (breakpointDepth == 0)
        {
            if (breakpointEphemeral)
            {
                generator.writeKey(LlmCacheBreakpoint.MEMBER).writeStartObject()
                    .write(LlmCacheBreakpoint.MODE, LlmCacheBreakpoint.MODE_EXPLICIT);

                if (ttl.length() > 0)
                {
                    generator.write(LlmCacheBreakpoint.TTL, ttl.toString());
                }

                generator.writeEnd();
            }

            outcome = VALUE;
        }

        return outcome;
    }

    void beginMember(
        MutableDirectBufferEx buffer,
        int origin,
        char separator)
    {
        begin(buffer, origin, KIND_CONTROL);

        this.separator = (byte) separator;
        this.separating = true;

        generator.writeStartObject();
    }

    void memberKey(
        MutableDirectBufferEx buffer,
        int origin,
        char separator,
        CharSequence name)
    {
        beginMember(buffer, origin, separator);
        generator.writeKey(name);
        flush(false);
    }

    void endMember()
    {
        flush(false);
    }

    void container(
        int container)
    {
        this.controlContainer = container;
        this.thinking = THINKING_UNSET;
    }

    int streamValue(
        JsonEvent event)
    {
        final boolean flag = flag(event);

        if (flag)
        {
            generator.writeKey(MEMBER_STREAM);
            writeFlag(event);
        }

        return flag ? VALUE : REJECT;
    }

    int tierValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        final String tier = event == JsonEvent.VALUE_STRING && !parser.deferredBytes()
            ? LlmServiceTiers.canonicalAnthropic(parser.getStringView().toString())
            : null;
        int outcome;

        if (tier != null)
        {
            generator.writeKey(MEMBER_SHARED_SERVICE_TIER);
            generator.write(tier);
            outcome = VALUE;
        }
        else
        {
            generator.writeKey(MEMBER_NATIVE_SERVICE_TIER);
            outcome = copyValue(event, parser);
        }

        return outcome;
    }

    int toolChoiceValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = captureValue(event, parser);

        if (outcome == VALUE)
        {
            if (captured.isObject(0))
            {
                splitToolChoice();
            }
            else
            {
                generator.writeKey(MEMBER_NATIVE_TOOL_CHOICE);
                replay(0, captured.count());
            }

            captured.clear();
        }

        return outcome;
    }

    int containerValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = DESCEND;

        if (event != JsonEvent.START_OBJECT)
        {
            generator.writeKey(residueKey());
            outcome = copyValue(event, parser);
        }

        return outcome;
    }

    int containerAction(
        CharSequence name,
        int residue)
    {
        int action = ACTION_COPY;

        if (controlContainer == CONTAINER_OUTPUT_CONFIG && MEMBER_FORMAT.contentEquals(name))
        {
            lift(residue, MEMBER_SHARED_RESPONSE_FORMAT);
            action = ACTION_FORMAT;
        }
        else if (controlContainer == CONTAINER_OUTPUT_CONFIG && MEMBER_EFFORT.contentEquals(name))
        {
            lift(residue, null);
            pendingKey = MEMBER_SHARED_REASONING_EFFORT;
            action = ACTION_OPTIONAL;
        }
        else if (controlContainer == CONTAINER_METADATA && MEMBER_USER_ID.contentEquals(name))
        {
            lift(residue, null);
            pendingKey = MEMBER_SHARED_USER;
            action = ACTION_OPTIONAL;
        }
        else if (controlContainer == CONTAINER_THINKING && MEMBER_TYPE.contentEquals(name))
        {
            action = ACTION_TYPE;
        }
        else if (controlContainer == CONTAINER_THINKING && MEMBER_BUDGET_TOKENS.contentEquals(name))
        {
            lift(residue, null);
            thinking = THINKING_BUDGET;
            action = ACTION_RANGE;
        }
        else
        {
            openResidue(residue);
            generator.writeKey(name);
        }

        return action;
    }

    int typeValue(
        JsonEvent event,
        JsonParserEx parser,
        int residue)
    {
        int outcome;

        typeResidue = residue;

        if (event == JsonEvent.VALUE_STRING && !parser.deferredBytes() &&
            TYPE_DISABLED.contentEquals(parser.getStringView()))
        {
            lift(residue, null);
            generator.write(MEMBER_SHARED_REASONING_EFFORT, EFFORT_NONE);
            typeResidue = RESIDUE_LIFTED;
            outcome = VALUE;
        }
        else if (event == JsonEvent.VALUE_STRING && !parser.deferredBytes() &&
            TYPE_ENABLED.contentEquals(parser.getStringView()))
        {
            thinking = thinking == THINKING_BUDGET ? THINKING_BUDGET : THINKING_ENABLED;
            outcome = VALUE;
        }
        else
        {
            openResidue(residue);
            generator.writeKey(MEMBER_TYPE);
            typeResidue = RESIDUE_OPEN;
            outcome = copyValue(event, parser);
        }

        return outcome;
    }

    int typeResidue()
    {
        return typeResidue;
    }

    int rangeValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = REJECT;

        if (event == JsonEvent.VALUE_NUMBER && !parser.deferredBytes())
        {
            final CharSequence number = parser.getStringView();
            final double value = Double.parseDouble(number.toString());

            if (value >= 0 && value <= Integer.MAX_VALUE && value == Math.rint(value))
            {
                generator.writeKey(MEMBER_SHARED_THINKING_BUDGET);
                generator.writeNumber(number, Completion.COMPLETE);
            }

            outcome = VALUE;
        }
        else if (event == JsonEvent.VALUE_NULL)
        {
            outcome = VALUE;
        }

        return outcome;
    }

    int formatValue(
        JsonEvent event)
    {
        int outcome = REJECT;

        if (event == JsonEvent.START_OBJECT)
        {
            ensure(FORMAT_ROOM);

            generator.writeStartObject()
                .write(MEMBER_TYPE, TYPE_JSON_SCHEMA)
                .writeStartObject(MEMBER_JSON_SCHEMA)
                .write(MEMBER_NAME, DEFAULT_FORMAT_NAME);
            outcome = DESCEND;
        }

        return outcome;
    }

    int formatAction(
        CharSequence name)
    {
        int action = ACTION_DROP;

        if (MEMBER_SCHEMA.contentEquals(name))
        {
            generator.writeKey(MEMBER_SCHEMA);
            action = ACTION_COPY;
        }
        else if (MEMBER_TYPE.contentEquals(name))
        {
            action = ACTION_FORMAT_TYPE;
        }

        return action;
    }

    int formatType(
        JsonEvent event,
        JsonParserEx parser)
    {
        final boolean accepted = event == JsonEvent.VALUE_STRING && !parser.deferredBytes() &&
            TYPE_JSON_SCHEMA.contentEquals(parser.getStringView());

        return accepted ? VALUE : REJECT;
    }

    void closeFormat()
    {
        generator.writeEnd().writeEnd();
    }

    void closeContainer(
        int residue)
    {
        if (thinking == THINKING_ENABLED)
        {
            openResidue(residue);
            generator.write(MEMBER_TYPE, TYPE_ENABLED).writeEnd();
        }
        else if (residue == RESIDUE_OPEN)
        {
            generator.writeEnd();
        }
        else if (residue == RESIDUE_NONE)
        {
            generator.writeStartObject(residueKey()).writeEnd();
        }
    }

    void flush(
        boolean last)
    {
        final int length = generator.length();

        if (length > 0 || last)
        {
            if (separating && length > 0)
            {
                buffer.putByte(origin, separator);
                separating = false;
            }

            sink.put(buffer, origin, length, last);
            generator.wrap(buffer, origin, buffer.capacity());
        }
    }

    private void begin(
        MutableDirectBufferEx buffer,
        int origin,
        int kind)
    {
        this.buffer = buffer;
        this.origin = origin;
        this.kind = kind;
        this.depth = 0;
        this.argumented = false;
        this.named = false;
        this.heldType = null;
        this.pendingKey = null;
        this.pendingPrefix = null;
        this.mediaType = null;
        this.separating = false;
        this.thinking = THINKING_UNSET;
        this.capture.setLength(0);
        this.captured.clear();

        generator.reset();
        generator.wrap(buffer, origin, buffer.capacity());
    }

    private int reasoningAction(
        CharSequence name)
    {
        int action = ACTION_COPY;

        if (MEMBER_TYPE.contentEquals(name))
        {
            action = ACTION_DROP;
        }
        else if (MEMBER_THINKING.contentEquals(name))
        {
            generator.writeKey(MEMBER_TEXT);
            action = ACTION_STRING;
        }
        else if (MEMBER_SIGNATURE.contentEquals(name))
        {
            generator.writeKey(SHARED_SIGNATURE);
        }
        else if (MEMBER_REDACTED.contentEquals(name))
        {
            generator.writeKey(SHARED_REDACTED);
        }
        else
        {
            writePrefixed(name);
        }

        return action;
    }

    private int mediaAction(
        CharSequence name)
    {
        int action = ACTION_DROP;

        if (MEMBER_SOURCE.contentEquals(name))
        {
            generator.writeKey(container).writeStartObject();
            action = ACTION_SOURCE;
        }
        else if (LlmCacheBreakpoint.NATIVE_MEMBER.contentEquals(name))
        {
            action = beginBreakpoint();
        }
        else if (LlmCitationsEnabled.MEMBER.contentEquals(name))
        {
            captureDepth = 0;
            captureKeyEnabled = false;
            captureEnabled = false;
            action = ACTION_CITATIONS;
        }
        else if (!MEMBER_TYPE.contentEquals(name))
        {
            writePrefixed(name);
            action = ACTION_COPY;
        }

        return action;
    }

    private int citationAction(
        CharSequence name)
    {
        int action;

        if (MEMBER_TYPE.contentEquals(name))
        {
            action = ACTION_HOLD;
        }
        else if (citation == CITATION_NONE && heldType == null)
        {
            action = ACTION_REJECT;
        }
        else
        {
            resolveCitation();

            final boolean title = MEMBER_TITLE.contentEquals(name);

            if (citation == CITATION_URL && (title || MEMBER_URL.contentEquals(name)))
            {
                if (nest == NEST_CLOSED)
                {
                    action = ACTION_REJECT;
                }
                else
                {
                    openNested();

                    if (title)
                    {
                        pendingKey = MEMBER_TITLE;
                        action = ACTION_OPTIONAL;
                    }
                    else
                    {
                        generator.writeKey(MEMBER_URL);
                        action = ACTION_STRING;
                    }
                }
            }
            else
            {
                closeNested();
                writePrefixed(name);
                action = ACTION_COPY;
            }
        }

        return action;
    }

    private void resolveCitation()
    {
        if (citation == CITATION_NONE)
        {
            final boolean url = TYPE_URL_LOCATION.equals(heldType);

            generator.writeKey(url ? MEMBER_TYPE : MEMBER_OTHER_TYPE).write(url ? TYPE_URL_CITATION : heldType);
            citation = url ? CITATION_URL : CITATION_OTHER;
        }
    }

    private void openNested()
    {
        if (nest == NEST_NONE)
        {
            generator.writeKey(TYPE_URL_CITATION).writeStartObject();
            nest = NEST_OPEN;
        }
    }

    private void closeNested()
    {
        if (nest == NEST_OPEN)
        {
            generator.writeEnd();
            nest = NEST_CLOSED;
        }
    }

    private int toolCallAction(
        CharSequence name)
    {
        int action = ACTION_DROP;

        if (MEMBER_INPUT.contentEquals(name))
        {
            generator.writeKey(MEMBER_ARGUMENTS);
            argumented = true;
            action = ACTION_OBJECT;
        }
        else if (LlmCacheBreakpoint.NATIVE_MEMBER.contentEquals(name))
        {
            action = beginBreakpoint();
        }
        else if (!MEMBER_TYPE.contentEquals(name) && !MEMBER_ID.contentEquals(name) && !MEMBER_NAME.contentEquals(name))
        {
            writePrefixed(name);
            action = ACTION_COPY;
        }

        return action;
    }

    private int toolDefinitionAction(
        CharSequence name)
    {
        int action;

        if (MEMBER_NAME.contentEquals(name))
        {
            generator.writeKey(MEMBER_NAME);
            named = true;
            action = ACTION_STRING;
        }
        else if (MEMBER_DESCRIPTION.contentEquals(name))
        {
            pendingKey = MEMBER_DESCRIPTION;
            action = ACTION_OPTIONAL;
        }
        else if (MEMBER_INPUT_SCHEMA.contentEquals(name))
        {
            generator.writeKey(MEMBER_PARAMETERS);
            action = ACTION_OBJECT;
        }
        else if (MEMBER_TYPE.contentEquals(name))
        {
            action = ACTION_HOLD;
        }
        else if (MEMBER_STRICT.contentEquals(name))
        {
            generator.writeKey(MEMBER_STRICT);
            action = ACTION_COPY;
        }
        else if (LlmCacheBreakpoint.NATIVE_MEMBER.contentEquals(name))
        {
            action = beginBreakpoint();
        }
        else
        {
            writePrefixed(name);
            action = ACTION_COPY;
        }

        return action;
    }

    private int beginBreakpoint()
    {
        breakpointDepth = 0;
        breakpointKeyType = false;
        breakpointKeyTtl = false;
        breakpointEphemeral = false;
        ttl.setLength(0);

        return ACTION_BREAKPOINT;
    }

    private void writePrefixed(
        CharSequence name)
    {
        key.setLength(0);
        key.append(PREFIX).append(name);
        generator.writeKey(key);
    }

    private void lift(
        int residue,
        String key)
    {
        if (residue == RESIDUE_OPEN)
        {
            generator.writeEnd();
        }

        if (key != null)
        {
            generator.writeKey(key);
        }
    }

    private void openResidue(
        int residue)
    {
        if (residue != RESIDUE_OPEN)
        {
            generator.writeStartObject(residueKey());
        }
    }

    private String residueKey()
    {
        return switch (controlContainer)
        {
        case CONTAINER_THINKING -> MEMBER_NATIVE_THINKING;
        case CONTAINER_METADATA -> MEMBER_NATIVE_METADATA;
        default -> MEMBER_NATIVE_OUTPUT_CONFIG;
        };
    }

    private void splitToolChoice()
    {
        final String type = captured.string(captured.member(0, MEMBER_TYPE));
        final String function = captured.string(captured.member(0, MEMBER_NAME));
        final JsonEvent disabled = captured.event(captured.member(0, MEMBER_DISABLE_PARALLEL));

        choice.clear();

        if (TYPE_AUTO.equals(type))
        {
            choice.mode(LlmToolChoice.MODE_AUTO);
        }
        else if (TYPE_ANY.equals(type))
        {
            choice.mode(LlmToolChoice.MODE_REQUIRED);
        }
        else if (TYPE_NONE.equals(type))
        {
            choice.mode(LlmToolChoice.MODE_NONE);
        }
        else if (TYPE_TOOL.equals(type) && function != null)
        {
            choice.function(function);
        }

        final boolean typed = choice.kind() != LlmToolChoice.UNRECOGNIZED;
        final boolean named = choice.kind() == LlmToolChoice.FUNCTION;
        final boolean flagged = disabled == JsonEvent.VALUE_TRUE || disabled == JsonEvent.VALUE_FALSE;

        if (typed)
        {
            ensure(HEADROOM + MEMBER_SHARED_TOOL_CHOICE.length() + CAPTURE_MAX);
            generator.writeKey(MEMBER_SHARED_TOOL_CHOICE);
            choice.emitCanonical(generator);
        }

        if (flagged)
        {
            ensure(HEADROOM + MEMBER_SHARED_PARALLEL.length());
            generator.write(MEMBER_SHARED_PARALLEL, disabled != JsonEvent.VALUE_TRUE);
        }

        boolean residue = false;
        int index = 1;

        while (captured.event(index) == JsonEvent.KEY_NAME)
        {
            final String name = captured.text(index);
            final boolean consumed = typed && MEMBER_TYPE.equals(name) ||
                named && MEMBER_NAME.equals(name) ||
                flagged && MEMBER_DISABLE_PARALLEL.equals(name);
            final int next = captured.end(index + 1);

            if (!consumed)
            {
                ensure(HEADROOM + MEMBER_NATIVE_TOOL_CHOICE.length());

                if (!residue)
                {
                    residue = true;
                    generator.writeStartObject(MEMBER_NATIVE_TOOL_CHOICE);
                }

                copyText(JsonEvent.KEY_NAME, name, false);
                replay(index + 1, next);
            }

            index = next;
        }

        if (residue)
        {
            ensure(HEADROOM);
            generator.writeEnd();
        }
        else if (!typed && !flagged)
        {
            ensure(HEADROOM + MEMBER_NATIVE_TOOL_CHOICE.length());
            generator.writeStartObject(MEMBER_NATIVE_TOOL_CHOICE).writeEnd();
        }
    }

    private int captureValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = MORE;

        switch (event)
        {
        case START_OBJECT, START_ARRAY:
            depth++;
            outcome = captured.add(event, null, CAPTURE_MAX) ? MORE : REJECT;
            break;
        case END_OBJECT, END_ARRAY:
            depth--;
            outcome = captured.add(event, null, CAPTURE_MAX) ? depth == 0 ? VALUE : MORE : REJECT;
            break;
        case VALUE_TRUE, VALUE_FALSE, VALUE_NULL:
            outcome = captured.add(event, null, CAPTURE_MAX) ? depth == 0 ? VALUE : MORE : REJECT;
            break;
        default:
            capture.append(parser.getStringView());

            if (capture.length() > CAPTURE_MAX)
            {
                outcome = REJECT;
            }
            else if (!parser.deferredBytes())
            {
                final String text = capture.toString();

                capture.setLength(0);
                outcome = captured.add(event, text, CAPTURE_MAX)
                    ? depth == 0 && event != JsonEvent.KEY_NAME ? VALUE : MORE
                    : REJECT;
            }
            break;
        }

        return outcome;
    }

    private void replay(
        int from,
        int to)
    {
        for (int index = from; index < to; index++)
        {
            ensure(HEADROOM);

            final String text = captured.text(index);

            switch (captured.event(index))
            {
            case START_OBJECT:
                generator.writeStartObject();
                break;
            case START_ARRAY:
                generator.writeStartArray();
                break;
            case END_OBJECT, END_ARRAY:
                generator.writeEnd();
                break;
            case KEY_NAME:
                copyText(JsonEvent.KEY_NAME, text, false);
                break;
            case VALUE_STRING:
                copyText(JsonEvent.VALUE_STRING, text, false);
                break;
            case VALUE_NUMBER:
                copyText(JsonEvent.VALUE_NUMBER, text, false);
                break;
            case VALUE_TRUE:
                generator.write(true);
                break;
            case VALUE_FALSE:
                generator.write(false);
                break;
            default:
                generator.writeNull();
                break;
            }
        }
    }

    private void ensure(
        int need)
    {
        if (generator.remaining() < need)
        {
            flush(false);
        }

        if (generator.remaining() < need)
        {
            throw new JsonException("insufficient room to write the next member");
        }
    }

    private void writeFlag(
        JsonEvent event)
    {
        if (event == JsonEvent.VALUE_NULL)
        {
            generator.writeNull();
        }
        else
        {
            generator.write(event == JsonEvent.VALUE_TRUE);
        }
    }

    private static boolean flag(
        JsonEvent event)
    {
        return event == JsonEvent.VALUE_TRUE || event == JsonEvent.VALUE_FALSE || event == JsonEvent.VALUE_NULL;
    }

    private void copy(
        JsonEvent event,
        JsonParserEx parser)
    {
        switch (event)
        {
        case START_OBJECT:
            generator.writeStartObject();
            depth++;
            break;
        case START_ARRAY:
            generator.writeStartArray();
            depth++;
            break;
        case END_OBJECT, END_ARRAY:
            generator.writeEnd();
            depth--;
            break;
        case VALUE_TRUE:
            generator.write(true);
            break;
        case VALUE_FALSE:
            generator.write(false);
            break;
        case VALUE_NULL:
            generator.writeNull();
            break;
        default:
            copyText(event, parser.getStringView(), parser.deferredBytes());
            break;
        }
    }

    private void copyText(
        JsonEvent event,
        CharSequence chars,
        boolean deferred)
    {
        final Completion completion = deferred ? Completion.INCOMPLETE : Completion.COMPLETE;
        final int length = chars.length();

        int taken = 0;

        do
        {
            final CharSequence rest = taken == 0 ? chars : chars.subSequence(taken, length);
            final int before = generator.consumed();

            if (event == JsonEvent.KEY_NAME)
            {
                generator.writeKey(rest, completion);
            }
            else if (event == JsonEvent.VALUE_NUMBER)
            {
                generator.writeNumber(rest, completion);
            }
            else
            {
                generator.write(rest, completion);
            }

            final int advanced = generator.consumed() - before;

            taken += advanced;

            if (taken < length)
            {
                if (advanced == 0 && generator.length() == 0)
                {
                    throw new JsonException("insufficient room to write the next character");
                }

                flush(false);
            }
        }
        while (taken < length);
    }

    private static int step(
        JsonEvent event)
    {
        return switch (event)
        {
        case START_OBJECT, START_ARRAY -> 1;
        case END_OBJECT, END_ARRAY -> -1;
        default -> 0;
        };
    }

    private static boolean deferred(
        JsonEvent event,
        JsonParserEx parser)
    {
        return event == JsonEvent.VALUE_STRING && parser.deferredBytes();
    }
}
