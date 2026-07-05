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
 * different lengths and depths — over one incremental pass instead of one re-scan per path. Only non-segmented
 * scalar events ({@code VALUE_STRING}, {@code VALUE_NUMBER}, {@code VALUE_TRUE}, {@code VALUE_FALSE}) are
 * captured, mirroring {@link McpHttpArguments}: a value large enough to arrive as a {@code SEGMENT} run resolves
 * to the empty string, the same fallback an unresolved path already has.
 */
public final class McpHttpResults implements JsonTransform
{
    private final Map<String, String> captured;
    private final String[] paths;
    private final String[][] segments;
    private final int[] matched;

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
        if (awaiting != -1)
        {
            capture(awaiting, event, source);
            awaiting = -1;
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

        return sink.transform(control, source, event);
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

    private void capture(
        int index,
        JsonEvent event,
        JsonSource source)
    {
        switch (event)
        {
        case VALUE_STRING:
        case VALUE_NUMBER:
            captured.put(paths[index], source.getString());
            break;
        case VALUE_TRUE:
            captured.put(paths[index], "true");
            break;
        case VALUE_FALSE:
            captured.put(paths[index], "false");
            break;
        default:
            break;
        }
    }
}
