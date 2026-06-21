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

import java.math.BigDecimal;

import jakarta.json.stream.JsonLocation;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

// Phase 3 (inject): a transform injects a canonical member between two verbatim runs, exercising the
// VERBATIM ... STRUCTURED ... VERBATIM interleaving. The generator tracks structural state continuously as
// each verbatim run's step is applied, so an injected member separates correctly without re-emitting the
// verbatim-written brackets, and a displaced former-first member gets a synthesized separator; the following
// verbatim run owns its own separator, so no double comma. Worked example from the design doc.
class JsonInjectTest
{
    @Test
    void shouldInjectMemberBetweenVerbatimRuns()
    {
        assertEquals("{\"a\": 1, \"b\": 2,\"x\":9, \"c\": 3}",
            inject("c", "x", "9", "{\"a\": 1, \"b\": 2, \"c\": 3}"));
    }

    @Test
    void shouldInjectStringMemberBetweenVerbatimRuns()
    {
        assertEquals("{\"a\": 1,\"tag\":\"new\", \"b\": 2}",
            inject("b", "tag", JsonEvent.VALUE_STRING, "new", "{\"a\": 1, \"b\": 2}"));
    }

    @Test
    void shouldInjectMemberBeforeOnlyField()
    {
        // injecting before a container's first member displaces it to non-first: the seeded generator
        // synthesizes the separator the displaced member's verbatim bytes do not carry
        assertEquals("{\"x\":9,\"a\": 1}",
            inject("a", "x", "9", "{\"a\": 1}"));
    }

    @Test
    void shouldInjectMemberBeforeFirstOfManyFields()
    {
        assertEquals("{\"x\":9,\"a\": 1, \"b\": 2}",
            inject("a", "x", "9", "{\"a\": 1, \"b\": 2}"));
    }

    private static String inject(
        String beforeKey,
        String injectKey,
        String injectValue,
        String json)
    {
        return inject(beforeKey, injectKey, JsonEvent.VALUE_NUMBER, injectValue, json);
    }

    private static String inject(
        String beforeKey,
        String injectKey,
        JsonEvent injectKind,
        String injectValue,
        String json)
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBuffer output = new UnsafeBuffer(new byte[1024]);
        generator.wrap(output, 0, output.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new Inject(beforeKey, injectKey, injectKind, injectValue))
            .into(JsonEx.createSink(generator));

        byte[] bytes = (json + " ").getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);
        assertEquals(Status.COMPLETED, status);

        byte[] out = new byte[generator.length()];
        output.getBytes(0, out);
        return new String(out, UTF_8);
    }

    // A mediating transform: it declines segmentable() and absorbs verbatim() (re-asserting it downstream), so
    // kept content copies its original bytes. Before a named top-level key it injects a canonical member
    // (key + number value) as structured events, then forwards the original key verbatim — the injected member
    // carries its own leading separator (decided by the seeded generator), the following verbatim run carries
    // the original separator.
    private static final class Inject implements JsonTransform
    {
        private final String beforeKey;
        private final CharSequence injectKey;
        private final JsonEvent injectKind;
        private final CharSequence injectValue;

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

        // injected values carry no source bytes, so a consumed() report from rendering them must not advance
        // the parser's cursor
        private final JsonController injectControl = new JsonController()
        {
            @Override
            public void segmentable()
            {
            }

            @Override
            public void verbatim()
            {
            }

            @Override
            public void consumed(
                int sourceBytes)
            {
            }
        };

        private Inject(
            String beforeKey,
            String injectKey,
            JsonEvent injectKind,
            String injectValue)
        {
            this.beforeKey = beforeKey;
            this.injectKey = injectKey;
            this.injectKind = injectKind;
            this.injectValue = injectValue;
        }

        @Override
        public Status feed(
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
                status = sink.feed(mediator, source, forward(event));
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                Status downstream = sink.feed(mediator, source, forward(event));
                status = downstream == Status.REJECTED ? Status.REJECTED
                    : depth == 0 ? Status.COMPLETED
                    : downstream;
                break;
            case KEY_NAME:
                if (depth == 1 && contentEquals(beforeKey, source.getStringView()))
                {
                    // inject the canonical member, then forward the original key verbatim; the generator's state
                    // is already current from the verbatim steps applied so far, so the injected key separates
                    // correctly and a displaced first member gets its synthesized separator
                    sink.feed(injectControl, new InjectSource(injectKey), JsonEvent.KEY_NAME);
                    sink.feed(injectControl, new InjectSource(injectValue), injectKind);
                }
                status = sink.feed(mediator, source, forward(event));
                break;
            default:
                status = sink.feed(mediator, source, forward(event));
                break;
            }
            return status;
        }

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

    // Supplies an injected key or number value: getStringView() returns the injected text. The injected events
    // are fed structurally (not verbatim), so the generator tracks state from them directly — getVerbatim() is
    // never consulted for an injected source.
    private static final class InjectSource implements JsonSource
    {
        private final CharSequence text;

        private InjectSource(
            CharSequence text)
        {
            this.text = text;
        }

        @Override
        public CharSequence getStringView()
        {
            return text;
        }

        @Override
        public boolean deferredBytes()
        {
            return false;
        }

        @Override
        public String getString()
        {
            return text.toString();
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIntegralNumber()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonLocation getLocation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectBuffer getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonVerbatim getVerbatim(
            int limit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void skipValue()
        {
            throw new UnsupportedOperationException();
        }
    }
}
