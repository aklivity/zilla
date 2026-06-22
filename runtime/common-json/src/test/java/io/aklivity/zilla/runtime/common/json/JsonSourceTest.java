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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.agrona.MutableDirectBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonSourceTest
{
    @Test
    void shouldReadPrimitivesAndViewThroughSource()
    {
        List<String> views = new ArrayList<>();
        List<Integer> ints = new ArrayList<>();
        List<Long> longs = new ArrayList<>();

        // a stage reads the new JsonSource accessors off the value it is handed, then forwards
        // the event to the terminal sink so the pipeline still completes the document
        JsonTransform capture = (control, source, event, sink) ->
        {
            if (event == JsonEvent.VALUE_NUMBER && !source.deferredBytes())
            {
                views.add(source.getStringView().toString());
                ints.add(source.getInt());
                longs.add(source.getLong());
            }
            else if (event == JsonEvent.VALUE_STRING && !source.deferredBytes())
            {
                views.add(source.getStringView().toString());
            }
            return sink.transform(control, source, event);
        };

        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBufferEx(new byte[256]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(capture)
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));

        byte[] bytes = "[42,-7,\"hi\"]".getBytes(UTF_8);
        pipeline.reset();
        Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(Status.COMPLETED, status);
        assertEquals(List.of(42, -7), ints);
        assertEquals(List.of(42L, -7L), longs);
        assertEquals(List.of("42", "-7", "hi"), views);

        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("[42,-7,\"hi\"]", new String(out, UTF_8));
    }
}
