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

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

// A stage that cannot decide on a partial key declines it and defers the key's bytes, so they survive the input
// window being replaced. Once the key is complete the stage either forwards it, and the output is the source
// exactly, or drops the member, and nothing of the key is left behind.
class JsonDeferVerbatimTest
{
    private static final int MINIMUM_WINDOW = 12;
    private static final int MAXIMUM_WINDOW = 64;

    @Test
    void shouldDropDeferredKeyAcrossInputWindows()
    {
        String json = "{\"a\":1,\"target\":{\"x\":1},\"b\":2}";
        int key = json.indexOf("\"target\"");
        assertChunks(json, "{\"a\":1,\"b\":2}", key + 3, json.length());
        assertChunks(json, "{\"a\":1,\"b\":2}", key + 3, key + 5, json.length());
        assertChunks(json, "{\"a\":1,\"b\":2}", key + 1, key + 4, key + 7, json.length());
    }

    @Test
    void shouldForwardDeferredKeyThatDivergesAcrossInputWindows()
    {
        String json = "{\"a\":1,\"tarxyz\":2,\"b\":3}";
        assertWindows(json, json);
    }

    @Test
    void shouldForwardDeferredKeyLongerThanTargetAcrossInputWindows()
    {
        String json = "{\"a\":1,\"targetsLonger\":2,\"b\":3}";
        assertWindows(json, json);
    }

    @Test
    void shouldDropDeferredFirstKeyAcrossInputWindows()
    {
        String json = "{\"target\":{\"x\":1},\"b\":2}";
        assertChunks(json, "{\"b\":2}", 4, json.length());
        assertChunks(json, "{\"b\":2}", 2, 5, json.length());
    }

    @Test
    void shouldForwardDeferredFirstKeyThatDivergesAcrossInputWindows()
    {
        String json = "{\"tarxyz\":2,\"b\":3}";
        assertWindows(json, json);
    }

    @Test
    void shouldForwardDeferredKeyAfterDroppedFirstKeyAcrossInputWindows()
    {
        assertWindows("{\"target\":1,\"tarxyz\":2,\"b\":3}", "{\"tarxyz\":2,\"b\":3}");
    }

    @Test
    void shouldDropDeferredKeyAfterDroppedFirstKeyAcrossInputWindows()
    {
        String json = "{\"target\":1,\"b\":2,\"target\":3,\"c\":4}";
        int key = json.lastIndexOf("\"target\"");
        assertChunks(json, "{\"b\":2,\"c\":4}", key + 3, json.length());
    }

    @Test
    void shouldReproduceKeysSharingThePrefixWithWhitespaceAcrossInputWindows()
    {
        String json = "{ \"a\" : 1 ,\n  \"tarxyz\" : 2 ,\n  \"b\" : 3 }";
        assertWindows(json, json);
    }

    private static void assertWindows(
        String json,
        String expected)
    {
        for (int window = MINIMUM_WINDOW; window <= MAXIMUM_WINDOW; window++)
        {
            int count = (json.length() + window - 1) / window;
            int[] ends = new int[count];
            for (int index = 0; index < count; index++)
            {
                ends[index] = Math.min((index + 1) * window, json.length());
            }
            assertEquals(expected, prune(json, ends), "window " + window);
        }
    }

    private static void assertChunks(
        String json,
        String expected,
        int... ends)
    {
        assertEquals(expected, prune(json, ends), "chunks " + Arrays.toString(ends));
    }

    private static String prune(
        String json,
        int[] ends)
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[4096]);
        generator.wrap(output, 0, output.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new Prune("target"))
            .into(JsonEx.createSink(generator));
        pipeline.reset();

        byte[] bytes = json.getBytes(UTF_8);
        int progress = 0;
        Status status = Status.STARVED;
        for (int index = 0; index < ends.length; index++)
        {
            status = pipeline.transform(new UnsafeBufferEx(bytes), progress, ends[index], index == ends.length - 1);
            if (status == Status.STARVED)
            {
                progress = ends[index] - pipeline.remaining();
            }
        }
        assertEquals(Status.COMPLETED, status, "chunks " + Arrays.toString(ends) + " of " + json);
        byte[] out = new byte[generator.length()];
        output.getBytes(0, out);
        return new String(out, UTF_8);
    }

    // Drops the member named by dropKey at any depth. A key fragment is declined while it could still be the drop
    // key, and its bytes are deferred; a key that cannot be the drop key is forwarded as it arrives.
    private static final class Prune implements JsonTransform
    {
        private final String dropKey;
        private boolean downstreamVerbatim;
        private boolean keyVerbatim;
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

        private Prune(
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
                status = onKey(control, source, event, sink);
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
            keyVerbatim = false;
            depth = 0;
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }

        private Status onKey(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            final CharSequence key = source.getStringView();
            final boolean complete = !source.deferredBytes();
            Status status;
            if (keyVerbatim)
            {
                keyVerbatim = !complete;
                status = sink.transform(mediator, source, forward(event));
            }
            else if (!complete && prefix(key))
            {
                source.deferVerbatim();
                control.consumed(0);
                status = Status.STARVED;
            }
            else if (!complete)
            {
                keyVerbatim = true;
                status = sink.transform(mediator, source, forward(event));
            }
            else if (dropKey.contentEquals(key))
            {
                source.skipValue();
                status = Status.ADVANCED;
            }
            else
            {
                status = sink.transform(mediator, source, forward(event));
            }
            return status;
        }

        private boolean prefix(
            CharSequence key)
        {
            boolean prefix = key.length() <= dropKey.length();
            for (int i = 0; prefix && i < key.length(); i++)
            {
                prefix = key.charAt(i) == dropKey.charAt(i);
            }
            return prefix;
        }

        private JsonEvent forward(
            JsonEvent event)
        {
            boolean body = event != JsonEvent.START_DOCUMENT && event != JsonEvent.END_DOCUMENT && !event.segmented();
            return downstreamVerbatim && body ? JsonEvent.VERBATIM : event;
        }
    }
}
