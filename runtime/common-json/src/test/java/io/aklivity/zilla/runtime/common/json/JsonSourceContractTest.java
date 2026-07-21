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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class JsonSourceContractTest
{
    // getSegment() is valid only in reaction to a segmented event; reading it off a structured value
    // event trips the parser's assert (assertions are enabled under the test runner).
    @Test
    void shouldRejectSegmentAccessOnStructuredValue()
    {
        JsonTransform probe = (control, source, event, sink) ->
        {
            if (event == JsonEvent.VALUE_NUMBER)
            {
                assertThrows(AssertionError.class, source::getSegment);
            }
            return sink.transform(control, source, event);
        };
        run(probe, "[42]", JsonSink.Delivery.STRUCTURED);
    }

    // the scalar value getters are valid only in reaction to a structured value event; reading one off a
    // segmented event trips the assert
    @Test
    void shouldRejectScalarAccessOnSegment()
    {
        JsonTransform probe = (control, source, event, sink) ->
        {
            if (event == JsonEvent.SEGMENT)
            {
                assertThrows(AssertionError.class, source::getStringView);
                assertThrows(AssertionError.class, source::getInt);
            }
            return sink.transform(control, source, event);
        };
        // the sink opts into segment delivery, so the whole document arrives as SEGMENT events
        run(probe, "{\"a\":1}", JsonSink.Delivery.SEGMENTABLE);
    }

    private static void run(
        JsonTransform probe,
        String json,
        JsonSink.Delivery delivery)
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[256]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(probe)
            .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, delivery)));
        generator.wrap(output, 0, output.capacity());
        pipeline.reset();

        byte[] bytes = (json + " ").getBytes(UTF_8);
        pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);
    }
}
