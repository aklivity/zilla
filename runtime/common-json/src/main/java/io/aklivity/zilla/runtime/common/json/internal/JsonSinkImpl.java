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
package io.aklivity.zilla.runtime.common.json.internal;

import jakarta.json.stream.JsonParser.Event;

import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Terminal {@link JsonSink} that materializes each fed event into the corresponding {@code writeXxx}
 * call on the wrapped {@link JsonGeneratorEx}. Reaches {@link Status#COMPLETE} when the current
 * top-level value closes at depth zero.
 */
public final class JsonSinkImpl implements JsonSink
{
    private final JsonGeneratorEx generator;
    private int depth;

    public JsonSinkImpl(
        JsonGeneratorEx generator)
    {
        this.generator = generator;
    }

    @Override
    public Status feed(
        Event evt,
        JsonSource in)
    {
        Status status = Status.PENDING;
        switch (evt)
        {
        case KEY_NAME:
            generator.writeKey(in.getString());
            break;
        case START_OBJECT:
            generator.writeStartObject();
            depth++;
            break;
        case START_ARRAY:
            generator.writeStartArray();
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            generator.writeEnd();
            depth--;
            if (depth == 0)
            {
                status = Status.COMPLETE;
            }
            break;
        case VALUE_STRING:
            generator.write(in.getString());
            status = scalarStatus();
            break;
        case VALUE_NUMBER:
            generator.writeNumber(in.getString());
            status = scalarStatus();
            break;
        case VALUE_TRUE:
            generator.write(true);
            status = scalarStatus();
            break;
        case VALUE_FALSE:
            generator.write(false);
            status = scalarStatus();
            break;
        case VALUE_NULL:
            generator.writeNull();
            status = scalarStatus();
            break;
        default:
            break;
        }
        return status;
    }

    @Override
    public void reset()
    {
        depth = 0;
    }

    private Status scalarStatus()
    {
        return depth == 0 ? Status.COMPLETE : Status.PENDING;
    }
}
