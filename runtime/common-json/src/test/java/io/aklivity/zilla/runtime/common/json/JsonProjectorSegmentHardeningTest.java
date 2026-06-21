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

import java.util.List;
import java.util.Map;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonProjectorSegmentHardeningTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);

    @Test
    void shouldSegmentDeeplyNestedKeptSubtreeAsSingleVerbatimValue()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/a")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        Status status = run(pipeline, "{\"a\":{ \"b\" : { \"c\" : [1, {\"d\": 2}] } },\"z\":9} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":{ \"b\" : { \"c\" : [1, {\"d\": 2}] } }}", output(gen));
    }

    @Test
    void shouldResetForReuseAcrossSegmentedValues()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/x")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE)));

        gen.wrap(buffer, 0, buffer.capacity());
        run(pipeline, "{\"x\":{ \"v\" : 1 },\"y\":2} ");
        assertEquals("{\"x\":{ \"v\" : 1 }}", output(gen));

        gen.wrap(buffer, 0, buffer.capacity());
        run(pipeline, "{\"x\":[9 ,8]} ");
        assertEquals("{\"x\":[9 ,8]}", output(gen));
    }

    @Test
    void shouldNormalizeNestedWhenNotOptedIn()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/a")))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));

        Status status = run(pipeline, "{\"a\":{ \"b\" : { \"c\" : [1, {\"d\": 2}] } },\"z\":9} ");

        assertEquals(Status.COMPLETED, status);
        assertEquals("{\"a\":{\"b\":{\"c\":[1,{\"d\":2}]}}}", output(gen));
    }

    private String output(
        JsonGeneratorEx gen)
    {
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }

    private static Status run(
        JsonPipeline pipeline,
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        pipeline.reset();
        return pipeline.feed(new UnsafeBuffer(bytes), 0, bytes.length);
    }
}
