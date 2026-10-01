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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import java.math.BigDecimal;

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

/**
 * Intercepts a request document's top-level (depth-1, direct child of the root object) scalar members,
 * offering each one to {@link #rename(CharSequence)} for a possible key substitution and to
 * {@link #onValue(CharSequence, JsonSource, JsonEvent)} as a side-effect observation, before forwarding the
 * (possibly renamed) member unchanged otherwise. Every other event -- a top-level member whose value is a
 * container, any event at any other depth, and document framing -- is forwarded verbatim.
 * <p>
 * This is the generalized replacement for the engine's {@code ModelEvent.REPLACED} path-substitution idiom
 * ({@code runtime/model-json}'s {@code JsonModelFieldTransform}), simplified because a request-side rename
 * only ever concerns a depth-1 scalar: a key is captured on {@code KEY_NAME}, its write deferred until the
 * paired value (or container) event decides whether it was a rename candidate at all.
 * </p>
 */
public abstract class LlmRequestFieldTransform implements JsonTransform
{
    private final StringBuilder pendingKey;
    private final KeyText keySource;
    private final Mediator mediator;

    private JsonController upstream;
    private int depth;
    private boolean keyPending;
    private WriteStep writeStep;

    protected LlmRequestFieldTransform()
    {
        this.pendingKey = new StringBuilder();
        this.keySource = new KeyText();
        this.mediator = new Mediator();
        this.writeStep = WriteStep.NONE;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstream = control;
        Status status;
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            status = onOpen(control, source, event, sink);
            break;
        case END_OBJECT:
        case END_ARRAY:
            status = onClose(control, source, event, sink);
            break;
        case KEY_NAME:
            status = onKey(control, source, sink);
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
        case VALUE_TRUE:
        case VALUE_FALSE:
        case VALUE_NULL:
            status = onScalar(control, source, event, sink);
            break;
        default:
            status = sink.transform(mediator, source, event);
            break;
        }
        return status;
    }

    @Override
    public Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstream = control;
        Status status;
        switch (writeStep)
        {
        case OPEN_KEY:
            status = sink.resume(keySource, keySource, JsonEvent.KEY_NAME);
            if (status == Status.ADVANCED)
            {
                writeStep = WriteStep.NONE;
                status = sink.transform(mediator, source, event);
            }
            break;
        case KEY:
            status = sink.resume(keySource, keySource, JsonEvent.KEY_NAME);
            if (status == Status.ADVANCED)
            {
                writeStep = WriteStep.VALUE;
                status = sink.transform(mediator, source, event);
            }
            break;
        case VALUE:
            status = sink.resume(mediator, source, event);
            break;
        default:
            status = sink.resume(mediator, source, event);
            break;
        }

        if (status == Status.ADVANCED || status == Status.COMPLETED)
        {
            writeStep = WriteStep.NONE;
        }
        return status;
    }

    @Override
    public void reset()
    {
        depth = 0;
        pendingKey.setLength(0);
        keyPending = false;
        writeStep = WriteStep.NONE;
    }

    /**
     * Returns the renamed key text for {@code key}, or {@code null} when this key is not a rename
     * candidate (forwarded unchanged).
     */
    protected abstract String rename(
        CharSequence key);

    /**
     * Observes a top-level scalar member's key and value, before the rename decision is applied. Called
     * for every top-level scalar, not only ones this transform renames.
     */
    protected abstract void onValue(
        CharSequence key,
        JsonSource source,
        JsonEvent event);

    private Status onOpen(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status;
        if (depth == 1 && keyPending)
        {
            keyPending = false;
            keySource.wrap(pendingKey);
            writeStep = WriteStep.OPEN_KEY;
            status = sink.transform(keySource, keySource, JsonEvent.KEY_NAME);
            if (status == Status.ADVANCED)
            {
                writeStep = WriteStep.NONE;
                status = sink.transform(mediator, source, event);
            }
        }
        else
        {
            status = sink.transform(mediator, source, event);
        }
        depth++;
        return status;
    }

    private Status onClose(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        depth--;
        return sink.transform(mediator, source, event);
    }

    private Status onKey(
        JsonController control,
        JsonSource source,
        JsonSink sink)
    {
        Status status;
        if (depth == 1)
        {
            pendingKey.setLength(0);
            pendingKey.append(source.getStringView());
            if (source.deferredBytes())
            {
                control.consumed(0);
                status = Status.STARVED;
            }
            else
            {
                keyPending = true;
                status = Status.ADVANCED;
            }
        }
        else
        {
            status = sink.transform(mediator, source, JsonEvent.KEY_NAME);
        }
        return status;
    }

    private Status onScalar(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status;
        if (depth == 1 && keyPending)
        {
            if (source.deferredBytes())
            {
                control.consumed(0);
                status = Status.STARVED;
            }
            else
            {
                keyPending = false;
                onValue(pendingKey, source, event);
                String toKey = rename(pendingKey);
                keySource.wrap(toKey != null ? toKey : pendingKey);
                writeStep = WriteStep.KEY;
                status = sink.transform(keySource, keySource, JsonEvent.KEY_NAME);
                if (status == Status.ADVANCED)
                {
                    writeStep = WriteStep.VALUE;
                    status = sink.transform(mediator, source, event);
                }
            }
        }
        else
        {
            status = sink.transform(mediator, source, event);
        }

        if (status == Status.ADVANCED || status == Status.COMPLETED)
        {
            writeStep = WriteStep.NONE;
        }
        return status;
    }

    private enum WriteStep
    {
        NONE,
        OPEN_KEY,
        KEY,
        VALUE
    }

    // a JsonSource carrying only an object key this transform decided to write, paired with its own
    // JsonController so a bounded generator write can report back how much of the key text it consumed --
    // mirrors runtime/model-json's JsonModelFieldTransform.TextSource/KeyText
    private static final class KeyText implements JsonSource, JsonController
    {
        private CharSequence text;
        private int progress;

        void wrap(
            CharSequence text)
        {
            this.text = text;
            this.progress = 0;
        }

        @Override
        public void segmentable()
        {
        }

        @Override
        public void consumed(
            int sourceChars)
        {
            progress += sourceChars;
        }

        @Override
        public String getString()
        {
            return getStringView().toString();
        }

        @Override
        public CharSequence getStringView()
        {
            return text.subSequence(progress, text.length());
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
            return null;
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

    // Intercepts the downstream's byte-delivery opt-in (segmentable) rather than letting it reach the
    // upstream (which would substitute opaque segments for the structure this stage needs to read
    // field-by-field at depth 1) -- mirrors runtime/model-json's JsonModelFieldTransform.Mediator.
    private final class Mediator implements JsonController
    {
        @Override
        public void segmentable()
        {
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            upstream.consumed(sourceBytes);
        }
    }
}
