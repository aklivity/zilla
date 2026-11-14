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

    private static final String PREFIX = LlmOpenaiResponsesDialectFactorySpi.NAME + ":";
    private static final String TYPE_FUNCTION = "function";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_ARGUMENTS = "arguments";
    private static final int HELD_MAX = 4096;
    private static final int HEADROOM = 16;
    private static final int ESCAPE_GROWTH = 6;
    private static final int CAPACITY_HEADROOM = 4096;

    private static final int MEMBER_KEY = 0;
    private static final int MEMBER_DROP = 1;
    private static final int MEMBER_COPY = 2;
    private static final int MEMBER_STRING = 3;
    private static final int MEMBER_STRING_OR_DROP = 4;
    private static final int MEMBER_HOLD = 5;
    private static final int MEMBER_UNESCAPE = 6;
    private static final int MEMBER_UNESCAPED = 7;
    private static final int MEMBER_ELEMENT = 8;
    private static final int MEMBER_REJECT = 9;
    private static final int MEMBER_FLAG = 10;
    private static final int MEMBER_FLAG_USAGE = 11;
    private static final int MEMBER_FLAG_OBFUSCATION = 12;
    private static final int MEMBER_OPTIONS = 13;
    private static final int MEMBER_CAPTURE = 14;
    private static final int MEMBER_CONTAINER = 15;
    private static final int MEMBER_FORMAT = 16;
    private static final int MEMBER_FORMAT_TYPE = 17;
    private static final int MEMBER_PRECEDING = 18;
    private static final int MEMBER_PASS = 19;

    private static final int CAPTURE_MAX = 2048;
    private static final int TEXT_NONE = 0;
    private static final int TEXT_RESIDUE = 1;
    private static final int TEXT_LIFTED = 2;
    private static final int FORMAT_AWAITING = 0;
    private static final int FORMAT_FLATTENING = 1;
    private static final int FORMAT_PLAIN = 2;
    private static final int LEVEL_CONTAINER = 2;
    private static final int LEVEL_FORMAT = 3;
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

    private static final int CITATION_NONE = 0;
    private static final int CITATION_URL = 1;
    private static final int CITATION_OTHER = 2;
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
    private int member;
    private int depth;
    private boolean named;
    private boolean argumented;
    private int citation;
    private boolean nested;
    private boolean nestedClosed;
    private boolean streamSeen;
    private boolean streaming;
    private boolean optionsSeen;
    private boolean usageSeen;
    private boolean usageRequested;
    private boolean obfuscationDisabled;
    private int textState;
    private int formatState;
    private int choiceLevel;

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
        this.member = kind == KIND_CITATIONS
            ? MEMBER_ELEMENT
            : kind == KIND_STREAM ? MEMBER_FLAG
            : kind == KIND_STREAM_OPTIONS ? MEMBER_OPTIONS
            : kind == KIND_TOOL_CHOICE || kind == KIND_SERVICE_TIER ? MEMBER_CAPTURE
            : kind == KIND_TEXT || kind == KIND_REASONING ? MEMBER_CONTAINER : MEMBER_KEY;
        this.depth = 0;
        this.textState = TEXT_NONE;
        this.formatState = FORMAT_AWAITING;
        this.choiceLevel = LEVEL_CONTAINER;
        this.capture.setLength(0);
        this.captured.clear();
        this.preceding.clear();
        this.streamSeen = false;
        this.streaming = false;
        this.optionsSeen = false;
        this.usageSeen = false;
        this.usageRequested = false;
        this.obfuscationDisabled = false;
        this.named = false;
        this.argumented = false;
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

    boolean streamSeen()
    {
        return streamSeen;
    }

    boolean streaming()
    {
        return streaming;
    }

    boolean optionsSeen()
    {
        return optionsSeen;
    }

    boolean usageRequested()
    {
        return usageRequested;
    }

    boolean obfuscationDisabled()
    {
        return obfuscationDisabled;
    }

    int accept(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = MORE;

        final boolean fragment = event == JsonEvent.VALUE_STRING && parser.deferredBytes();
        final int length = fragment ? parser.getStringView().length() : 0;

        reserve(event, parser);

        switch (member)
        {
        case MEMBER_KEY:
            outcome = onKey(event, parser);
            break;
        case MEMBER_DROP:
            onDrop(event, parser);
            break;
        case MEMBER_COPY:
            onCopy(event, parser);
            break;
        case MEMBER_STRING:
            outcome = onString(event, parser);
            break;
        case MEMBER_STRING_OR_DROP:
            outcome = onStringOrDrop(event, parser);
            break;
        case MEMBER_HOLD:
            outcome = onHold(event, parser);
            break;
        case MEMBER_UNESCAPE:
            outcome = onUnescape(event, parser);
            break;
        case MEMBER_UNESCAPED:
            outcome = onUnescaped(event);
            break;
        case MEMBER_ELEMENT:
            outcome = onElement(event);
            break;
        case MEMBER_FLAG:
            outcome = onStreamFlag(event);
            break;
        case MEMBER_FLAG_USAGE, MEMBER_FLAG_OBFUSCATION:
            outcome = onOptionFlag(event);
            break;
        case MEMBER_OPTIONS:
            outcome = onOptions(event);
            break;
        case MEMBER_CAPTURE:
            outcome = onCapture(event, parser);
            break;
        case MEMBER_CONTAINER:
            outcome = onContainer(event, parser);
            break;
        case MEMBER_FORMAT:
            outcome = onFormat(event);
            break;
        case MEMBER_FORMAT_TYPE:
            outcome = onFormatType(event, parser);
            break;
        case MEMBER_PRECEDING:
            outcome = onPreceding(event, parser);
            break;
        case MEMBER_PASS:
            outcome = onPass(event, parser);
            break;
        default:
            outcome = REJECT;
            break;
        }

        if (fragment && outcome != REJECT)
        {
            parser.consumed(length);
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
        close();
    }

    static int capacity(
        int window)
    {
        return ESCAPE_GROWTH * window + CAPACITY_HEADROOM;
    }

    private void reserve(
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

    private int onKey(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = MORE;

        if (event == JsonEvent.KEY_NAME)
        {
            selectMember(parser.getStringView());

            switch (member)
            {
            case MEMBER_COPY, MEMBER_STRING:
                if (kind != KIND_CITATIONS)
                {
                    generator.writeKey(key);
                }
                break;
            case MEMBER_REJECT:
                outcome = REJECT;
                break;
            case MEMBER_UNESCAPE:
                parser.escaped();
                generator.writeKey(key);
                break;
            default:
                break;
            }
        }
        else if (event == JsonEvent.END_OBJECT)
        {
            outcome = kind == KIND_TEXT || kind == KIND_REASONING ? closeChoice() : close();
        }
        else
        {
            outcome = REJECT;
        }

        return outcome;
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
        case KIND_CITATIONS:
            selectCitation(name);
            break;
        case KIND_STREAM_OPTIONS:
            selectOption(name);
            break;
        case KIND_TEXT, KIND_REASONING:
            if (choiceLevel == LEVEL_FORMAT)
            {
                selectFormatMember(name);
            }
            else
            {
                selectChoiceMember(name);
            }
            break;
        default:
            selectDocument(name);
            break;
        }
    }

    private int onCapture(
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

    private int onContainer(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = MORE;

        if (event == JsonEvent.START_OBJECT)
        {
            textState = TEXT_NONE;
            choiceLevel = LEVEL_CONTAINER;
            member = MEMBER_KEY;
        }
        else
        {
            generator.writeKey(residueKey());
            member = MEMBER_PASS;
            outcome = onPass(event, parser);
        }

        return outcome;
    }

    private int onPass(
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

    private String residueKey()
    {
        return kind == KIND_REASONING ? MEMBER_NATIVE_REASONING : MEMBER_NATIVE_TEXT;
    }

    private void openResidue()
    {
        if (textState != TEXT_RESIDUE)
        {
            generator.writeStartObject(residueKey());
            textState = TEXT_RESIDUE;
        }
    }

    private void closeResidue()
    {
        if (textState == TEXT_RESIDUE)
        {
            generator.writeEnd();
        }

        textState = TEXT_LIFTED;
    }

    private void selectChoiceMember(
        CharSequence name)
    {
        if (kind == KIND_REASONING && is(name, MEMBER_EFFORT))
        {
            closeResidue();
            key.setLength(0);
            key.append(MEMBER_SHARED_REASONING_EFFORT);
            member = MEMBER_STRING_OR_DROP;
        }
        else if (kind == KIND_TEXT && is(name, MEMBER_FORMAT_NAME))
        {
            closeResidue();
            generator.writeKey(MEMBER_SHARED_RESPONSE_FORMAT);
            member = MEMBER_FORMAT;
        }
        else
        {
            openResidue();
            target(name);
            member = MEMBER_COPY;
        }
    }

    private void selectFormatMember(
        CharSequence name)
    {
        if (is(name, MEMBER_TYPE))
        {
            member = MEMBER_FORMAT_TYPE;
        }
        else if (formatState == FORMAT_FLATTENING)
        {
            target(name);
            member = MEMBER_COPY;
        }
        else if (formatState == FORMAT_AWAITING)
        {
            member = preceding.add(JsonEvent.KEY_NAME, name.toString(), CAPTURE_MAX) ? MEMBER_PRECEDING : MEMBER_REJECT;
            depth = 0;
        }
        else
        {
            member = MEMBER_DROP;
        }
    }

    private int onFormat(
        JsonEvent event)
    {
        int outcome = MORE;

        if (event == JsonEvent.START_OBJECT)
        {
            generator.writeStartObject();
            formatState = FORMAT_AWAITING;
            preceding.clear();
            choiceLevel = LEVEL_FORMAT;
            member = MEMBER_KEY;
        }
        else
        {
            outcome = REJECT;
        }

        return outcome;
    }

    private int onFormatType(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = MORE;

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
        }
        else
        {
            outcome = REJECT;
        }

        return outcome;
    }

    private int acceptFormatType(
        String type)
    {
        int outcome = MORE;

        if (MEMBER_JSON_SCHEMA.equals(type))
        {
            generator.write(MEMBER_TYPE, MEMBER_JSON_SCHEMA).writeStartObject(MEMBER_JSON_SCHEMA);
            replay(preceding);
            formatState = FORMAT_FLATTENING;
            member = MEMBER_KEY;
        }
        else if (TYPE_TEXT.equals(type) || TYPE_JSON_OBJECT.equals(type))
        {
            generator.write(MEMBER_TYPE, type);
            formatState = FORMAT_PLAIN;
            member = MEMBER_KEY;
        }
        else
        {
            outcome = REJECT;
        }

        return outcome;
    }

    private int onPreceding(
        JsonEvent event,
        JsonParserEx parser)
    {
        final int outcome = captureValue(event, parser, preceding);

        if (outcome == DONE)
        {
            member = MEMBER_KEY;
        }

        return outcome == REJECT ? REJECT : MORE;
    }

    private int closeChoice()
    {
        int outcome = MORE;

        if (choiceLevel == LEVEL_FORMAT)
        {
            if (formatState == FORMAT_FLATTENING)
            {
                generator.writeEnd().writeEnd();
            }
            else if (formatState == FORMAT_PLAIN)
            {
                generator.writeEnd();
            }
            else
            {
                outcome = REJECT;
            }

            choiceLevel = LEVEL_CONTAINER;
        }
        else
        {
            if (textState == TEXT_RESIDUE)
            {
                generator.writeEnd();
            }
            else if (textState == TEXT_NONE)
            {
                generator.writeStartObject(residueKey()).writeEnd();
            }

            flush(true);
            outcome = DONE;
        }

        return outcome;
    }

    private int onStreamFlag(
        JsonEvent event)
    {
        int outcome = REJECT;

        if (event == JsonEvent.VALUE_TRUE || event == JsonEvent.VALUE_FALSE || event == JsonEvent.VALUE_NULL)
        {
            streamSeen = true;
            streaming = event == JsonEvent.VALUE_TRUE;
            writeFlag(event);
            generator.writeEnd();
            flush(true);
            outcome = DONE;
        }

        return outcome;
    }

    private int onOptions(
        JsonEvent event)
    {
        int outcome = MORE;

        if (event == JsonEvent.START_OBJECT)
        {
            optionsSeen = true;
            usageSeen = false;
            generator.writeStartObject();
            member = MEMBER_KEY;
        }
        else if (event == JsonEvent.VALUE_NULL)
        {
            optionsSeen = true;
            generator.writeStartObject().write(MEMBER_INCLUDE_USAGE, true).writeEnd().writeEnd();
            flush(true);
            outcome = DONE;
        }
        else
        {
            outcome = REJECT;
        }

        return outcome;
    }

    private int onOptionFlag(
        JsonEvent event)
    {
        int outcome = MORE;

        if (event == JsonEvent.VALUE_TRUE || event == JsonEvent.VALUE_FALSE || event == JsonEvent.VALUE_NULL)
        {
            if (member == MEMBER_FLAG_USAGE)
            {
                usageSeen = true;
                usageRequested = event == JsonEvent.VALUE_TRUE;
                generator.write(MEMBER_INCLUDE_USAGE, true);
            }
            else
            {
                obfuscationDisabled = event == JsonEvent.VALUE_FALSE;
                generator.writeKey(key);
                writeFlag(event);
            }

            member = MEMBER_KEY;
        }
        else
        {
            outcome = REJECT;
        }

        return outcome;
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

    private void selectOption(
        CharSequence name)
    {
        if (is(name, MEMBER_INCLUDE_USAGE))
        {
            member = MEMBER_FLAG_USAGE;
        }
        else if (is(name, MEMBER_INCLUDE_OBFUSCATION))
        {
            key.setLength(0);
            key.append(LlmOpenaiMembers.VENDOR).append(':').append(name);
            member = MEMBER_FLAG_OBFUSCATION;
        }
        else
        {
            key.setLength(0);
            key.append(LlmOpenaiMembers.scope(PREFIX.substring(0, PREFIX.length() - 1), name)).append(':').append(name);
            member = MEMBER_COPY;
        }
    }

    private int onElement(
        JsonEvent event)
    {
        int outcome = MORE;

        if (event == JsonEvent.START_OBJECT)
        {
            generator.writeStartObject();
            held.setLength(0);
            citation = CITATION_NONE;
            nested = false;
            nestedClosed = false;
            member = MEMBER_KEY;
        }
        else if (event == JsonEvent.END_ARRAY)
        {
            generator.writeEnd();
            flush(true);
            outcome = DONE;
        }
        else
        {
            outcome = REJECT;
        }

        return outcome;
    }

    private void selectCitation(
        CharSequence name)
    {
        if (is(name, MEMBER_TYPE))
        {
            held.setLength(0);
            member = MEMBER_HOLD;
        }
        else if (citation == CITATION_NONE && held.length() == 0)
        {
            member = MEMBER_REJECT;
        }
        else
        {
            resolveCitation();

            if (citation == CITATION_URL && citationNested(name))
            {
                if (nestedClosed)
                {
                    member = MEMBER_REJECT;
                }
                else
                {
                    openNested();
                    target(name);
                    member = is(name, "url")
                        ? MEMBER_STRING
                        : is(name, "title") ? MEMBER_STRING_OR_DROP : MEMBER_COPY;

                    if (member == MEMBER_STRING || member == MEMBER_COPY)
                    {
                        generator.writeKey(key);
                    }
                }
            }
            else
            {
                closeNested();
                prefixed(name);
                member = MEMBER_COPY;
                generator.writeKey(key);
            }
        }
    }

    private void resolveCitation()
    {
        if (citation == CITATION_NONE)
        {
            final boolean url = TYPE_URL_CITATION.contentEquals(held);

            generator.write(url ? MEMBER_TYPE : PREFIX + MEMBER_TYPE, held.toString());
            citation = url ? CITATION_URL : CITATION_OTHER;
        }
    }

    private void openNested()
    {
        if (!nested)
        {
            generator.writeStartObject(TYPE_URL_CITATION);
            nested = true;
        }
    }

    private void closeNested()
    {
        if (nested)
        {
            generator.writeEnd();
            nested = false;
            nestedClosed = true;
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
            argumented = true;
            target(name);
            member = MEMBER_UNESCAPE;
        }
        else if (identity(name))
        {
            member = MEMBER_DROP;
        }
        else
        {
            prefixed(name);
            member = MEMBER_COPY;
        }
    }

    private void selectToolDefinition(
        CharSequence name)
    {
        if (is(name, "name"))
        {
            named = true;
            target(name);
            member = MEMBER_STRING;
        }
        else if (is(name, "description"))
        {
            target(name);
            member = MEMBER_STRING_OR_DROP;
        }
        else if (is(name, "parameters") || is(name, "strict"))
        {
            target(name);
            member = MEMBER_COPY;
        }
        else if (is(name, MEMBER_TYPE))
        {
            member = MEMBER_HOLD;
        }
        else
        {
            prefixed(name);
            member = MEMBER_COPY;
        }
    }

    private void selectServerTool(
        CharSequence name)
    {
        if (identity(name))
        {
            member = MEMBER_DROP;
        }
        else
        {
            target(name);
            member = MEMBER_COPY;
        }
    }

    private void selectImage(
        CharSequence name)
    {
        if (is(name, MEMBER_TYPE))
        {
            member = MEMBER_DROP;
        }
        else if (is(name, "image_url"))
        {
            key.setLength(0);
            key.append("url");
            member = MEMBER_STRING_OR_DROP;
        }
        else if (is(name, "file_id") || is(name, "detail"))
        {
            target(name);
            member = MEMBER_STRING_OR_DROP;
        }
        else
        {
            prefixed(name);
            member = MEMBER_COPY;
        }
    }

    private void selectDocument(
        CharSequence name)
    {
        if (is(name, MEMBER_TYPE))
        {
            member = MEMBER_DROP;
        }
        else if (is(name, "file_data") || is(name, "file_url") || is(name, "file_id") || is(name, "filename"))
        {
            target(name);
            member = MEMBER_STRING_OR_DROP;
        }
        else
        {
            prefixed(name);
            member = MEMBER_COPY;
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

    private void onDrop(
        JsonEvent event,
        JsonParserEx parser)
    {
        depth += step(event);

        if (depth == 0 && !deferred(event, parser))
        {
            member = MEMBER_KEY;
        }
    }

    private void onCopy(
        JsonEvent event,
        JsonParserEx parser)
    {
        copy(event, parser);

        if (depth == 0 && !deferred(event, parser))
        {
            member = MEMBER_KEY;
        }
    }

    private int onString(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = MORE;

        if (event == JsonEvent.VALUE_STRING)
        {
            final boolean deferred = parser.deferredBytes();

            copy(event, parser);

            if (!deferred)
            {
                member = MEMBER_KEY;
            }
        }
        else
        {
            outcome = REJECT;
        }

        return outcome;
    }

    private int onStringOrDrop(
        JsonEvent event,
        JsonParserEx parser)
    {
        if (event == JsonEvent.VALUE_STRING)
        {
            generator.writeKey(key);
            member = MEMBER_STRING;
        }
        else
        {
            member = MEMBER_DROP;
        }

        return member == MEMBER_STRING ? onString(event, parser) : dropFirst(event, parser);
    }

    private int dropFirst(
        JsonEvent event,
        JsonParserEx parser)
    {
        onDrop(event, parser);

        return MORE;
    }

    private int onHold(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = MORE;

        if (event == JsonEvent.VALUE_STRING)
        {
            held.append(parser.getStringView());

            if (held.length() > HELD_MAX)
            {
                outcome = REJECT;
            }
            else if (!parser.deferredBytes())
            {
                member = MEMBER_KEY;
            }
        }
        else
        {
            outcome = REJECT;
        }

        return outcome;
    }

    private int onUnescape(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = MORE;

        if (depth == 0 && event == JsonEvent.START_ESCAPED)
        {
            depth = 0;
        }
        else if (depth == 0 && event == JsonEvent.START_OBJECT)
        {
            copy(event, parser);
        }
        else if (depth > 0)
        {
            copy(event, parser);

            if (depth == 0)
            {
                member = MEMBER_UNESCAPED;
            }
        }
        else
        {
            outcome = REJECT;
        }

        return outcome;
    }

    private int onUnescaped(
        JsonEvent event)
    {
        int outcome = MORE;

        if (event == JsonEvent.END_ESCAPED)
        {
            member = MEMBER_KEY;
        }
        else
        {
            outcome = REJECT;
        }

        return outcome;
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

    private int close()
    {
        int outcome = DONE;

        switch (kind)
        {
        case KIND_TOOL_CALL:
            if (!argumented)
            {
                generator.writeStartObject(MEMBER_ARGUMENTS).writeEnd();
            }
            break;
        case KIND_TOOL_DEFINITION:
            if (named && TYPE_FUNCTION.contentEquals(held))
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
        case KIND_CITATIONS:
            if (held.length() > 0)
            {
                resolveCitation();
            }

            closeNested();
            break;
        case KIND_STREAM_OPTIONS:
            if (!usageSeen)
            {
                generator.write(MEMBER_INCLUDE_USAGE, true);
            }

            generator.writeEnd();
            break;
        default:
            break;
        }

        if (outcome == DONE && kind == KIND_CITATIONS)
        {
            generator.writeEnd();
            member = MEMBER_ELEMENT;
            outcome = MORE;
        }
        else if (outcome == DONE)
        {
            generator.writeEnd();
            flush(true);
        }

        return outcome;
    }
}
