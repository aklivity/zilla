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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

// A key or number that straddles an input window is delivered as several fragments of one token. Forwarding a
// fragmented token verbatim must reproduce the source bytes exactly, wherever the window boundary falls.
class JsonVerbatimFragmentTest
{
    private static final String JSON = "{\"tools\":[" +
        "{\"name\":\"get_weather\",\"description\":\"Get the weather\"," +
        "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},\"required\":[\"city\"]}," +
        "\"count\":12345},{\"name\":\"get_time\",\"count\":6789}]}";

    @Test
    void shouldReproduceKeysAndNumbersThatFragmentAcrossInputWindows()
    {
        for (int window = 4; window <= 64; window++)
        {
            assertEquals(JSON, forward(JSON, window), "window " + window);
        }
    }

    private static String forward(
        String json,
        int window)
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[4096]);
        generator.wrap(output, 0, output.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new Forward())
            .into(JsonEx.createSink(generator));
        pipeline.reset();

        byte[] bytes = json.getBytes(UTF_8);
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        int guard = 0;
        while (status == Status.STARVED && guard++ < 10_000)
        {
            limit = Math.min(limit + window, bytes.length);
            status = pipeline.transform(new UnsafeBufferEx(bytes), progress, limit, limit >= bytes.length);
            if (status == Status.STARVED)
            {
                progress = limit - pipeline.remaining();
            }
        }
        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[generator.length()];
        output.getBytes(0, out);
        return new String(out, UTF_8);
    }

    // A mediating transform that needs structured events but reproduces the source: it declines segmentable(),
    // absorbs verbatim() and re-asserts it downstream, forwarding every event as VERBATIM.
    private static final class Forward implements JsonTransform
    {
        private boolean downstreamVerbatim;
        private int depth;
        private JsonController upstream;
        private final JsonController mediator = new JsonController()
        {
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
        };

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
                status = sink.transform(mediator, source, forward(event));
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                Status downstream = sink.transform(mediator, source, forward(event));
                status = downstream == Status.REJECTED ? Status.REJECTED
                    : depth == 0 ? Status.COMPLETED
                    : downstream;
                break;
            default:
                status = sink.transform(mediator, source, forward(event));
                break;
            }
            return status;
        }

        @Override
        public void reset()
        {
            downstreamVerbatim = false;
            depth = 0;
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }

        private JsonEvent forward(
            JsonEvent event)
        {
            boolean body = event != JsonEvent.START_DOCUMENT && event != JsonEvent.END_DOCUMENT && !event.segmented();
            return downstreamVerbatim && body ? JsonEvent.VERBATIM : event;
        }
    }
}
