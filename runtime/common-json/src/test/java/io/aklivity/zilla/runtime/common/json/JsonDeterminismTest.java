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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonDeterminismTest
{
    private static final JsonTransform NON_DETERMINISTIC = new JsonTransform()
    {
        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            return sink.transform(control, source, event);
        }

        @Override
        public boolean deterministic()
        {
            return false;
        }
    };

    @Test
    void shouldBeDeterministicWithoutTransforms()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .into(JsonEx.createGenerator());

        assertTrue(pipeline.deterministic());
    }

    @Test
    void shouldBeDeterministicWithDeterministicTransforms()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of("{\"type\":\"object\"}").validator())
            .transform(JsonTransforms.projector(List.of("/a")))
            .transform(JsonTransforms.flatten(Map.of("a.b", "b")))
            .into(JsonEx.createGenerator());

        assertTrue(pipeline.deterministic());
    }

    @Test
    void shouldNotBeDeterministicWithNonDeterministicTransform()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(NON_DETERMINISTIC)
            .into(JsonEx.createGenerator());

        assertFalse(pipeline.deterministic());
    }

    @Test
    void shouldNotBeDeterministicWithNonDeterministicTransformAmongDeterministic()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonTransforms.projector(List.of("/a")))
            .transform(NON_DETERMINISTIC)
            .transform(JsonTransforms.projector(List.of("/a")))
            .into(JsonEx.createGenerator());

        assertFalse(pipeline.deterministic());
    }

    @Test
    void shouldNotBeDeterministicWithNonDeterministicTransformAndStructuredDelivery()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(NON_DETERMINISTIC)
            .into(JsonEx.createGenerator(), Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED));

        assertFalse(pipeline.deterministic());
    }

    @Test
    void shouldBeDeterministicForParserAndGeneratorLeaves()
    {
        assertTrue(JsonEx.createParser().deterministic());
        assertTrue(JsonEx.createGenerator().deterministic());
    }

    @Test
    void shouldDelegateSinkDeterminismToGenerator()
    {
        JsonSink sink = JsonEx.createSink(JsonEx.createGenerator());

        assertTrue(sink.deterministic());
    }
}
