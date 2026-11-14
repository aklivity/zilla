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

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.view.JsonStringView;

final class LlmOpenaiResponsesBlockWriter
{
    static final int KIND_TOOL_CALL = 0;
    static final int KIND_TOOL_DEFINITION = 1;
    static final int KIND_SERVER_TOOL_CALL = 2;
    static final int KIND_SERVER_TOOL_RESULT = 3;
    static final int KIND_IMAGE = 4;
    static final int KIND_DOCUMENT = 5;
    static final int KIND_CITATIONS = 6;
    static final int KIND_STREAM = 7;
    static final int KIND_STREAM_OPTIONS = 8;
    static final int KIND_TOOL_CHOICE = 9;
    static final int KIND_SERVICE_TIER = 10;
    static final int KIND_TEXT = 11;
    static final int KIND_REASONING = 12;

    static final int MORE = 0;
    static final int DONE = 1;
    static final int REJECT = 2;
    static final int VALUE = 3;
    static final int PASS = 4;

    private static final String PREFIX = LlmOpenaiResponsesDialectFactorySpi.NAME + ":";
    private static final String TYPE_FUNCTION = "function";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_ARGUMENTS = "arguments";
    private static final int HELD_MAX = 4096;
    private static final int HEADROOM = 16;
    private static final int ESCAPE_GROWTH = 6;
    private static final int CAPACITY_HEADROOM = 4096;

    static final int ACTION_DROP = 1;
    static final int ACTION_COPY = 2;
    static final int ACTION_STRING = 3;
    static final int ACTION_STRING_OR_DROP = 4;
    static final int ACTION_HOLD = 5;
    static final int ACTION_UNESCAPE = 6;
    static final int ACTION_REJECT = 9;
    static final int ACTION_NAME = 20;
    static final int ACTION_ARGUMENTS = 21;
    static final int ACTION_FLAG_USAGE = 11;
    static final int ACTION_FLAG_OBFUSCATION = 12;
    static final int ACTION_FORMAT = 16;
    static final int ACTION_FORMAT_TYPE = 17;
    static final int ACTION_PRECEDING = 18;

    private static final int CAPTURE_MAX = 2048;
    private static final String MEMBER_NATIVE_TOOL_CHOICE = PREFIX + "tool_choice";
    private static final String MEMBER_SHARED_TOOL_CHOICE = "x:tool_choice";
    private static final String MEMBER_NATIVE_TEXT = PREFIX + "text";
    private static final String MEMBER_NATIVE_REASONING = PREFIX + "reasoning";
    private static final String MEMBER_SHARED_REASONING_EFFORT = "x:reasoning_effort";
    private static final String MEMBER_SHARED_SERVICE_TIER = "x:service_tier";
    private static final String MEMBER_NATIVE_SERVICE_TIER = "openai:service_tier";
    private static final String MEMBER_SHARED_RESPONSE_FORMAT = "x:response_format";
    private static final String MEMBER_EFFORT = "effort";
    private static final String MEMBER_FORMAT_NAME = "format";
    private static final String MEMBER_JSON_SCHEMA = "json_schema";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_JSON_OBJECT = "json_object";
    private static final String MEMBER_STREAM = "stream";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String MEMBER_INCLUDE_USAGE = "include_usage";
    private static final String MEMBER_INCLUDE_OBFUSCATION = "include_obfuscation";

    private static final String TYPE_URL_CITATION = "url_citation";

    private final MutableDirectBufferEx buffer;
    private final LlmOutputSink sink;
    private final JsonGeneratorEx generator;
    private final StringBuilder key = new StringBuilder();
    private final StringBuilder held = new StringBuilder();
    private final StringBuilder capture = new StringBuilder();
    private final LlmCapturedValue captured = new LlmCapturedValue();
    private final LlmCapturedValue preceding = new LlmCapturedValue();
    private final LlmToolChoice choice = new LlmToolChoice();

    private int kind;
    private int action;
    private boolean schema;
    private int depth;
    private boolean streaming;
    private boolean usageRequested;
    private boolean obfuscationDisabled;

    LlmOpenaiResponsesBlockWriter(
        MutableDirectBufferEx buffer,
        LlmOutputSink sink,
        JsonGeneratorEx generator)
    {
        this.buffer = buffer;
        this.sink = sink;
        this.generator = generator;
    }

    void begin(
        int kind)
    {
        this.kind = kind;
        this.depth = 0;
        this.capture.setLength(0);
        this.captured.clear();
        this.preceding.clear();
        this.streaming = false;
        this.usageRequested = false;
        this.obfuscationDisabled = false;
        this.held.setLength(0);

        generator.reset();
        generator.wrap(buffer, 0, buffer.capacity());

        if (kind == KIND_CITATIONS)
        {
            generator.writeStartArray();
        }
        else if (kind >= KIND_TOOL_CHOICE)
        {
            generator.writeStartObject();
            generator.wrap(buffer, 0, buffer.capacity());
        }
        else
        {
            generator.writeStartObject();
        }

        switch (kind)
        {
        case KIND_TOOL_DEFINITION:
            generator.write(MEMBER_TYPE, TYPE_FUNCTION).writeStartObject(TYPE_FUNCTION);
            break;
        case KIND_SERVER_TOOL_CALL:
            generator.writeStartObject(MEMBER_ARGUMENTS);
            break;
        case KIND_IMAGE:
            generator.write(MEMBER_TYPE, "image_url").writeStartObject("image_url");
            break;
        case KIND_DOCUMENT:
            generator.write(MEMBER_TYPE, "file").writeStartObject("file");
            break;
        case KIND_STREAM:
            generator.writeKey(MEMBER_STREAM);
            break;
        case KIND_STREAM_OPTIONS:
            generator.writeKey(MEMBER_STREAM_OPTIONS);
            break;
        default:
            break;
        }
    }

    int kind()
    {
        return kind;
    }

    boolean streaming()
    {
        return streaming;
    }

    boolean usageRequested()
    {
        return usageRequested;
    }

    boolean obfuscationDisabled()
    {
        return obfuscationDisabled;
    }

    int select(
        CharSequence name,
        JsonParserEx parser)
    {
        selectMember(name);

        final int selected = action;

        switch (selected)
        {
        case ACTION_COPY, ACTION_STRING, ACTION_NAME:
            generator.writeKey(key);
            break;
        case ACTION_UNESCAPE, ACTION_ARGUMENTS:
            parser.escaped();
            generator.writeKey(key);
            break;
        default:
            break;
        }

        return selected;
    }

    void reserve(
        JsonEvent event,
        JsonParserEx parser)
    {
        int need = HEADROOM;

        if (event == JsonEvent.KEY_NAME || event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER)
        {
            need += ESCAPE_GROWTH * (PREFIX.length() + parser.getStringView().length());
        }

        if (generator.remaining() < need)
        {
            flush(false);
        }
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

    int string(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = REJECT;

        if (event == JsonEvent.VALUE_STRING)
        {
            final boolean fragment = parser.deferredBytes();

            copy(event, parser);
            outcome = fragment ? MORE : VALUE;
        }

        return outcome;
    }

    void openKey()
    {
        generator.writeKey(key);
    }

    int hold(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = REJECT;

        if (event == JsonEvent.VALUE_STRING)
        {
            held.append(parser.getStringView());

            if (held.length() <= HELD_MAX)
            {
                outcome = parser.deferredBytes() ? MORE : VALUE;
            }
        }

        return outcome;
    }

    int unescape(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = MORE;

        if (depth == 0 && event == JsonEvent.START_ESCAPED)
        {
            outcome = MORE;
        }
        else if (depth == 0 && event == JsonEvent.START_OBJECT)
        {
            copy(event, parser);
        }
        else if (depth > 0)
        {
            copy(event, parser);
            outcome = depth == 0 ? VALUE : MORE;
        }
        else
        {
            outcome = REJECT;
        }

        return outcome;
    }

    int unescaped(
        JsonEvent event)
    {
        return event == JsonEvent.END_ESCAPED ? VALUE : REJECT;
    }

    int streamFlag(
        JsonEvent event)
    {
        int outcome = REJECT;

        if (event == JsonEvent.VALUE_TRUE || event == JsonEvent.VALUE_FALSE || event == JsonEvent.VALUE_NULL)
        {
            streaming = event == JsonEvent.VALUE_TRUE;
            writeFlag(event);
            generator.writeEnd();
            flush(true);
            outcome = DONE;
        }

        return outcome;
    }

    int obfuscationFlag(
        JsonEvent event)
    {
        int outcome = REJECT;

        if (flag(event))
        {
            obfuscationDisabled = event == JsonEvent.VALUE_FALSE;
            generator.writeKey(key);
            writeFlag(event);
            outcome = VALUE;
        }

        return outcome;
    }

    int capture(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = captureValue(event, parser, captured);

        if (outcome == DONE)
        {
            if (kind == KIND_TOOL_CHOICE)
            {
                if (choice.parseResponses(captured))
                {
                    generator.writeKey(MEMBER_SHARED_TOOL_CHOICE);
                    choice.emitCanonical(generator);
                }
                else
                {
                    generator.writeKey(MEMBER_NATIVE_TOOL_CHOICE);
                    replay(captured);
                }
            }
            else
            {
                final String tier = LlmServiceTiers.canonicalOpenai(captured.string(0));

                if (tier != null)
                {
                    generator.write(MEMBER_SHARED_SERVICE_TIER, tier);
                }
                else
                {
                    generator.writeKey(MEMBER_NATIVE_SERVICE_TIER);
                    replay(captured);
                }
            }

            flush(true);
        }

        return outcome;
    }

    int pass(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = MORE;

        copy(event, parser);

        if (depth == 0 && !deferred(event, parser))
        {
            flush(true);
            outcome = DONE;
        }

        return outcome;
    }

    int preceding(
        JsonEvent event,
        JsonParserEx parser)
    {
        final int outcome = captureValue(event, parser, preceding);

        return outcome == DONE ? VALUE : outcome;
    }

    boolean citations()
    {
        return kind == KIND_CITATIONS;
    }

    private static boolean flag(
        JsonEvent event)
    {
        return event == JsonEvent.VALUE_TRUE || event == JsonEvent.VALUE_FALSE || event == JsonEvent.VALUE_NULL;
    }

    int closeItem(
        boolean seen)
    {
        int outcome = DONE;

        switch (kind)
        {
        case KIND_TOOL_CALL:
            if (!seen)
            {
                generator.writeStartObject(MEMBER_ARGUMENTS).writeEnd();
            }
            break;
        case KIND_TOOL_DEFINITION:
            if (seen && TYPE_FUNCTION.contentEquals(held))
            {
                generator.writeEnd();
            }
            else
            {
                outcome = REJECT;
            }
            break;
        case KIND_SERVER_TOOL_CALL, KIND_IMAGE, KIND_DOCUMENT:
            generator.writeEnd();
            break;
        default:
            break;
        }

        if (outcome == DONE)
        {
            generator.writeEnd();
            flush(true);
        }

        return outcome;
    }

    int complete()
    {
        flush(true);

        return DONE;
    }

    boolean isType(
        CharSequence name)
    {
        return is(name, MEMBER_TYPE);
    }

    void holdType()
    {
        held.setLength(0);
    }

    boolean heldType()
    {
        return held.length() > 0;
    }

    boolean resolveCitation()
    {
        final boolean url = TYPE_URL_CITATION.contentEquals(held);

        generator.write(url ? MEMBER_TYPE : PREFIX + MEMBER_TYPE, held.toString());

        return url;
    }

    static boolean isCitationNested(
        CharSequence name)
    {
        return is(name, "url") || is(name, "title") || is(name, "start_index") || is(name, "end_index");
    }

    void openNested()
    {
        generator.writeStartObject(TYPE_URL_CITATION);
    }

    void closeNested()
    {
        generator.writeEnd();
    }

    void closeElement()
    {
        generator.writeEnd();
    }

    int citationNestedMember(
        CharSequence name)
    {
        target(name);

        final int selected = is(name, "url")
            ? ACTION_STRING
            : is(name, "title") ? ACTION_STRING_OR_DROP : ACTION_COPY;

        if (selected != ACTION_STRING_OR_DROP)
        {
            generator.writeKey(key);
        }

        return selected;
    }

    int prefixedMember(
        CharSequence name)
    {
        prefixed(name);
        generator.writeKey(key);

        return ACTION_COPY;
    }

    int element(
        JsonEvent event)
    {
        int outcome = REJECT;

        if (event == JsonEvent.START_OBJECT)
        {
            generator.writeStartObject();
            held.setLength(0);
            outcome = VALUE;
        }
        else if (event == JsonEvent.END_ARRAY)
        {
            generator.writeEnd();
            flush(true);
            outcome = DONE;
        }

        return outcome;
    }

    int options(
        JsonEvent event)
    {
        int outcome = REJECT;

        if (event == JsonEvent.START_OBJECT)
        {
            generator.writeStartObject();
            outcome = VALUE;
        }
        else if (event == JsonEvent.VALUE_NULL)
        {
            generator.writeStartObject().write(MEMBER_INCLUDE_USAGE, true).writeEnd().writeEnd();
            flush(true);
            outcome = DONE;
        }

        return outcome;
    }

    int optionMember(
        CharSequence name)
    {
        int selected = ACTION_COPY;

        if (is(name, MEMBER_INCLUDE_USAGE))
        {
            selected = ACTION_FLAG_USAGE;
        }
        else if (is(name, MEMBER_INCLUDE_OBFUSCATION))
        {
            key.setLength(0);
            key.append(LlmOpenaiMembers.VENDOR).append(':').append(name);
            selected = ACTION_FLAG_OBFUSCATION;
        }
        else
        {
            key.setLength(0);
            key.append(LlmOpenaiMembers.scope(PREFIX.substring(0, PREFIX.length() - 1), name)).append(':').append(name);
            generator.writeKey(key);
        }

        return selected;
    }

    void defaultUsage()
    {
        generator.write(MEMBER_INCLUDE_USAGE, true);
    }

    int closeOptions()
    {
        generator.writeEnd();
        generator.writeEnd();
        flush(true);

        return DONE;
    }

    int usageFlag(
        JsonEvent event)
    {
        int outcome = REJECT;

        if (flag(event))
        {
            usageRequested = event == JsonEvent.VALUE_TRUE;
            generator.write(MEMBER_INCLUDE_USAGE, true);
            outcome = VALUE;
        }

        return outcome;
    }

    boolean lifts(
        CharSequence name)
    {
        return kind == KIND_REASONING && is(name, MEMBER_EFFORT) || kind == KIND_TEXT && is(name, MEMBER_FORMAT_NAME);
    }

    int lift(
        CharSequence name)
    {
        int selected = ACTION_FORMAT;

        if (is(name, MEMBER_EFFORT))
        {
            key.setLength(0);
            key.append(MEMBER_SHARED_REASONING_EFFORT);
            selected = ACTION_STRING_OR_DROP;
        }
        else
        {
            generator.writeKey(MEMBER_SHARED_RESPONSE_FORMAT);
        }

        return selected;
    }

    int residueMember(
        CharSequence name)
    {
        target(name);
        generator.writeKey(key);

        return ACTION_COPY;
    }

    void openResidue()
    {
        generator.writeStartObject(residueKey());
    }

    void closeResidue()
    {
        generator.writeEnd();
    }

    void emptyResidue()
    {
        generator.writeStartObject(residueKey()).writeEnd();
    }

    int container(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = VALUE;

        if (event != JsonEvent.START_OBJECT)
        {
            generator.writeKey(residueKey());
            outcome = pass(event, parser);
            outcome = outcome == MORE ? PASS : outcome;
        }

        return outcome;
    }

    int format(
        JsonEvent event)
    {
        int outcome = REJECT;

        if (event == JsonEvent.START_OBJECT)
        {
            generator.writeStartObject();
            preceding.clear();
            outcome = VALUE;
        }

        return outcome;
    }

    int precedingMember(
        CharSequence name)
    {
        depth = 0;

        return preceding.add(JsonEvent.KEY_NAME, name.toString(), CAPTURE_MAX) ? ACTION_PRECEDING : ACTION_REJECT;
    }

    int formatMember(
        CharSequence name)
    {
        target(name);
        generator.writeKey(key);

        return ACTION_COPY;
    }

    boolean schema()
    {
        return schema;
    }

    void closeFormat(
        boolean flattening)
    {
        generator.writeEnd();

        if (flattening)
        {
            generator.writeEnd();
        }
    }

    int formatType(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = REJECT;

        if (event == JsonEvent.VALUE_STRING)
        {
            capture.append(parser.getStringView());

            if (capture.length() > CAPTURE_MAX)
            {
                outcome = REJECT;
            }
            else if (!parser.deferredBytes())
            {
                final String type = capture.toString();

                capture.setLength(0);
                outcome = acceptFormatType(type);
            }
            else
            {
                outcome = MORE;
            }
        }

        return outcome;
    }

    private int acceptFormatType(
        String type)
    {
        int outcome = REJECT;

        if (MEMBER_JSON_SCHEMA.equals(type))
        {
            generator.write(MEMBER_TYPE, MEMBER_JSON_SCHEMA).writeStartObject(MEMBER_JSON_SCHEMA);
            replay(preceding);
            schema = true;
            outcome = VALUE;
        }
        else if (TYPE_TEXT.equals(type) || TYPE_JSON_OBJECT.equals(type))
        {
            generator.write(MEMBER_TYPE, type);
            schema = false;
            outcome = VALUE;
        }

        return outcome;
    }

    void flush(
        boolean last)
    {
        final int length = generator.length();

        if (length > 0 || last)
        {
            sink.put(buffer, 0, length, last);
            generator.wrap(buffer, 0, buffer.capacity());
        }
    }

    void finish()
    {
        closeItem(false);
    }

    static int capacity(
        int window)
    {
        return ESCAPE_GROWTH * window + CAPACITY_HEADROOM;
    }

    private void selectMember(
        CharSequence name)
    {
        depth = 0;

        switch (kind)
        {
        case KIND_TOOL_CALL:
            selectToolCall(name);
            break;
        case KIND_TOOL_DEFINITION:
            selectToolDefinition(name);
            break;
        case KIND_SERVER_TOOL_CALL, KIND_SERVER_TOOL_RESULT:
            selectServerTool(name);
            break;
        case KIND_IMAGE:
            selectImage(name);
            break;
        default:
            selectDocument(name);
            break;
        }
    }

    private int captureValue(
        JsonEvent event,
        JsonParserEx parser,
        LlmCapturedValue target)
    {
        int outcome = MORE;

        switch (event)
        {
        case START_OBJECT, START_ARRAY:
            depth++;
            outcome = target.add(event, null, CAPTURE_MAX) ? MORE : REJECT;
            break;
        case END_OBJECT, END_ARRAY:
            depth--;
            outcome = target.add(event, null, CAPTURE_MAX) ? depth == 0 ? DONE : MORE : REJECT;
            break;
        case VALUE_TRUE, VALUE_FALSE, VALUE_NULL:
            outcome = target.add(event, null, CAPTURE_MAX) ? depth == 0 ? DONE : MORE : REJECT;
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
                outcome = target.add(event, text, CAPTURE_MAX)
                    ? depth == 0 && event != JsonEvent.KEY_NAME ? DONE : MORE
                    : REJECT;
            }
            break;
        }

        return outcome;
    }

    private void replay(
        LlmCapturedValue value)
    {
        for (int index = 0; index < value.count(); index++)
        {
            final String text = value.text(index);

            switch (value.event(index))
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
                generator.writeKey(text);
                break;
            case VALUE_STRING:
                generator.write(text);
                break;
            case VALUE_NUMBER:
                generator.writeNumber(text);
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

    private String residueKey()
    {
        return kind == KIND_REASONING ? MEMBER_NATIVE_REASONING : MEMBER_NATIVE_TEXT;
    }

    private void writeFlag(
        JsonEvent event)
    {
        switch (event)
        {
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

    private static boolean citationNested(
        CharSequence name)
    {
        return is(name, "url") || is(name, "title") || is(name, "start_index") || is(name, "end_index");
    }

    private void selectToolCall(
        CharSequence name)
    {
        if (is(name, MEMBER_ARGUMENTS))
        {
            target(name);
            action = ACTION_ARGUMENTS;
        }
        else if (identity(name))
        {
            action = ACTION_DROP;
        }
        else
        {
            prefixed(name);
            action = ACTION_COPY;
        }
    }

    private void selectToolDefinition(
        CharSequence name)
    {
        if (is(name, "name"))
        {
            target(name);
            action = ACTION_NAME;
        }
        else if (is(name, "description"))
        {
            target(name);
            action = ACTION_STRING_OR_DROP;
        }
        else if (is(name, "parameters") || is(name, "strict"))
        {
            target(name);
            action = ACTION_COPY;
        }
        else if (is(name, MEMBER_TYPE))
        {
            held.setLength(0);
            action = ACTION_HOLD;
        }
        else
        {
            prefixed(name);
            action = ACTION_COPY;
        }
    }

    private void selectServerTool(
        CharSequence name)
    {
        if (identity(name))
        {
            action = ACTION_DROP;
        }
        else
        {
            target(name);
            action = ACTION_COPY;
        }
    }

    private void selectImage(
        CharSequence name)
    {
        if (is(name, MEMBER_TYPE))
        {
            action = ACTION_DROP;
        }
        else if (is(name, "image_url"))
        {
            key.setLength(0);
            key.append("url");
            action = ACTION_STRING_OR_DROP;
        }
        else if (is(name, "file_id") || is(name, "detail"))
        {
            target(name);
            action = ACTION_STRING_OR_DROP;
        }
        else
        {
            prefixed(name);
            action = ACTION_COPY;
        }
    }

    private void selectDocument(
        CharSequence name)
    {
        if (is(name, MEMBER_TYPE))
        {
            action = ACTION_DROP;
        }
        else if (is(name, "file_data") || is(name, "file_url") || is(name, "file_id") || is(name, "filename"))
        {
            target(name);
            action = ACTION_STRING_OR_DROP;
        }
        else
        {
            prefixed(name);
            action = ACTION_COPY;
        }
    }

    private boolean identity(
        CharSequence name)
    {
        return is(name, "id") || is(name, MEMBER_TYPE) || is(name, "status") || is(name, "call_id") || is(name, "name");
    }

    private void target(
        CharSequence name)
    {
        key.setLength(0);
        key.append(name);
    }

    private void prefixed(
        CharSequence name)
    {
        key.setLength(0);
        key.append(PREFIX).append(name);
    }

    private static boolean is(
        CharSequence name,
        String expected)
    {
        return expected.contentEquals(name);
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
            final JsonStringView chars = parser.getStringView();
            final Completion completion = parser.deferredBytes() ? Completion.INCOMPLETE : Completion.COMPLETE;

            if (event == JsonEvent.KEY_NAME)
            {
                generator.writeKey(chars, completion);
            }
            else if (event == JsonEvent.VALUE_NUMBER)
            {
                generator.writeNumber(chars, completion);
            }
            else
            {
                generator.write(chars, completion);
            }
            break;
        }
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
