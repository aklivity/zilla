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

    private static final int CITATION_NONE = 0;
    private static final int CITATION_URL = 1;
    private static final int CITATION_OTHER = 2;
    private static final String TYPE_URL_CITATION = "url_citation";

    private final MutableDirectBufferEx buffer;
    private final LlmSectionPipeline.Output sink;
    private final JsonGeneratorEx generator;
    private final StringBuilder key = new StringBuilder();
    private final StringBuilder held = new StringBuilder();

    private int kind;
    private int member;
    private int depth;
    private boolean named;
    private boolean argumented;
    private int citation;
    private boolean nested;
    private boolean nestedClosed;

    LlmOpenaiResponsesBlockWriter(
        MutableDirectBufferEx buffer,
        LlmSectionPipeline.Output sink,
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
        this.member = kind == KIND_CITATIONS ? MEMBER_ELEMENT : MEMBER_KEY;
        this.depth = 0;
        this.named = false;
        this.argumented = false;
        this.held.setLength(0);

        generator.reset();
        generator.wrap(buffer, 0, buffer.capacity());

        if (kind == KIND_CITATIONS)
        {
            generator.writeStartArray();
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
        default:
            break;
        }
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
            outcome = close();
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
        default:
            selectDocument(name);
            break;
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
