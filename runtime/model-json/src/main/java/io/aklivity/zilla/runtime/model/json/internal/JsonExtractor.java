/*
 * Copyright 2021-2024 Aklivity Inc
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

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

// Transparent pipeline stage that forwards every event unchanged while capturing the value of each
// registered top-level field as a side-effect, making it available to the converter after the value
// completes. Capture is char-view based, so it sees the decoded scalar (string content or number lexeme).
final class JsonExtractor implements JsonTransform
{
    private final List<Field> fields;
    private final JsonController mediator;

    private JsonController upstreamControl;
    private boolean downstreamVerbatim;
    private int depth;
    private int armed;

    JsonExtractor()
    {
        this.fields = new ArrayList<>();
        this.mediator = new Mediator();
        this.armed = -1;
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

    void register(
        String name)
    {
        if (find(name) == null)
        {
            fields.add(new Field(name));
        }
    }

    int length(
        String name)
    {
        Field field = find(name);
        return field != null ? field.length : 0;
    }

    DirectBuffer value(
        String name)
    {
        Field field = find(name);
        return field != null ? field.value : null;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstreamControl = control;
        if (!fields.isEmpty())
        {
            // observe the structured event (the upstream keeps delivering it because this stage intercepts the
            // byte-delivery opt-ins) before re-asserting verbatim toward the sink
            observe(source, event);
        }
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
        armed = -1;
        downstreamVerbatim = false;
        for (int i = 0; i < fields.size(); i++)
        {
            fields.get(i).length = 0;
        }
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
            armed = -1;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            armed = -1;
            break;
        case KEY_NAME:
            armed = depth == 1 ? indexOf(source.getStringView()) : -1;
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
            if (depth == 1 && armed >= 0)
            {
                capture(armed, source.getStringView());
            }
            armed = -1;
            break;
        default:
            armed = -1;
            break;
        }
    }

    private void capture(
        int index,
        CharSequence view)
    {
        Field field = fields.get(index);
        field.length = field.value.putStringWithoutLengthUtf8(0, view.toString());
    }

    private int indexOf(
        CharSequence key)
    {
        int match = -1;
        for (int i = 0; match < 0 && i < fields.size(); i++)
        {
            if (charsEqual(fields.get(i).name, key))
            {
                match = i;
            }
        }
        return match;
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

    private Field find(
        String name)
    {
        Field result = null;
        for (int i = 0; result == null && i < fields.size(); i++)
        {
            if (fields.get(i).name.equals(name))
            {
                result = fields.get(i);
            }
        }
        return result;
    }

    private static final class Field
    {
        private final String name;
        private final MutableDirectBuffer value;

        private int length;

        private Field(
            String name)
        {
            this.name = name;
            this.value = new ExpandableDirectByteBuffer();
        }
    }
}
