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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.stream;

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
 * document — while capturing each top-level argument's scalar string value as the bytes stream past, so a
 * mediating caller can interpolate the request path without materializing the whole request. Segment demand
 * raised by the downstream projector is propagated to the upstream parser, letting a large argument value
 * (e.g. a request body) stream verbatim across input frames.
 */
final class McpHttpArguments implements JsonTransform
{
    private final Map<String, String> captured;
    private final JsonController downstreamControl = this::onDownstreamSegmentable;

    private JsonController upstream;
    private int depth;
    private boolean argsArmed;
    private boolean forwarding;
    private int forwardDepth;
    private String captureKey;

    McpHttpArguments(
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
    }

    @Override
    public Status feed(
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
            argsArmed = depth == 1 && "arguments".contentEquals(source.getKey());
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
            captureKey = forwardDepth == 1 ? source.getKey().toString() : null;
            status = forward(sink, source, event);
            break;
        case VALUE_STRING:
            if (captureKey != null)
            {
                captured.put(captureKey, source.getString());
                captureKey = null;
            }
            status = forward(sink, source, event);
            break;
        case SEGMENT:
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
        return sink.feed(downstreamControl, source, event);
    }

    private void onDownstreamSegmentable()
    {
        if (upstream != null)
        {
            upstream.segmentable();
        }
    }
}
