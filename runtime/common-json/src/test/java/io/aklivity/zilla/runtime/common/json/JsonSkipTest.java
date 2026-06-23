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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

// Phase 2 (prune): a transform drops a named top-level field while forwarding the rest verbatim, so the
// kept content keeps its original bytes (insignificant whitespace included). The drop is driven by a single
// source.skipValue() call on the matched KEY_NAME — the parser advances past the whole member (key,
// separator, value) and folds in the leading-separator trim that keeps the surviving siblings well-formed.
class JsonSkipTest
{
    @Test
    void shouldDropMiddleFieldPreservingWhitespace()
    {
        assertEquals("{ \"a\" : 1, \"c\" : 3 } ", skip("b", "{ \"a\" : 1, \"b\" : 2, \"c\" : 3 }"));
    }

    @Test
    void shouldDropLastFieldPreservingWhitespace()
    {
        assertEquals("{ \"a\" : 1 } ", skip("b", "{ \"a\" : 1, \"b\" : 2 }"));
    }

    @Test
    void shouldDropFirstFieldPreservingWhitespace()
    {
        assertEquals("{ \"b\" : 2 } ", skip("a", "{ \"a\" : 1, \"b\" : 2 }"));
    }

    @Test
    void shouldDropFieldWithObjectValue()
    {
        assertEquals("{ \"a\" : 1, \"c\" : 3 } ",
            skip("b", "{ \"a\" : 1, \"b\" : { \"x\" : [1, 2] }, \"c\" : 3 }"));
    }

    @Test
    void shouldDropOnlyField()
    {
        assertEquals("{} ", skip("a", "{ \"a\" : 1 }"));
    }

    private static String skip(
        String dropKey,
        String json)
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[1024]);
        generator.wrap(output, 0, output.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new Skip(dropKey))
            .into(JsonEx.createSink(generator));

        byte[] bytes = (json + " ").getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.transform(new UnsafeBuffer(bytes), 0, bytes.length);
        assertEquals(Status.COMPLETED, status);

        byte[] out = new byte[generator.length()];
        output.getBytes(0, out);
        return new String(out, UTF_8);
    }

    // A mediating transform: it declines segmentable() (it needs structured events to match keys) but absorbs
    // verbatim(), re-asserting it downstream so kept content copies its original bytes. It forwards every event
    // verbatim except a named top-level field, which it drops with a single source.skipValue() on the matched
    // KEY_NAME — the parser consumes the whole member's sub-events, so the transform never sees them.
    private static final class Skip implements JsonTransform
    {
        private final String dropKey;

        private JsonController upstream;
        private boolean downstreamVerbatim;
        private int depth;

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

        private Skip(
            String dropKey)
        {
            this.dropKey = dropKey;
        }

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
            case KEY_NAME:
                if (depth == 1 && contentEquals(dropKey, source.getStringView()))
                {
                    // drop the matched member: the source advances past its key, separator, and value and folds
                    // in the leading-separator trim, so no sub-event reaches this transform or the sink
                    source.skipValue();
                    status = Status.ADVANCED;
                }
                else
                {
                    status = sink.transform(mediator, source, forward(event));
                }
                break;
            default:
                status = sink.transform(mediator, source, forward(event));
                break;
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

        @Override
        public void reset()
        {
            downstreamVerbatim = false;
            depth = 0;
        }

        private static boolean contentEquals(
            String name,
            CharSequence view)
        {
            boolean matches = name.length() == view.length();
            for (int i = 0; matches && i < name.length(); i++)
            {
                matches = name.charAt(i) == view.charAt(i);
            }
            return matches;
        }
    }
}
