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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Validates, on decode only, that an Anthropic streaming response event's own top-level {@code type} field
 * (e.g. {@code "content_block_delta"}) agrees with the SSE {@code event:} name the framing layer captured
 * for it, stashed under {@code event} in the supplied {@link JsonEnvelope} by whichever caller drives the
 * content decoder (mirroring how {@code model} is captured from a request field). A mismatch rejects the
 * value -- a well-behaved backend never sends one, so this only ever fires against a malformed or
 * malicious upstream, which is exactly the boundary this decode direction sits on.
 * <p>
 * This is a pure validation stage: every field, including {@code type} itself, is forwarded unchanged; no
 * field is ever renamed here. Used only by {@link LlmAnthropicDialect#supplyDecoder(LlmDialect.Kind,
 * JsonEnvelope)} for {@link LlmDialect.Kind#RESPONSE} on a route with no genuine streaming decode/encode
 * pair of its own (see {@code internal.mapper.LlmResponseTransformFactory}) -- the real cross-dialect
 * translation path re-derives its own event dispatch directly from the SSE event name (see
 * {@code internal.mapper.LlmAnthropicDecodeTransform}), so this guard exists specifically for the
 * schema-validate-and-passthrough fallback a third-party or test-only target dialect takes.
 * </p>
 */
final class LlmAnthropicResponseTypeTransform implements JsonTransform
{
    private static final String TYPE_KEY = "type";
    private static final String EVENT_NAME = "event";

    private final JsonEnvelope envelope;
    private final Mediator mediator;

    private JsonController upstream;
    private int depth;
    private boolean typePending;

    LlmAnthropicResponseTypeTransform(
        JsonEnvelope envelope)
    {
        this.envelope = envelope;
        this.mediator = new Mediator();
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
            depth++;
            status = sink.transform(mediator, source, event);
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            status = sink.transform(mediator, source, event);
            break;
        case KEY_NAME:
            status = onKey(control, source, sink, event);
            break;
        case VALUE_STRING:
            status = onValueString(control, source, sink, event);
            break;
        default:
            typePending = false;
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
        return sink.resume(mediator, source, event);
    }

    @Override
    public void reset()
    {
        depth = 0;
        typePending = false;
    }

    @Override
    public boolean identity()
    {
        return true;
    }

    private Status onKey(
        JsonController control,
        JsonSource source,
        JsonSink sink,
        JsonEvent event)
    {
        Status status;
        if (source.deferredBytes())
        {
            control.consumed(0);
            status = Status.STARVED;
        }
        else
        {
            typePending = depth == 1 && contentEquals(source.getStringView(), TYPE_KEY);
            status = sink.transform(mediator, source, event);
        }
        return status;
    }

    private Status onValueString(
        JsonController control,
        JsonSource source,
        JsonSink sink,
        JsonEvent event)
    {
        Status status;
        if (typePending && source.deferredBytes())
        {
            control.consumed(0);
            status = Status.STARVED;
        }
        else if (typePending)
        {
            typePending = false;
            status = matchesEvent(source) ? sink.transform(mediator, source, event) : Status.REJECTED;
        }
        else
        {
            status = sink.transform(mediator, source, event);
        }
        return status;
    }

    private boolean matchesEvent(
        JsonSource source)
    {
        int eventCount = envelope.count(EVENT_NAME);
        boolean matches = true;
        if (eventCount > 0)
        {
            DirectBufferEx eventValue = envelope.get(EVENT_NAME, eventCount - 1);
            CharSequence type = source.getStringView();
            matches = eventValue != null && contentEquals(type, eventValue);
        }
        return matches;
    }

    private static boolean contentEquals(
        CharSequence text,
        String value)
    {
        boolean matches = text.length() == value.length();
        for (int i = 0; matches && i < value.length(); i++)
        {
            matches = text.charAt(i) == value.charAt(i);
        }
        return matches;
    }

    private static boolean contentEquals(
        CharSequence text,
        DirectBufferEx value)
    {
        boolean matches = text.length() == value.capacity();
        for (int i = 0; matches && i < text.length(); i++)
        {
            matches = text.charAt(i) == (char) value.getByte(i);
        }
        return matches;
    }

    // Intercepts the downstream's byte-delivery opt-in (segmentable) rather than letting it reach the
    // upstream, since this stage needs genuine structured events to read the top-level type key.
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
