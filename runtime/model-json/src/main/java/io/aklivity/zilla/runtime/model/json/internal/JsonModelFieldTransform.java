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
package io.aklivity.zilla.runtime.model.json.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableDirectByteBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

// The native ModelTransform adapter for the JSON model: drives a wired ModelTransform inline as the
// document streams through, at every scalar field of the value, at any nesting depth -- not just the
// top-level fields ModelFieldBridge's observation-only fallback surfaces for a model with no native
// adapter of its own. A FIELD/REPLACED answer is written to the destination as it is decided (so a
// substituted value, or a field redirected to a differently-named sibling key, actually changes the output
// bytes); a DECLINED answer writes a JSON null placeholder instead of the original value.
//
// Path tracking: a single reused path buffer, rooted at "$", grows by ".name" entering a named object
// member and by "[index]" entering an array element, truncated back on that member's own close (container)
// or once resolved (scalar). Only the trailing segment of a REPLACED answer's own path may differ from the
// original -- redirecting a field to a sibling key of the same enclosing object -- since nothing here
// moves a value to a different parent or depth.
//
// Container-valued members (delta.tool_calls[] and the like) are forwarded unchanged, at any depth,
// without ever being offered to the wired transform: only scalar leaves (string/number/true/false/null)
// are fields in this adapter's sense, matching ModelSource's own scalar-oriented value rendering.
final class JsonModelFieldTransform implements JsonTransform
{
    private static final String ROOT_PATH = "$";

    private final ModelTransform transform;
    private final List<Frame> frames;
    private final StringBuilder path;
    private final StringBuilder pendingKey;
    private final StringBuilder scalarText;
    private final MutableDirectBufferEx valueBuffer;
    private final Field fieldSource;
    private final KeyText keyText;
    private final ValueText valueText;
    private final Mediator mediator;
    private final ModelBridge modelBridge;
    private final ModelControl modelControl;

    private JsonController upstream;
    private JsonSink downstream;
    private boolean downstreamVerbatim;
    private int depth;
    private int valueLength;
    private JsonEvent scalarKind;
    private JsonEvent writeEvent;
    private int mark;
    private WriteStep writeStep;

    JsonModelFieldTransform(
        ModelTransform transform)
    {
        this.transform = transform;
        this.frames = new ArrayList<>();
        this.path = new StringBuilder(64);
        this.pendingKey = new StringBuilder();
        this.scalarText = new StringBuilder();
        this.valueBuffer = new ExpandableDirectByteBufferEx();
        this.fieldSource = new Field();
        this.keyText = new KeyText();
        this.valueText = new ValueText();
        this.mediator = new Mediator();
        this.modelBridge = new ModelBridge();
        this.modelControl = new ModelControl();
        this.path.append(ROOT_PATH);
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
        downstream = sink;
        Status status;
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            status = onOpen(source, event, sink);
            break;
        case END_OBJECT:
        case END_ARRAY:
            status = onClose(source, event, sink);
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
            status = sink.transform(mediator, source, forward(event));
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
        downstream = sink;
        Status status;
        switch (writeStep)
        {
        case KEY:
            status = afterWrite(resumeKey(sink));
            break;
        case VALUE:
            status = afterWrite(sink.resume(valueText, valueText, writeEvent));
            break;
        case OPEN_KEY:
            status = resumeOpenKey(source, event, sink);
            break;
        default:
            status = sink.resume(mediator, source, event);
            break;
        }
        return status;
    }

    @Override
    public void reset()
    {
        frames.clear();
        path.setLength(0);
        path.append(ROOT_PATH);
        pendingKey.setLength(0);
        scalarText.setLength(0);
        downstreamVerbatim = false;
        depth = 0;
        valueLength = 0;
        mark = 0;
        writeStep = WriteStep.NONE;
    }

    private Status onOpen(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        boolean namedMember = depth > 0 && !frame(depth - 1).array;
        int enteredMark = enterMember();
        Frame frame = frame(depth);
        frame.array = event == JsonEvent.START_ARRAY;
        frame.index = -1;
        frame.mark = enteredMark;
        depth++;

        Status status;
        if (namedMember)
        {
            keyText.wrap(pendingKey);
            writeStep = WriteStep.OPEN_KEY;
            status = sink.transform(keyText, keyText, JsonEvent.KEY_NAME);
            if (status == Status.ADVANCED)
            {
                writeStep = WriteStep.NONE;
                status = sink.transform(mediator, source, forward(event));
            }
        }
        else
        {
            status = sink.transform(mediator, source, forward(event));
        }
        return status;
    }

    // resumes a container-valued member's deferred key write (see onOpen), then forwards the same
    // START_OBJECT/START_ARRAY source and event the pipeline replays into resume() per the suspended
    // transform() call -- this stage never stores that source/event itself, since JsonTransform's own
    // contract guarantees they come back unchanged
    private Status resumeOpenKey(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status = sink.resume(keyText, keyText, JsonEvent.KEY_NAME);
        if (status == Status.ADVANCED)
        {
            writeStep = WriteStep.NONE;
            status = sink.transform(mediator, source, forward(event));
        }
        return status;
    }

    private Status onClose(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        depth--;
        Frame frame = frame(depth);
        Status status = sink.transform(mediator, source, forward(event));
        path.setLength(frame.mark);
        return status;
    }

    private Status onKey(
        JsonController control,
        JsonSource source,
        JsonSink sink)
    {
        Status status;
        pendingKey.setLength(0);
        pendingKey.append(source.getStringView());
        if (source.deferredBytes())
        {
            control.consumed(0);
            status = Status.STARVED;
        }
        else
        {
            status = Status.ADVANCED;
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
        scalarText.setLength(0);
        switch (event)
        {
        case VALUE_TRUE:
            scalarText.append("true");
            break;
        case VALUE_FALSE:
            scalarText.append("false");
            break;
        case VALUE_NULL:
            scalarText.append("null");
            break;
        default:
            scalarText.append(source.getStringView());
            break;
        }
        valueLength = putUtf8(valueBuffer, scalarText);
        if (source.deferredBytes())
        {
            control.consumed(0);
            status = Status.STARVED;
        }
        else
        {
            mark = enterMember();
            scalarKind = event;
            fieldSource.wrap(path.toString(), valueBuffer, valueLength);
            ModelStatus answer = transform.transform(modelControl, fieldSource, ModelEvent.FIELD, modelBridge);
            status = afterWrite(modelBridge.lastWrite(answer));
        }
        return status;
    }

    private Status afterWrite(
        Status status)
    {
        if (status == Status.ADVANCED || status == Status.COMPLETED)
        {
            writeStep = WriteStep.NONE;
            path.setLength(mark);
        }
        return status;
    }

    private Status resumeKey(
        JsonSink sink)
    {
        Status status = sink.resume(keyText, keyText, JsonEvent.KEY_NAME);
        if (status == Status.ADVANCED)
        {
            writeStep = WriteStep.VALUE;
            status = sink.transform(valueText, valueText, writeEvent);
        }
        return status;
    }

    // appends the current member's own path segment (a named object member, or an array element's
    // index), returning the path length from before the append so the caller can roll back to it once
    // this member resolves (a scalar, immediately; a container, on its matching close)
    private int enterMember()
    {
        int enteredMark = path.length();
        if (depth > 0)
        {
            Frame parent = frame(depth - 1);
            if (parent.array)
            {
                parent.index++;
                path.append('[').append(parent.index).append(']');
            }
            else
            {
                path.append('.').append(pendingKey);
            }
        }
        return enteredMark;
    }

    private Frame frame(
        int level)
    {
        while (level >= frames.size())
        {
            frames.add(new Frame());
        }
        return frames.get(level);
    }

    // Re-asserts verbatim downstream: once the sink has opted in, a body event (not document framing or a
    // segment) is forwarded as VERBATIM so the sink copies the original bytes for events this stage never
    // substitutes (structural open/close) -- see Mediator for why substitutable events never reach here.
    private JsonEvent forward(
        JsonEvent event)
    {
        boolean body = event != JsonEvent.START_DOCUMENT && event != JsonEvent.END_DOCUMENT && !event.segmented();
        return downstreamVerbatim && body ? JsonEvent.VERBATIM : event;
    }

    private static int putUtf8(
        MutableDirectBufferEx buffer,
        CharSequence value)
    {
        int length = value.length();
        int index = 0;
        for (int i = 0; i < length; i++)
        {
            char c = value.charAt(i);
            if (c < 0x80)
            {
                buffer.putByte(index++, (byte) c);
            }
            else if (c < 0x800)
            {
                buffer.putByte(index++, (byte) (0xC0 | (c >> 6)));
                buffer.putByte(index++, (byte) (0x80 | (c & 0x3F)));
            }
            else if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(value.charAt(i + 1)))
            {
                int codePoint = Character.toCodePoint(c, value.charAt(++i));
                buffer.putByte(index++, (byte) (0xF0 | (codePoint >> 18)));
                buffer.putByte(index++, (byte) (0x80 | ((codePoint >> 12) & 0x3F)));
                buffer.putByte(index++, (byte) (0x80 | ((codePoint >> 6) & 0x3F)));
                buffer.putByte(index++, (byte) (0x80 | (codePoint & 0x3F)));
            }
            else if (Character.isSurrogate(c))
            {
                buffer.putByte(index++, (byte) '?');
            }
            else
            {
                buffer.putByte(index++, (byte) (0xE0 | (c >> 12)));
                buffer.putByte(index++, (byte) (0x80 | ((c >> 6) & 0x3F)));
                buffer.putByte(index++, (byte) (0x80 | (c & 0x3F)));
            }
        }
        return index;
    }

    // the key-write branch that calls this (see writeField/writeDeclined) is reached only for a named
    // object member -- a scalar array element writes its value with no key at all -- so the last segment
    // of any path reaching here is always a ".name" suffix, never a "[index]" one
    private static String lastSegment(
        String fieldPath)
    {
        return fieldPath.substring(fieldPath.lastIndexOf('.') + 1);
    }

    private static Status mapToJson(
        ModelStatus status)
    {
        return switch (status)
        {
        case OK -> Status.ADVANCED;
        case OVERFLOW -> Status.SUSPENDED;
        case UNDERFLOW -> Status.STARVED;
        case COMPLETE -> Status.COMPLETED;
        default -> Status.REJECTED;
        };
    }

    private static ModelStatus mapToModel(
        Status status)
    {
        return switch (status)
        {
        case ADVANCED -> ModelStatus.OK;
        case SUSPENDED -> ModelStatus.OVERFLOW;
        case STARVED -> ModelStatus.UNDERFLOW;
        case COMPLETED -> ModelStatus.COMPLETE;
        default -> ModelStatus.REJECTED;
        };
    }

    private enum WriteStep
    {
        NONE,
        KEY,
        VALUE,
        OPEN_KEY
    }

    private static final class Frame
    {
        private boolean array;
        private int index;
        private int mark;
    }

    // the value view a wired ModelTransform reads the current scalar field from, and the view a
    // transform's own substitute answer is expected to be shaped like (see Field, constructed by a
    // dialect's own ModelTransform to answer REPLACED)
    private static final class Field implements ModelSource
    {
        private String path;
        private final UnsafeBufferEx value;

        private Field()
        {
            this.value = new UnsafeBufferEx(new byte[0]);
        }

        private void wrap(
            String path,
            DirectBufferEx buffer,
            int length)
        {
            this.path = path;
            this.value.wrap(buffer, 0, length);
        }

        @Override
        public String getPath()
        {
            return path;
        }

        @Override
        public DirectBufferEx getValue()
        {
            return value;
        }
    }

    // a JsonSource carrying only text (an object key, or a scalar value re-typed per the original JSON
    // event kind) that this adapter itself decided to write -- every other accessor is unreachable from a
    // key or a already-decoded scalar re-emission, so each throws rather than silently returning nonsense.
    // Also its own JsonController for that same write: a bounded generator write reports back how much of
    // the text it actually consumed via consumed(int), so a SUSPENDED write's later resume() re-exposes
    // only the remainder through getStringView() rather than offering the whole text again from the start.
    private static class TextSource implements JsonSource, JsonController
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

    private static final class KeyText extends TextSource
    {
    }

    private static final class ValueText extends TextSource
    {
    }

    // the control this adapter passes to the wired ModelTransform: authorization is not meaningful within
    // one document's own field stream (the pipeline-level authorization already scopes the whole value), so
    // this only ever surfaces rejection, folding it into the JsonPipeline.Status this adapter already
    // reports for every other outcome
    private final class ModelControl implements ModelController
    {
        private boolean rejected;

        @Override
        public long authorization()
        {
            return 0L;
        }

        @Override
        public void reject(
            String diagnostic)
        {
            rejected = true;
        }
    }

    // Drives the actual write for a wired ModelTransform's FIELD/REPLACED/DECLINED answer into this
    // adapter's real downstream JsonSink, tracking the two-step (key, then value) write as its own small
    // resumable state machine so a SUSPENDED mid-write resumes at exactly the step that filled the output.
    private final class ModelBridge implements ModelSink
    {
        private Status written;

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event)
        {
            Status status = switch (event)
            {
            case FIELD -> writeUnchanged();
            case REPLACED -> writeReplaced(source);
            case DECLINED -> writeDeclined();
            default -> Status.ADVANCED;
            };
            written = status;
            return mapToModel(status);
        }

        @Override
        public boolean identity()
        {
            return false;
        }

        private Status lastWrite(
            ModelStatus answer)
        {
            return modelControl.rejected ? Status.REJECTED : written != null ? written : mapToJson(answer);
        }

        // FIELD means the value is kept as-is (see ModelEvent), so the original decoded text and key
        // already captured for this scalar are reused verbatim -- no path or value re-derivation, and no
        // allocation beyond what onScalar already did to offer the field to the wired transform
        private Status writeUnchanged()
        {
            keyText.wrap(pendingKey);
            valueText.wrap(scalarText);
            return writeKeyThenValue(scalarKind);
        }

        private Status writeReplaced(
            ModelSource answer)
        {
            String answerPath = answer.getPath();
            CharSequence key = answerPath != null ? lastSegment(answerPath) : pendingKey;
            DirectBufferEx value = answer.getValue();
            keyText.wrap(key);
            valueText.wrap(value.getStringWithoutLengthUtf8(0, value.capacity()));
            return writeKeyThenValue(scalarKind);
        }

        private Status writeKeyThenValue(
            JsonEvent valueEvent)
        {
            writeEvent = valueEvent;
            Status status;
            if (depth == 0 || frame(depth - 1).array)
            {
                writeStep = WriteStep.VALUE;
                status = downstream.transform(valueText, valueText, writeEvent);
            }
            else
            {
                writeStep = WriteStep.KEY;
                status = downstream.transform(keyText, keyText, JsonEvent.KEY_NAME);
                if (status == Status.ADVANCED)
                {
                    writeStep = WriteStep.VALUE;
                    status = downstream.transform(valueText, valueText, writeEvent);
                }
            }
            return status;
        }

        private Status writeDeclined()
        {
            keyText.wrap(pendingKey);
            return writeKeyThenValue(JsonEvent.VALUE_NULL);
        }
    }

    // Intercepts the downstream's byte-delivery opt-ins (segmentable, verbatim) rather than letting them
    // reach the upstream (which would substitute opaque segments or coalesced VERBATIM runs for the
    // structure this stage needs to read field-by-field), and re-asserts verbatim toward its own
    // downstream for the structural events it never substitutes.
    private final class Mediator implements JsonController
    {
        @Override
        public void segmentable()
        {
        }

        @Override
        public void verbatim()
        {
            downstreamVerbatim = true;
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            upstream.consumed(sourceBytes);
        }
    }
}
