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

import jakarta.json.stream.JsonParser;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class JsonSchemaPathsTest
{
    @Test
    void shouldCollectTopLevelProperties()
    {
        assertEquals(List.of("/a", "/b"),
            retained("{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"integer\"},\"b\":{\"type\":\"string\"}}}"));
    }

    @Test
    void shouldCollectNestedLeaf()
    {
        assertEquals(List.of("/a/b"),
            retained("{\"properties\":{\"a\":{\"type\":\"object\",\"properties\":{\"b\":{\"type\":\"integer\"}}}}}"));
    }

    @Test
    void shouldCollectArrayItemsWildcard()
    {
        assertEquals(List.of("/items/-/id"),
            retained("{\"properties\":{\"items\":{\"type\":\"array\",\"items\":" +
                "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}}}}}}"));
    }

    @Test
    void shouldTreatStructurelessObjectAsRetainedLeaf()
    {
        assertEquals(List.of("/meta"),
            retained("{\"properties\":{\"meta\":{\"type\":\"object\"}}}"));
    }

    @Test
    void shouldTreatScalarRootAsWholeDocument()
    {
        assertEquals(List.of(""), retained("{\"type\":\"string\"}"));
    }

    @Test
    void shouldUnionCombinatorBranches()
    {
        assertEquals(List.of("/a", "/b"),
            retained("{\"oneOf\":[{\"properties\":{\"a\":{\"type\":\"integer\"}}}," +
                "{\"properties\":{\"b\":{\"type\":\"string\"}}}]}"));
    }

    @Test
    void shouldUnionIfThenElseIncludingCondition()
    {
        assertEquals(List.of("/t", "/y", "/z"),
            retained("{\"if\":{\"properties\":{\"t\":{\"const\":\"x\"}}}," +
                "\"then\":{\"properties\":{\"y\":{\"type\":\"integer\"}}}," +
                "\"else\":{\"properties\":{\"z\":{\"type\":\"integer\"}}}}"));
    }

    @Test
    void shouldEscapePointerSegments()
    {
        assertEquals(List.of("/a~1b"),
            retained("{\"properties\":{\"a/b\":{\"type\":\"integer\"}}}"));
    }

    @Test
    void shouldHonourBooleanSubschemas()
    {
        assertEquals(List.of("/a"),
            retained("{\"properties\":{\"a\":true,\"b\":false}}"));
    }

    @Test
    void shouldTreatTupleItemsAsRetainedLeaf()
    {
        assertEquals(List.of("/t"),
            retained("{\"properties\":{\"t\":{\"type\":\"array\",\"items\":[{\"type\":\"integer\"}]}}}"));
    }

    @Test
    void shouldDriveProjectorEndToEnd()
    {
        JsonGeneratorEx gen = StreamingJson.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0);
        JsonProjector projector = StreamingJson.createProjector(JsonSchemaPaths.retained(
            "{\"type\":\"object\",\"properties\":{" +
            "\"items\":{\"type\":\"array\",\"items\":{\"type\":\"object\"," +
            "\"properties\":{\"id\":{\"type\":\"integer\"}}}}}}"),
            JsonEventConsumer.of(gen));
        projector.reset();
        projector.pump(parserFor("{\"items\":[{\"id\":1,\"x\":9},{\"id\":2}],\"k\":0} "));
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{\"items\":[{\"id\":1},{\"id\":2}]}", new String(out, UTF_8));
    }

    private static List<String> retained(
        String schema)
    {
        return JsonSchemaPaths.retained(schema);
    }

    private static JsonParser parserFor(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
        return StreamingJson.createParser(in);
    }
}
