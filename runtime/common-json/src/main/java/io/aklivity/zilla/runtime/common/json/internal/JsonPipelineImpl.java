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

import jakarta.json.stream.JsonParsingException;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonSink;

/**
 * Backs {@link JsonPipeline}: holds the bound root {@link JsonSink} and the {@link JsonParserImpl}
 * driver. {@link #feed(DirectBuffer, int, int)} re-targets the parser at the frame buffer then pumps
 * each parsed event through the root sink, passing the parser itself as the immutable
 * {@code JsonSource} view.
 */
public final class JsonPipelineImpl implements JsonPipeline
{
    private final JsonParserImpl parser;
    private final JsonSink root;

    private boolean suspended;

    public JsonPipelineImpl(
        JsonParserImpl parser,
        JsonSink root)
    {
        this.parser = parser;
        this.root = root;
    }

    @Override
    public void reset()
    {
        parser.reset();
        root.reset();
        suspended = false;
    }

    @Override
    public Status feed(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        Status status = Status.RESUMABLE;
        try
        {
            if (suspended)
            {
                status = root.resume();
            }
            else
            {
                parser.wrap(buffer, offset, length);
            }
            while (status == Status.RESUMABLE && parser.hasNextEvent())
            {
                status = root.feed(parser, parser, parser.nextEvent());
            }
        }
        catch (JsonParsingException ex)
        {
            status = Status.REJECTED;
        }
        suspended = status == Status.SUSPENDED;
        return status;
    }
}
