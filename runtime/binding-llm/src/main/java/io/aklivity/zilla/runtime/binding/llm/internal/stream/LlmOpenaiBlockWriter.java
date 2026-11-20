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
import io.aklivity.zilla.runtime.common.json.view.JsonStringView;

final class LlmOpenaiBlockWriter
{
    static final int MORE = 0;
    static final int VALUE = 1;
    static final int REJECT = 2;
    static final int STALL = 3;
    static final int RETRY = 4;

    static final int ACTION_DROP = 0;
    static final int ACTION_COPY = 1;
    static final int ACTION_FUNCTION = 2;
    static final int ACTION_UNESCAPE = 3;
    static final int ACTION_USAGE = 4;
    static final int ACTION_OBFUSCATION = 5;

    private static final String PREFIX = LlmOpenaiDialectFactorySpi.NAME + ":";
    private static final String MEMBER_ID = "id";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_FUNCTION = "function";
    private static final String MEMBER_ARGUMENTS = "arguments";
    private static final String MEMBER_STREAM = "stream";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String MEMBER_STOP = "x:stop";
    private static final String MEMBER_INCLUDE_USAGE = "include_usage";
    private static final String MEMBER_INCLUDE_OBFUSCATION = "include_obfuscation";
    private static final String MEMBER_SHARED_SERVICE_TIER = "x:service_tier";
    private static final String MEMBER_NATIVE_SERVICE_TIER = "openai:service_tier";

    private final LlmOutputSink sink;
    private final JsonGeneratorEx generator;
    private final StringBuilder key = new StringBuilder();

    private MutableDirectBufferEx buffer;
    private int origin;
    private int carried;
    private int flushed;
    private int depth;
    private boolean functioned;
    private boolean argumented;
    private boolean separating;
    private byte separator;
    private boolean usageWritten;
    private int fragmentTaken;

    private int markDepth;
    private boolean markFunctioned;
    private boolean markArgumented;
    private boolean markUsageWritten;

    LlmOpenaiBlockWriter(
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

        generator.wrap(buffer, origin + carried, buffer.capacity());
    }

    void suspend(
        MutableDirectBufferEx detached)
    {
        carried = staged();
        buffer = detached;

        generator.wrap(detached, 0, detached.capacity());
    }

    void begin(
        MutableDirectBufferEx buffer,
        int origin)
    {
        this.buffer = buffer;
        this.origin = origin;
        this.carried = 0;
        this.flushed = 0;
        this.depth = 0;
        this.functioned = false;
        this.argumented = false;

        generator.reset();
        generator.wrap(buffer, origin, buffer.capacity());
        generator.writeStartObject();
    }

    void beginMember(
        MutableDirectBufferEx buffer,
        int origin,
        char separator)
    {
        this.buffer = buffer;
        this.origin = origin;
        this.carried = 0;
        this.flushed = 0;
        this.depth = 0;
        this.separator = (byte) separator;
        this.separating = true;

        generator.reset();
        generator.wrap(buffer, origin, buffer.capacity());
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

    int optionsValue(
        JsonEvent event)
    {
        int outcome = REJECT;

        generator.writeKey(MEMBER_STREAM_OPTIONS);
        usageWritten = false;

        if (event == JsonEvent.START_OBJECT)
        {
            generator.writeStartObject();
            outcome = MORE;
        }
        else if (event == JsonEvent.VALUE_NULL)
        {
            generator.writeStartObject();
            writeIncludeUsage();
            generator.writeEnd();
            outcome = VALUE;
        }

        return outcome;
    }

    int optionsAction(
        CharSequence name)
    {
        int action = ACTION_COPY;

        if (MEMBER_INCLUDE_USAGE.contentEquals(name))
        {
            action = ACTION_USAGE;
        }
        else if (MEMBER_INCLUDE_OBFUSCATION.contentEquals(name))
        {
            generator.writeKey(LlmOpenaiMembers.VENDOR + ":" + name);
            action = ACTION_OBFUSCATION;
        }
        else
        {
            key.setLength(0);
            key.append(PREFIX).append(name);
            generator.writeKey(key);
        }

        return action;
    }

    int usageValue(
        JsonEvent event)
    {
        final boolean flag = flag(event);

        if (flag)
        {
            writeIncludeUsage();
        }

        return flag ? VALUE : REJECT;
    }

    int obfuscationValue(
        JsonEvent event)
    {
        final boolean flag = flag(event);

        if (flag)
        {
            writeFlag(event);
        }

        return flag ? VALUE : REJECT;
    }

    void closeOptions()
    {
        if (!usageWritten)
        {
            writeIncludeUsage();
        }

        generator.writeEnd();
    }

    int stopValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        generator.writeKey(MEMBER_STOP);

        final boolean wrap = event == JsonEvent.VALUE_STRING;

        if (wrap)
        {
            generator.writeStartArray();
        }

        final int outcome = copyValue(event, parser);

        if (wrap && outcome == VALUE)
        {
            generator.writeEnd();
        }

        return wrap ? outcome == VALUE ? VALUE : MORE : outcome;
    }

    int stopText(
        JsonEvent event,
        JsonParserEx parser)
    {
        final int outcome = copyValue(event, parser);

        if (outcome == VALUE)
        {
            generator.writeEnd();
        }

        return outcome;
    }

    int tierValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        final String tier = event == JsonEvent.VALUE_STRING && !parser.deferredBytes()
            ? LlmServiceTiers.canonicalOpenai(parser.getStringView().toString())
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

    int staged()
    {
        return carried + generator.length();
    }

    int pending()
    {
        return staged() - flushed;
    }

    int leftover()
    {
        return Math.max(carried - flushed, 0);
    }

    int fragmentTaken()
    {
        return fragmentTaken;
    }

    void mark()
    {
        generator.mark();
        markDepth = depth;
        markFunctioned = functioned;
        markArgumented = argumented;
        markUsageWritten = usageWritten;
    }

    void rewind()
    {
        generator.rewind();
        depth = markDepth;
        functioned = markFunctioned;
        argumented = markArgumented;
        usageWritten = markUsageWritten;
    }

    boolean functioned()
    {
        return functioned;
    }

    int rootAction(
        CharSequence name)
    {
        int action = ACTION_DROP;

        if (MEMBER_FUNCTION.contentEquals(name))
        {
            functioned = true;
            action = ACTION_FUNCTION;
        }
        else if (!MEMBER_ID.contentEquals(name) && !MEMBER_TYPE.contentEquals(name))
        {
            key.setLength(0);
            key.append(PREFIX).append(name);
            generator.writeKey(key);
            action = ACTION_COPY;
        }

        return action;
    }

    int functionAction(
        CharSequence name,
        JsonParserEx parser)
    {
        int action = ACTION_DROP;

        if (MEMBER_ARGUMENTS.contentEquals(name))
        {
            parser.escaped();
            generator.writeKey(MEMBER_ARGUMENTS);
            argumented = true;
            action = ACTION_UNESCAPE;
        }

        return action;
    }

    void closeFunction()
    {
        if (!argumented)
        {
            generator.writeStartObject(MEMBER_ARGUMENTS).writeEnd();
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

    void finish()
    {
        generator.writeEnd();
    }

    int flush(
        int room,
        boolean last)
    {
        final int pending = pending();
        final int length = Math.min(room, pending);
        final boolean fin = last && length == pending;

        if (length > 0 || fin)
        {
            if (separating && length > 0)
            {
                buffer.putByte(origin, separator);
                separating = false;
            }

            sink.put(buffer, origin + flushed, length, fin);
            flushed += length;

            if (flushed == staged())
            {
                carried = 0;
                flushed = 0;
                generator.wrap(buffer, origin, buffer.capacity());
            }
        }

        return length;
    }

    private void writeIncludeUsage()
    {
        generator.write(MEMBER_INCLUDE_USAGE, true);
        usageWritten = true;
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
        JsonStringView chars,
        boolean deferred)
    {
        final Completion completion = deferred ? Completion.INCOMPLETE : Completion.COMPLETE;
        final int length = chars.length();
        final int before = generator.consumed();

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

        fragmentTaken = generator.consumed() - before;

        if (fragmentTaken < length && !deferred)
        {
            throw new JsonException("insufficient room to stage the event");
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
