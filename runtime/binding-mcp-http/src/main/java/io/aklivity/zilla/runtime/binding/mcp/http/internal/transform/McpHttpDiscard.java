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

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Terminal {@link JsonSink} that discards every event, reaching {@link Status#COMPLETED} when the top-level
 * value closes at depth zero. Used when a tool's {@code tool.input} schema must be validated (a validator
 * stage sits upstream of this sink) but the route has nothing to shape a request from that value — no
 * {@code with.body}, {@code with.body.template}, or {@code with.query} — so there is no destination for the
 * validated bytes to project into.
 */
public final class McpHttpDiscard implements JsonSink
{
    private int depth;

    @Override
    public void reset()
    {
        depth = 0;
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
        case START_ARRAY:
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            if (depth == 0)
            {
                status = Status.COMPLETED;
            }
            break;
        default:
            break;
        }
        return status;
    }
}
