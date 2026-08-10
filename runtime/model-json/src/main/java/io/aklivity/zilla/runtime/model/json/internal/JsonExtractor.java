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

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableDirectByteBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

// Transparent pipeline stage that forwards every event unchanged while capturing the value of every
// top-level field as a side-effect, making it available to the converter after the value completes. Capture
// is char-view based, so it sees the decoded scalar (string content or number lexeme).
final class JsonExtractor implements JsonTransform
{
    // every distinct field name ever seen on this extractor, keyed by name and never shrunk: a Field's
    // name/path/value buffer are all allocated exactly once (see supplyField) and reused across every
    // later document that has a field of that name, however many documents that extractor lives across
    private final List<Field> fields;
    // fields captured for the in-flight document, in encounter order; cleared (not reallocated) on reset()
    private final List<Field> captured;
    private final JsonController mediator;
    private final StringBuilder pendingKey;

    private JsonController upstreamControl;
    private boolean downstreamVerbatim;
    private int depth;
    private boolean armed;

    JsonExtractor()
    {
        this.fields = new ArrayList<>();
        this.captured = new ArrayList<>();
        this.mediator = new Mediator();
        this.pendingKey = new StringBuilder();
    }

    int captured()
    {
        return captured.size();
    }

    // pre-joined "$." + name, computed once per distinct field name (see supplyField) rather than on
    // every capture, so a caller building a JSON pointer path never pays a per-call concatenation
    String path(
        int index)
    {
        return captured.get(index).path;
    }

    // Extraction reads the decoded structured events, so this stage must keep receiving them: it intercepts the
    // downstream's byte-delivery opt-ins (segmentable, verbatim) rather than letting them reach the upstream
    // validator (which would substitute opaque segments or coalesced VERBATIM runs for the structure), and
    // re-asserts verbatim toward its own downstream so the terminal sink still reproduces the original bytes.
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
            upstreamControl.consumed(sourceBytes);
        }
    }

    int length(
        int index)
    {
        return captured.get(index).length;
    }

    DirectBufferEx value(
        int index)
    {
        return captured.get(index).value;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstreamControl = control;
        // observe the structured event (the upstream keeps delivering it because this stage intercepts the
        // byte-delivery opt-ins) before re-asserting verbatim toward the sink
        observe(source, event);
        return sink.transform(mediator, source, forward(event));
    }

    @Override
    public Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstreamControl = control;
        return sink.resume(mediator, source, forward(event));
    }

    @Override
    public Status flush(
        JsonController control,
        JsonSource source,
        JsonSink sink)
    {
        upstreamControl = control;
        return sink.flush(mediator, source);
    }

    @Override
    public void reset()
    {
        depth = 0;
        armed = false;
        captured.clear();
        downstreamVerbatim = false;
    }

    @Override
    public boolean identity()
    {
        return true;
    }

    // Re-asserts verbatim downstream: once the sink has opted in, a body event (scalar, key, or structural —
    // not document framing or a segment) is forwarded as a VERBATIM event so the sink copies the original
    // bytes; the structured event was already observed above for extraction.
    private JsonEvent forward(
        JsonEvent event)
    {
        boolean body = event != JsonEvent.START_DOCUMENT && event != JsonEvent.END_DOCUMENT && !event.segmented();
        return downstreamVerbatim && body ? JsonEvent.VERBATIM : event;
    }

    private void observe(
        JsonSource source,
        JsonEvent event)
    {
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            armed = false;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            armed = false;
            break;
        case KEY_NAME:
            armed = depth == 1;
            if (armed)
            {
                pendingKey.setLength(0);
                pendingKey.append(source.getStringView());
            }
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
            if (depth == 1 && armed)
            {
                capture(pendingKey, source.getStringView());
            }
            armed = false;
            break;
        default:
            armed = false;
            break;
        }
    }

    // surfaces only scalar top-level fields: a field is captured on its scalar value, so a top-level key whose
    // value is an object or array is never counted
    private void capture(
        CharSequence key,
        CharSequence view)
    {
        Field field = supplyField(key);
        field.length = putUtf8(field.value, view);
        // a duplicate top-level key within one document overwrites the value in place rather than
        // appearing twice in capture order
        if (!captured.contains(field))
        {
            captured.add(field);
        }
    }

    // encodes UTF-8 bytes straight from the decoded char view into the field's reused buffer, matching
    // CharSequence.toString().getBytes(UTF_8) byte-for-byte (including the '?' replacement for an unpaired
    // surrogate) without materializing an intermediate String or byte[] on every capture
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

    // searches every field name ever seen on this extractor (not just this document's), so a name that
    // recurs across documents — the common case, since the same schema shapes every message on a stream —
    // reuses its Field (and thus its name/path Strings) instead of reallocating them every call
    private Field supplyField(
        CharSequence key)
    {
        Field result = null;
        for (int i = 0; result == null && i < fields.size(); i++)
        {
            if (charsEqual(fields.get(i).name, key))
            {
                result = fields.get(i);
            }
        }
        if (result == null)
        {
            result = new Field();
            result.name = key.toString();
            result.path = "$." + result.name;
            fields.add(result);
        }
        return result;
    }

    private static boolean charsEqual(
        String name,
        CharSequence key)
    {
        boolean matches = name.length() == key.length();
        for (int i = 0; matches && i < name.length(); i++)
        {
            matches = name.charAt(i) == key.charAt(i);
        }
        return matches;
    }

    private static final class Field
    {
        private final MutableDirectBufferEx value;

        private String name;
        private String path;
        private int length;

        private Field()
        {
            this.value = new ExpandableDirectByteBufferEx();
        }
    }
}
