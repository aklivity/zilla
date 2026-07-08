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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Terminal {@link JsonSink} that captures a projected {@code with.query} object's top-level scalar values
 * into an ordered {@code captured} map, in place of the one-shot buffer walk a fully-buffered request used
 * to require: a top-level scalar member becomes one entry; a top-level array value repeats the same key
 * once per scalar element, in document order — the same semantics the prior implementation had, just driven
 * incrementally by the pipeline's own event stream rather than a second pass over a materialized buffer.
 * The caller turns {@code captured} into a percent-encoded query string once this sink reaches
 * {@link Status#COMPLETED}; this class does no encoding of its own, so url-encoding stays in one place.
 * <p>
 * A value spanning more than one input window accumulates across every fragment into {@code text} and only
 * commits once {@link JsonSource#deferredBytes()} reports the value complete, mirroring {@link McpHttpArguments}
 * and {@link McpHttpResults}'s identical accumulation pattern — only one value can ever be in flight at a
 * time (JSON parsing is strictly sequential), so a single reused accumulator is sufficient.
 * <p>
 * This sink never requests {@link JsonController#segmentable()} or {@link JsonController#verbatim()} on
 * {@code START_DOCUMENT} (the default JsonController requests neither), so its upstream projector delivers
 * plain structured events with decoded scalar text available directly — no segment/verbatim handling is
 * needed here.
 */
public final class McpHttpQuery implements JsonSink
{
    private final Map<String, List<String>> captured;
    private final StringBuilder text = new StringBuilder();

    private int depth;
    private boolean inArray;
    private String key;

    public McpHttpQuery(
        Map<String, List<String>> captured)
    {
        this.captured = captured;
    }

    @Override
    public void reset()
    {
        depth = 0;
        inArray = false;
        key = null;
        text.setLength(0);
    }

    @Override
    public boolean identity()
    {
        return false;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        Status status = Status.ADVANCED;
        switch (event)
        {
        case START_OBJECT:
            depth++;
            break;
        case START_ARRAY:
            depth++;
            inArray = depth == 2;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            if (event == JsonEvent.END_ARRAY)
            {
                inArray = false;
            }
            if (depth == 0)
            {
                status = Status.COMPLETED;
            }
            break;
        case KEY_NAME:
            if (depth == 1)
            {
                key = source.getString();
            }
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
            if (capturable())
            {
                text.append(source.getStringView());
                if (!source.deferredBytes())
                {
                    captureScalar(text.toString());
                    text.setLength(0);
                }
            }
            break;
        case VALUE_TRUE:
            if (capturable())
            {
                captureScalar("true");
            }
            break;
        case VALUE_FALSE:
            if (capturable())
            {
                captureScalar("false");
            }
            break;
        default:
            break;
        }
        return status;
    }

    private boolean capturable()
    {
        return key != null && (depth == 1 || depth == 2 && inArray);
    }

    private void captureScalar(
        String value)
    {
        captured.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }
}
