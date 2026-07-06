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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.transform;

import java.util.Map;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Re-roots a {@code tools/call} request to its {@code arguments} object — suppressing the outer
 * {@code name}/{@code arguments} wrapper so the downstream projector sees {@code arguments} as the top-level
 * document — while capturing each top-level argument's scalar value as the bytes stream past, so a mediating
 * caller can interpolate the request path without materializing the whole request.
 * <p>
 * This is a mediating, structure-inspecting transform (it must see the {@code name}/{@code arguments}
 * wrapper's own {@code KEY_NAME} events, then every top-level argument's own {@code KEY_NAME}, to do its
 * job), sitting in front of a byte-preferring projector/sink chain — so, per the same mediating-transform
 * rule {@code common-json}'s {@code JsonSchemaImpl.Validator} follows, it cannot forward a downstream
 * {@link JsonController#segmentable()} request upstream unchanged: granting it would let the whole document
 * (or the whole {@code arguments} object) stream past as an opaque, structure-free run, and the
 * {@code KEY_NAME} events this class matches against would never be delivered at all.
 * {@code segmentable()} is always declined so the upstream keeps delivering structured events, and
 * {@link JsonController#verbatim()} is re-asserted upstream instead — the byte-preserving fidelity modifier
 * that rides alongside structured events rather than substituting for them, letting a large argument value
 * (e.g. a request body) still stream without a canonical re-render.
 */
public final class McpHttpArguments implements JsonTransform
{
    private final Map<String, String> captured;
    private final StringBuilder text = new StringBuilder();
    private final JsonController downstreamControl = new JsonController()
    {
        @Override
        public void segmentable()
        {
        }

        @Override
        public void verbatim()
        {
            upstream.verbatim();
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            upstream.consumed(sourceBytes);
        }
    };

    private JsonController upstream;
    private int depth;
    private boolean argsArmed;
    private boolean forwarding;
    private int forwardDepth;
    private String captureKey;

    public McpHttpArguments(
        Map<String, String> captured)
    {
        this.captured = captured;
    }

    @Override
    public void reset()
    {
        depth = 0;
        argsArmed = false;
        forwarding = false;
        forwardDepth = 0;
        captureKey = null;
        text.setLength(0);
    }

    @Override
    public boolean identity()
    {
        return true;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstream = control;
        return forwarding
            ? onForwarding(source, event, sink)
            : onWrapper(source, event, sink);
    }

    @Override
    public Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        upstream = control;
        return sink.resume(downstreamControl, source, event);
    }

    @Override
    public Status flush(
        JsonController control,
        JsonSource source,
        JsonSink sink)
    {
        upstream = control;
        return sink.flush(downstreamControl, source);
    }

    private Status onWrapper(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status = Status.ADVANCED;
        switch (event)
        {
        case START_DOCUMENT:
            status = forward(sink, source, event);
            break;
        case START_OBJECT:
            depth++;
            if (argsArmed)
            {
                argsArmed = false;
                forwarding = true;
                forwardDepth = 1;
                status = forward(sink, source, event);
            }
            break;
        case START_ARRAY:
            depth++;
            break;
        case KEY_NAME:
            argsArmed = depth == 1 && "arguments".contentEquals(source.getStringView());
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            break;
        default:
            break;
        }
        return status;
    }

    private Status onForwarding(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status;
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            forwardDepth++;
            captureKey = null;
            status = forward(sink, source, event);
            break;
        case END_OBJECT:
        case END_ARRAY:
            forwardDepth--;
            status = forward(sink, source, event);
            if (forwardDepth == 0)
            {
                forwarding = false;
            }
            break;
        case KEY_NAME:
            captureKey = forwardDepth == 1 ? source.getStringView().toString() : null;
            text.setLength(0);
            status = forward(sink, source, event);
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
            if (captureKey != null)
            {
                text.append(source.getStringView());
                if (!source.deferredBytes())
                {
                    captured.put(captureKey, text.toString());
                    text.setLength(0);
                    captureKey = null;
                }
            }
            status = forward(sink, source, event);
            break;
        case VALUE_TRUE:
            if (captureKey != null)
            {
                captured.put(captureKey, "true");
                captureKey = null;
            }
            status = forward(sink, source, event);
            break;
        case VALUE_FALSE:
            if (captureKey != null)
            {
                captured.put(captureKey, "false");
                captureKey = null;
            }
            status = forward(sink, source, event);
            break;
        case VERBATIM:
            // rides alongside the structured event stream for the same value rather than substituting for
            // it (see the class Javadoc), so it must not disturb an in-progress capture
            status = forward(sink, source, event);
            break;
        default:
            captureKey = null;
            status = forward(sink, source, event);
            break;
        }
        return status;
    }

    private Status forward(
        JsonSink sink,
        JsonSource source,
        JsonEvent event)
    {
        return sink.transform(downstreamControl, source, event);
    }
}
