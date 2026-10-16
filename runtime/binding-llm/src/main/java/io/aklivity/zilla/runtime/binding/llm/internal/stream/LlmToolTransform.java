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

import java.math.BigDecimal;

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

abstract class LlmToolTransform implements JsonTransform
{
    protected static final int DROP = 0;
    protected static final int REJECT = 1;
    protected static final int DESCEND = 2;
    protected static final int PASS_STRING = 3;
    protected static final int PASS_STRING_OR_DROP = 4;
    protected static final int PASS_OBJECT = 5;
    protected static final int HOLD_STRING = 6;
    protected static final int UNESCAPE_OBJECT = 7;
    protected static final int ESCAPE_OBJECT = 8;
    protected static final int DESCEND_KEPT = 9;
    protected static final int PASS_VALUE = 10;

    protected static final int MODE_OBJECT = 0;
    protected static final int MODE_EXTRACT = 1;
    protected static final int MODE_VALUE = 2;

    private static final int NONE = -1;
    private static final int QUEUE_CAPACITY = 16;
    private static final int HELD_MAX = 4096;

    protected String target;

    private final int mode;
    private final JsonEvent[] queueEvents;
    private final String[] queueTexts;
    private final boolean[] queueSources;
    private final LiteralSource literalSource;
    private final LiteralControl literalControl;
    private final StringBuilder heldBuilder;

    private JsonController control;
    private JsonSource source;
    private JsonSink sink;
    private int queueHead;
    private int queueTail;
    private int depth;
    private int pending;
    private boolean passing;
    private boolean passEscaped;
    private int passDepth;
    private boolean skipping;
    private int skipDepth;
    private boolean holding;
    private boolean awaitingContent;
    private boolean valueStarted;
    private String held;

    protected LlmToolTransform()
    {
        this(MODE_OBJECT);
    }

    protected LlmToolTransform(
        int mode)
    {
        this.mode = mode;
        this.queueEvents = new JsonEvent[QUEUE_CAPACITY];
        this.queueTexts = new String[QUEUE_CAPACITY];
        this.queueSources = new boolean[QUEUE_CAPACITY];
        this.literalSource = new LiteralSource();
        this.literalControl = new LiteralControl();
        this.heldBuilder = new StringBuilder();
        this.pending = NONE;
    }

    protected abstract int member(
        int depth,
        CharSequence key);

    protected abstract void onOpen();

    protected abstract Status onClose(
        int depth);

    protected void onBegin()
    {
    }

    protected Status onEnd()
    {
        return Status.ADVANCED;
    }

    protected final String attribute(
        String name)
    {
        final DirectBufferEx value = control.envelope().get(name, 0);

        return value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
    }

    protected final String held()
    {
        return held;
    }

    protected final void pass(
        JsonEvent event)
    {
        enqueue(event, null, true);
    }

    protected final void emit(
        JsonEvent event)
    {
        enqueue(event, null, false);
    }

    protected final void emit(
        JsonEvent event,
        String text)
    {
        enqueue(event, text, false);
    }

    protected final int prefix(
        String dialect,
        CharSequence key)
    {
        target = dialect + ":" + key;

        return PASS_VALUE;
    }

    protected final int unprefix(
        String dialect,
        CharSequence key)
    {
        final int length = key.length();
        int colon = 0;

        while (colon < length && key.charAt(colon) != ':')
        {
            colon++;
        }

        int action = PASS_VALUE;

        if (colon == length)
        {
            target = key.toString();
        }
        else if (matches(key.subSequence(0, colon), dialect))
        {
            target = key.subSequence(colon + 1, length).toString();
        }
        else
        {
            action = DROP;
        }

        return action;
    }

    protected static boolean matches(
        CharSequence value,
        String expected)
    {
        final int length = expected.length();
        boolean matches = value.length() == length;

        for (int index = 0; matches && index < length; index++)
        {
            matches = value.charAt(index) == expected.charAt(index);
        }

        return matches;
    }

    @Override
    public boolean deterministic()
    {
        return true;
    }

    @Override
    public void reset()
    {
        queueHead = 0;
        queueTail = 0;
        depth = 0;
        pending = NONE;
        passing = false;
        passEscaped = false;
        skipping = false;
        holding = false;
        awaitingContent = false;
        valueStarted = false;
        held = null;
        heldBuilder.setLength(0);
    }

    @Override
    public final Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        this.control = control;
        this.source = source;
        this.sink = sink;

        Status status = Status.ADVANCED;

        switch (event)
        {
        case START_DOCUMENT:
            pass(event);
            break;
        case END_DOCUMENT:
            status = mode == MODE_VALUE && !valueStarted ? Status.REJECTED : Status.ADVANCED;
            pass(event);
            break;
        case KEY_NAME:
            status = onKey();
            break;
        case START_ESCAPED:
        case END_ESCAPED:
            status = onEscaped(event);
            break;
        default:
            status = onValue(event);
            break;
        }

        return status == Status.ADVANCED ? drain() : status;
    }

    @Override
    public final Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        this.control = control;
        this.source = source;
        this.sink = sink;

        Status status = Status.ADVANCED;

        if (queueHead < queueTail)
        {
            status = queueSources[queueHead]
                ? sink.resume(control, source, event)
                : sink.resume(literalControl, literalSource, queueEvents[queueHead]);

            if (status != Status.SUSPENDED)
            {
                queueHead++;
            }

            if (status == Status.ADVANCED)
            {
                status = drain();
            }
        }

        return status;
    }

    private Status onKey()
    {
        Status status = Status.ADVANCED;

        if (passing)
        {
            pass(JsonEvent.KEY_NAME);
        }
        else if (!skipping && !source.deferredBytes())
        {
            pending = member(depth, source.getStringView());

            if (pending == REJECT)
            {
                pending = NONE;
                status = Status.REJECTED;
            }
            else if (pending == UNESCAPE_OBJECT)
            {
                control.escaped();
            }
        }

        return status;
    }

    private Status onEscaped(
        JsonEvent event)
    {
        Status status = Status.ADVANCED;

        if (event == JsonEvent.START_ESCAPED)
        {
            if (pending == UNESCAPE_OBJECT)
            {
                pending = NONE;
                awaitingContent = true;

                if (mode != MODE_EXTRACT)
                {
                    emit(JsonEvent.KEY_NAME, target);
                }
            }
            else
            {
                status = Status.REJECTED;
            }
        }
        else if (awaitingContent)
        {
            awaitingContent = false;
            emit(JsonEvent.START_OBJECT);
            emit(JsonEvent.END_OBJECT);
        }

        return status;
    }

    private Status onValue(
        JsonEvent event)
    {
        final Status status;

        if (skipping)
        {
            status = skip(event);
        }
        else if (awaitingContent)
        {
            awaitingContent = false;
            status = event == JsonEvent.START_OBJECT ? startPass(event) : Status.REJECTED;
        }
        else if (mode == MODE_VALUE && !valueStarted)
        {
            valueStarted = true;
            onBegin();
            status = event == JsonEvent.START_OBJECT ? startPass(event) : Status.REJECTED;
        }
        else if (pending != NONE)
        {
            status = begin(event);
        }
        else if (holding)
        {
            status = hold();
        }
        else if (passing)
        {
            status = carry(event);
        }
        else
        {
            status = structure(event);
        }

        return status;
    }

    private Status structure(
        JsonEvent event)
    {
        Status status = Status.REJECTED;

        if (event == JsonEvent.START_OBJECT && depth == 0)
        {
            if (mode != MODE_EXTRACT)
            {
                pass(event);
            }

            depth++;
            onOpen();
            status = Status.ADVANCED;
        }
        else if (event == JsonEvent.END_OBJECT && depth > 0)
        {
            status = onClose(depth);
            depth--;
        }

        return status;
    }

    private Status begin(
        JsonEvent event)
    {
        final int action = pending;
        pending = NONE;

        Status status = Status.ADVANCED;

        switch (action)
        {
        case DESCEND:
            if (event == JsonEvent.START_OBJECT)
            {
                depth++;
            }
            else
            {
                status = Status.REJECTED;
            }
            break;
        case PASS_VALUE:
            status = beginPass(event);
            break;
        case DESCEND_KEPT:
            if (event == JsonEvent.START_OBJECT)
            {
                emit(JsonEvent.KEY_NAME, target);
                pass(event);
                depth++;
            }
            else
            {
                status = Status.REJECTED;
            }
            break;
        case PASS_STRING:
            status = event == JsonEvent.VALUE_STRING ? beginPass(event) : Status.REJECTED;
            break;
        case PASS_STRING_OR_DROP:
            status = event == JsonEvent.VALUE_STRING ? beginPass(event) : beginSkip(event);
            break;
        case PASS_OBJECT:
            status = event == JsonEvent.START_OBJECT ? beginPass(event) : Status.REJECTED;
            break;
        case HOLD_STRING:
            status = event == JsonEvent.VALUE_STRING ? hold() : Status.REJECTED;
            break;
        case ESCAPE_OBJECT:
            if (event == JsonEvent.START_OBJECT)
            {
                emit(JsonEvent.KEY_NAME, target);
                emit(JsonEvent.START_ESCAPED);
                passEscaped = true;
                status = startPass(event);
            }
            else
            {
                status = Status.REJECTED;
            }
            break;
        default:
            status = beginSkip(event);
            break;
        }

        return status;
    }

    private Status beginPass(
        JsonEvent event)
    {
        if (mode != MODE_EXTRACT)
        {
            emit(JsonEvent.KEY_NAME, target);
        }

        return startPass(event);
    }

    private Status startPass(
        JsonEvent event)
    {
        pass(event);
        passDepth = depth;

        if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
        {
            passing = true;
            depth++;
        }
        else
        {
            passing = source.deferredBytes();
        }

        return Status.ADVANCED;
    }

    private Status carry(
        JsonEvent event)
    {
        pass(event);

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            passing = depth != passDepth;
            break;
        default:
            passing = source.deferredBytes() || depth != passDepth;
            break;
        }

        return finishPass();
    }

    private Status finishPass()
    {
        Status status = Status.ADVANCED;

        if (!passing && passEscaped)
        {
            passEscaped = false;
            emit(JsonEvent.END_ESCAPED);
        }

        if (!passing && mode == MODE_VALUE)
        {
            status = onEnd();
        }

        return status;
    }

    private Status beginSkip(
        JsonEvent event)
    {
        Status status = Status.ADVANCED;

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            skipping = true;
            skipDepth = depth;
            depth++;
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
            if (source.deferredBytes())
            {
                skipping = true;
                skipDepth = depth;
                status = drop();
            }
            break;
        default:
            break;
        }

        return status;
    }

    private Status skip(
        JsonEvent event)
    {
        Status status = Status.ADVANCED;

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            skipping = depth != skipDepth;
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
            if (source.deferredBytes())
            {
                status = drop();
            }
            else
            {
                skipping = depth != skipDepth;
            }
            break;
        default:
            skipping = depth != skipDepth;
            break;
        }

        return status;
    }

    private Status drop()
    {
        control.consumed(source.getStringView().length());

        return Status.STARVED;
    }

    private Status hold()
    {
        final CharSequence view = source.getStringView();

        heldBuilder.append(view);
        control.consumed(view.length());

        Status status = Status.REJECTED;

        if (heldBuilder.length() <= HELD_MAX)
        {
            holding = source.deferredBytes();
            status = Status.STARVED;

            if (!holding)
            {
                held = heldBuilder.toString();
                heldBuilder.setLength(0);
                status = Status.ADVANCED;
            }
        }

        return status;
    }

    private void enqueue(
        JsonEvent event,
        String text,
        boolean fromSource)
    {
        queueEvents[queueTail] = event;
        queueTexts[queueTail] = text;
        queueSources[queueTail] = fromSource;
        queueTail++;
    }

    private Status drain()
    {
        Status status = Status.ADVANCED;

        while (status == Status.ADVANCED && queueHead < queueTail)
        {
            status = queueSources[queueHead]
                ? sink.transform(control, source, queueEvents[queueHead])
                : sink.transform(literalControl, literalSource.with(queueTexts[queueHead]), queueEvents[queueHead]);

            if (status != Status.SUSPENDED)
            {
                queueHead++;
            }
        }

        if (queueHead == queueTail)
        {
            queueHead = 0;
            queueTail = 0;
        }

        return status;
    }

    private final class LiteralControl implements JsonController
    {
        @Override
        public void segmentable()
        {
        }

        @Override
        public long authorization()
        {
            return control.authorization();
        }

        @Override
        public JsonEnvelope envelope()
        {
            return control.envelope();
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            literalSource.advance(sourceBytes);
        }
    }

    private static final class LiteralSource implements JsonSource
    {
        private final LiteralView view;

        private LiteralSource()
        {
            this.view = new LiteralView();
        }

        private LiteralSource with(
            String text)
        {
            view.text = text != null ? text : "";
            view.offset = 0;

            return this;
        }

        private void advance(
            int consumed)
        {
            view.offset += consumed;
        }

        @Override
        public String getString()
        {
            return view.toString();
        }

        @Override
        public CharSequence getStringView()
        {
            return view;
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIntegralNumber()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonLocation getLocation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectBufferEx getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonVerbatim getVerbatim(
            int limit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void skipValue()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deferredBytes()
        {
            return false;
        }
    }

    private static final class LiteralView implements CharSequence
    {
        private String text;
        private int offset;

        @Override
        public int length()
        {
            return text.length() - offset;
        }

        @Override
        public char charAt(
            int index)
        {
            return text.charAt(offset + index);
        }

        @Override
        public CharSequence subSequence(
            int start,
            int end)
        {
            return text.substring(offset + start, offset + end);
        }

        @Override
        public String toString()
        {
            return text.substring(offset);
        }
    }
}
