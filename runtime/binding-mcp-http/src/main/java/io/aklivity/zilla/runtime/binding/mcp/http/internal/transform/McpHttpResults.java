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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.transform;

import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Watches a {@code tools/call} response body as it streams past and captures the scalar value at each of a
 * fixed set of dotted {@code result.<path>} references (e.g. {@code number}, {@code data.id}) — the paths a
 * configured {@code tool.summary} template interpolates — into {@code captured}, while forwarding every event
 * downstream unchanged ({@link #identity()} returns {@code true}). This lets a summary be resolved once the
 * response has fully streamed past without re-scanning a buffered copy, the response-side counterpart to
 * {@link McpHttpArguments}'s request-side capture.
 * <p>
 * Each configured path tracks its own match progress independently against the shared document depth, mirroring
 * the depth/matched-segment algorithm a one-shot buffered lookup would use (match segment {@code n} of a path
 * only at a {@code KEY_NAME} seen at depth {@code n + 1}), extended to watch several paths — of possibly
 * different lengths and depths — over one incremental pass instead of one re-scan per path. Only scalar events
 * ({@code VALUE_STRING}, {@code VALUE_NUMBER}, {@code VALUE_TRUE}, {@code VALUE_FALSE}) are captured. A value
 * spanning more than one input window accumulates across every fragment into {@code text} and only commits
 * once {@link JsonSource#deferredBytes()} reports the value complete; since only one value can ever be
 * captured "in flight" at a time (JSON parsing is strictly sequential), a single reused accumulator is
 * sufficient.
 * <p>
 * This is a mediating, structure-inspecting transform sitting in front of a byte-preferring terminal sink
 * (see {@code common-json}'s verbatim-validate design notes), so it cannot forward a downstream
 * {@link JsonController#segmentable()} request upstream unchanged the way a purely forwarding stage would:
 * granting it would let the whole document stream past as an opaque {@code SEGMENT} run, and the {@code
 * KEY_NAME} events this class matches paths against would never be delivered at all. While any path remains
 * configured, {@code segmentable()} is declined so its own upstream keeps delivering structured events, and
 * {@link JsonController#verbatim()} is re-asserted upstream instead — the byte-preserving fidelity modifier
 * that rides alongside structured events rather than substituting for them. When no paths are configured
 * (nothing to watch), both requests pass through unchanged, so a tool with no {@code tool.summary} references
 * keeps the full byte-passthrough optimization.
 */
public final class McpHttpResults implements JsonTransform
{
    private final Map<String, String> captured;
    private final String[] paths;
    private final String[][] segments;
    private final int[] matched;
    private final StringBuilder text = new StringBuilder();
    private final JsonController downstreamControl = new JsonController()
    {
        @Override
        public void segmentable()
        {
            if (paths.length == 0)
            {
                upstream.segmentable();
            }
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
    private int awaiting = -1;

    public McpHttpResults(
        Map<String, String> captured,
        List<String> paths)
    {
        this.captured = captured;
        this.paths = paths.toArray(String[]::new);
        this.segments = new String[this.paths.length][];
        for (int i = 0; i < this.paths.length; i++)
        {
            this.segments[i] = this.paths[i].split("\\.");
        }
        this.matched = new int[this.paths.length];
    }

    @Override
    public void reset()
    {
        depth = 0;
        awaiting = -1;
        text.setLength(0);
        for (int i = 0; i < matched.length; i++)
        {
            matched[i] = 0;
        }
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
        if (awaiting != -1)
        {
            capture(awaiting, event, source);
        }

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            break;
        case KEY_NAME:
            onKeyName(source);
            break;
        default:
            break;
        }

        return sink.transform(downstreamControl, source, event);
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

    private void onKeyName(
        JsonSource source)
    {
        for (int i = 0; i < segments.length; i++)
        {
            if (matched[i] < segments[i].length &&
                depth == matched[i] + 1 &&
                segments[i][matched[i]].contentEquals(source.getStringView()))
            {
                matched[i]++;
                if (matched[i] == segments[i].length)
                {
                    awaiting = i;
                }
            }
        }
    }

    // Appends this fragment to the in-flight value's accumulator, committing to `captured` (and clearing
    // `awaiting`, so the next KEY_NAME can arm a fresh capture) only once deferredBytes() reports no more
    // fragments follow; a structural value (an object/array where a scalar was expected) gives up
    // immediately, matching the existing unresolved-path fallback to the empty string. A VERBATIM event
    // rides alongside the structured event stream for the same value rather than substituting for it (see
    // the class Javadoc), so it is ignored here rather than treated as an unexpected structural event.
    private void capture(
        int index,
        JsonEvent event,
        JsonSource source)
    {
        switch (event)
        {
        case VALUE_STRING:
        case VALUE_NUMBER:
            text.append(source.getStringView());
            if (!source.deferredBytes())
            {
                captured.put(paths[index], text.toString());
                text.setLength(0);
                awaiting = -1;
            }
            break;
        case VALUE_TRUE:
            captured.put(paths[index], "true");
            awaiting = -1;
            break;
        case VALUE_FALSE:
            captured.put(paths[index], "false");
            awaiting = -1;
            break;
        case VERBATIM:
            break;
        default:
            awaiting = -1;
            break;
        }
    }
}
