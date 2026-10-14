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
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class JsonEscapedFixture
{
    private JsonEscapedFixture()
    {
    }

    // The outcome of driving a document through a pipeline: its terminal status and everything the terminal
    // generator wrote, drained across every output bound.
    record Outcome(
        Status status,
        String output)
    {
    }

    // Asks the upstream to parse the string value of each named key as a document, and forwards every event
    // (including the markers) when forward is set; otherwise the markers are dropped, which unescapes the document.
    // Mediating: it declines segmentable() so the structured events it inspects are what it sees.
    static final class Unescaper implements JsonTransform
    {
        private final Set<String> keys;
        private final boolean forward;
        private final Controller controller;

        Unescaper(
            boolean forward,
            String... keys)
        {
            this.keys = Set.of(keys);
            this.forward = forward;
            this.controller = new Controller();
        }

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            controller.upstream = control;
            Status status;
            if (event == JsonEvent.KEY_NAME && source.deferredBytes())
            {
                // a key needs to be whole to be matched: decline the fragment so the source re-presents it whole
                control.consumed(0);
                status = Status.STARVED;
            }
            else
            {
                // the key is read before it is forwarded, which consumes it
                final boolean escape = event == JsonEvent.KEY_NAME && keys.contains(source.getStringView().toString());
                final boolean marker = event == JsonEvent.START_ESCAPED || event == JsonEvent.END_ESCAPED;
                status = marker && !forward ? Status.ADVANCED : sink.transform(controller, source, event);
                if (escape)
                {
                    control.escaped();
                }
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
            controller.upstream = control;
            return sink.resume(controller, source, event);
        }

        @Override
        public Status flush(
            JsonController control,
            JsonSource source,
            JsonSink sink)
        {
            controller.upstream = control;
            return sink.flush(controller, source);
        }

        private static final class Controller implements JsonController
        {
            private JsonController upstream;

            @Override
            public void segmentable()
            {
            }

            @Override
            public void consumed(
                int sourceBytes)
            {
                upstream.consumed(sourceBytes);
            }

            @Override
            public void escaped()
            {
                upstream.escaped();
            }
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }
    }

    // Wraps the value of each named key in synthesized markers, which escapes the value as a string: the
    // key is forwarded, then START_ESCAPED, then the events of the value, then END_ESCAPED. The value may be a
    // scalar or a container. Mediating, like Unescaper.
    static final class Escaper implements JsonTransform
    {
        private final Set<String> keys;
        private final int[] bases;

        private int scopes;
        private int depth;

        Escaper(
            String... keys)
        {
            this.keys = Set.of(keys);
            this.bases = new int[8];
        }

        @Override
        public void reset()
        {
            scopes = 0;
            depth = 0;
        }

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            Status status;
            if (event == JsonEvent.KEY_NAME && source.deferredBytes())
            {
                control.consumed(0);
                status = Status.STARVED;
            }
            else
            {
                final boolean escape = event == JsonEvent.KEY_NAME && keys.contains(source.getStringView().toString());
                status = sink.transform(control, source, event);
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
                default:
                    break;
                }
                final boolean complete = event != JsonEvent.KEY_NAME && event != JsonEvent.START_OBJECT &&
                    event != JsonEvent.START_ARRAY && event != JsonEvent.START_DOCUMENT;
                while (complete && scopes > 0 && depth == bases[scopes - 1])
                {
                    scopes--;
                    sink.transform(control, source, JsonEvent.END_ESCAPED);
                }
                if (escape)
                {
                    bases[scopes++] = depth;
                    sink.transform(control, source, JsonEvent.START_ESCAPED);
                }
            }
            return status;
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }
    }

    // Misbehaves in one of the ways a stage can misuse the markers.
    static final class Misuse implements JsonTransform
    {
        enum Mode
        {
            START_WITHOUT_END,
            END_WITHOUT_START,
            START_IN_KEY_POSITION,
            END_WITH_OPEN_CONTAINER
        }

        private final Mode mode;

        Misuse(
            Mode mode)
        {
            this.mode = mode;
        }

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            Status status = sink.transform(control, source, event);
            switch (mode)
            {
            case START_WITHOUT_END:
                if (event == JsonEvent.KEY_NAME)
                {
                    status = sink.transform(control, source, JsonEvent.START_ESCAPED);
                }
                break;
            case END_WITHOUT_START:
                if (event == JsonEvent.VALUE_NUMBER)
                {
                    status = sink.transform(control, source, JsonEvent.END_ESCAPED);
                }
                break;
            case START_IN_KEY_POSITION:
                if (event == JsonEvent.START_OBJECT)
                {
                    status = sink.transform(control, source, JsonEvent.START_ESCAPED);
                }
                break;
            case END_WITH_OPEN_CONTAINER:
                if (event == JsonEvent.KEY_NAME)
                {
                    sink.transform(control, source, JsonEvent.START_ESCAPED);
                }
                else if (event == JsonEvent.VALUE_NUMBER)
                {
                    status = sink.transform(control, source, JsonEvent.END_ESCAPED);
                }
                break;
            default:
                break;
            }
            return status;
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }
    }

    // Records each delivered event, with the decoded text of a key or scalar, as one token per event.
    static final class Recorder implements JsonSink
    {
        private final List<String> events = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private int depth;

        List<String> events()
        {
            return events;
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
            case KEY_NAME:
            case VALUE_STRING:
            case VALUE_NUMBER:
                text.append(source.getStringView());
                control.consumed(source.getStringView().length());
                if (!source.deferredBytes())
                {
                    events.add(event + "(" + text + ")");
                    text.setLength(0);
                    status = depth == 0 && event != JsonEvent.KEY_NAME ? Status.COMPLETED : Status.ADVANCED;
                }
                break;
            case START_OBJECT:
            case START_ARRAY:
            case START_ESCAPED:
                depth++;
                events.add(event.name());
                break;
            case END_OBJECT:
            case END_ARRAY:
            case END_ESCAPED:
                depth--;
                events.add(event.name());
                status = depth == 0 ? Status.COMPLETED : Status.ADVANCED;
                break;
            case START_DOCUMENT:
            case END_DOCUMENT:
                break;
            default:
                events.add(event.name());
                status = depth == 0 ? Status.COMPLETED : Status.ADVANCED;
                break;
            }
            return status;
        }

        @Override
        public boolean identity()
        {
            return false;
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }
    }

    // Drives json through the pipeline in fixed-size input windows (carrying the unconsumed tail across each
    // STARVED) and a bounded output (draining across each SUSPENDED), as a real caller would.
    static Outcome drive(
        JsonStream stream,
        JsonSink.Delivery delivery,
        String json,
        int inWindow,
        int outBound)
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        final MutableDirectBufferEx output = new UnsafeBufferEx(new byte[outBound]);
        final JsonPipeline pipeline = stream.into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, delivery)));
        return drive(pipeline, generator, output, json, inWindow, outBound);
    }

    static Outcome drive(
        JsonPipeline pipeline,
        JsonGeneratorEx generator,
        MutableDirectBufferEx output,
        String json,
        int inWindow,
        int outBound)
    {
        final byte[] msg = (json + " ").getBytes(UTF_8);
        final UnsafeBufferEx input = new UnsafeBufferEx(msg);
        final StringBuilder result = new StringBuilder();
        pipeline.reset();
        generator.wrap(output, 0, outBound);
        int progress = 0;
        int limit = 0;
        int guard = 0;
        Status status = Status.STARVED;
        while (guard++ < 1_000_000)
        {
            if (status == Status.STARVED)
            {
                limit = Math.min(limit + inWindow, msg.length);
            }
            final boolean last = limit >= msg.length;
            status = pipeline.transform(input, progress, limit, last);
            if (status != Status.REJECTED)
            {
                final byte[] chunk = new byte[generator.length()];
                output.getBytes(0, chunk);
                result.append(new String(chunk, UTF_8));
            }
            if (status == Status.STARVED)
            {
                assertFalse(last, "last window must not starve");
                progress = limit - pipeline.remaining();
            }
            else if (status != Status.SUSPENDED)
            {
                break;
            }
            generator.wrap(output, 0, outBound);
        }
        return new Outcome(status, result.toString());
    }

    // Drives json through the pipeline into a recorder, in fixed-size input windows.
    static Outcome record(
        JsonStream stream,
        Recorder recorder,
        String json,
        int inWindow)
    {
        final JsonPipeline pipeline = stream.into(recorder);
        final byte[] msg = (json + " ").getBytes(UTF_8);
        final UnsafeBufferEx input = new UnsafeBufferEx(msg);
        pipeline.reset();
        int progress = 0;
        int limit = 0;
        int guard = 0;
        Status status = Status.STARVED;
        while (guard++ < 1_000_000)
        {
            limit = Math.min(limit + inWindow, msg.length);
            final boolean last = limit >= msg.length;
            status = pipeline.transform(input, progress, limit, last);
            if (status != Status.STARVED)
            {
                break;
            }
            progress = limit - pipeline.remaining();
        }
        return new Outcome(status, String.join(" ", recorder.events()));
    }
}
