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
package io.aklivity.zilla.runtime.binding.mcp.internal.transform;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

// Drops the securitySchemes member from each direct element of the target array while forwarding every other
// event verbatim, so the surviving content keeps its original bytes (insignificant whitespace included). Used
// at the mcp server egress to withhold securitySchemes from clients until SEP-1488 finalizes, while the
// upstream pipeline keeps it for scope enforcement. Models the byte-preserving prune on model-json's
// JsonExtractor: it absorbs the sink's verbatim() opt-in and re-asserts it downstream so kept events copy
// their source bytes rather than re-render canonically, and drops the matched member with a single
// JsonSource#skipValue() that also folds in the separator trim keeping the surviving members well-formed.
public final class McpSchemeExcluder implements JsonTransform
{
    private static final String SECURITY_SCHEMES = "securitySchemes";
    private static final int NO_DEPTH = -1;

    private final String arrayKey;
    private final Mediator mediator = new Mediator();

    private boolean downstreamVerbatim;
    private int depth;
    private int arrayDepth;
    private boolean armed;

    public McpSchemeExcluder(
        String arrayKey)
    {
        this.arrayKey = arrayKey;
    }

    @Override
    public void reset()
    {
        downstreamVerbatim = false;
        depth = 0;
        arrayDepth = NO_DEPTH;
        armed = false;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        mediator.upstream = control;
        Status status;
        switch (event)
        {
        case START_ARRAY:
            if (armed)
            {
                arrayDepth = depth + 1;
                armed = false;
            }
            depth++;
            status = sink.transform(mediator, source, forward(event));
            break;
        case START_OBJECT:
            // a non-array value clears the arm so only the target array's elements are pruned
            armed = false;
            depth++;
            status = sink.transform(mediator, source, forward(event));
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            if (arrayDepth != NO_DEPTH && depth < arrayDepth)
            {
                arrayDepth = NO_DEPTH;
            }
            // the verbatim sink owns no structure, so this stage signals document completion at depth zero
            Status downstream = sink.transform(mediator, source, forward(event));
            status = downstream == Status.REJECTED ? Status.REJECTED
                : depth == 0 ? Status.COMPLETED
                : downstream;
            break;
        case KEY_NAME:
            status = onKey(source, event, sink);
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
        mediator.upstream = control;
        return sink.resume(mediator, source, forward(event));
    }

    @Override
    public Status flush(
        JsonController control,
        JsonSource source,
        JsonSink sink)
    {
        mediator.upstream = control;
        return sink.flush(mediator, source);
    }

    // arms array descent on the target key, then drops the securitySchemes member of a direct array element
    private Status onKey(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        if (depth == 1)
        {
            armed = arrayKey.contentEquals(source.getStringView());
        }
        Status status;
        if (arrayDepth != NO_DEPTH && depth == arrayDepth + 1 &&
            SECURITY_SCHEMES.contentEquals(source.getStringView()))
        {
            // drop the matched member: the source advances past its key, separator, and value and folds in the
            // leading-separator trim, so no sub-event of the dropped value reaches this stage or the sink
            source.skipValue();
            status = Status.ADVANCED;
        }
        else
        {
            status = sink.transform(mediator, source, forward(event));
        }
        return status;
    }

    // Re-asserts verbatim downstream once the sink has opted in: a body event (a scalar, key, or structural
    // event — not document framing or a segment) is forwarded as a VERBATIM event so the sink copies the
    // original source bytes rather than re-rendering them canonically.
    private JsonEvent forward(
        JsonEvent event)
    {
        boolean body = event != JsonEvent.START_DOCUMENT && event != JsonEvent.END_DOCUMENT && !event.segmented();
        return downstreamVerbatim && body ? JsonEvent.VERBATIM : event;
    }

    // Keeps receiving structured events so it can match keys: it intercepts the sink's byte-delivery opt-ins
    // (segmentable, verbatim) rather than letting them reach the parser, and re-asserts verbatim toward its own
    // sink (via forward) so the terminal sink still reproduces the original bytes.
    private final class Mediator implements JsonController
    {
        private JsonController upstream;

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
