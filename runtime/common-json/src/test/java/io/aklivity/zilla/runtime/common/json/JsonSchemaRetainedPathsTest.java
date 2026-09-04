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

import java.util.List;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class JsonSchemaRetainedPathsTest
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
    void shouldCollectWildcardWhenAdditionalPropertiesAllowed()
    {
        assertEquals(List.of("/a", "/*"),
            retained("{\"properties\":{\"a\":{\"type\":\"integer\"}},\"additionalProperties\":true}"));
    }

    @Test
    void shouldCollectWildcardForTypedAdditionalPropertiesSchema()
    {
        assertEquals(List.of("/a", "/*"),
            retained("{\"properties\":{\"a\":{\"type\":\"integer\"}},\"additionalProperties\":{\"type\":\"string\"}}"));
    }

    @Test
    void shouldNotCollectWildcardWhenAdditionalPropertiesAbsent()
    {
        assertEquals(List.of("/a"),
            retained("{\"properties\":{\"a\":{\"type\":\"integer\"}}}"));
    }

    @Test
    void shouldNotCollectWildcardWhenAdditionalPropertiesFalse()
    {
        assertEquals(List.of("/a"),
            retained("{\"properties\":{\"a\":{\"type\":\"integer\"}},\"additionalProperties\":false}"));
    }

    @Test
    void shouldCollectRejectedPathForFalseProperty()
    {
        assertEquals(List.of("/b"), rejected("{\"properties\":{\"a\":true,\"b\":false}}"));
    }

    @Test
    void shouldCollectRejectedPathAlongsideAdditionalPropertiesWildcard()
    {
        JsonSchema schema = JsonSchema.of(
            "{\"type\":\"object\",\"properties\":{\"connector.class\":{\"type\":\"string\"}," +
            "\"connector\":false},\"additionalProperties\":true}");
        assertEquals(List.of("/connector.class", "/*"), schema.retainedPaths());
        assertEquals(List.of("/connector"), schema.rejectedPaths());
    }

    @Test
    void shouldHaveNoRejectedPathsWhenNoPropertyIsDenied()
    {
        assertEquals(List.of(), rejected("{\"properties\":{\"a\":{\"type\":\"integer\"}}}"));
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
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonSchema schema = JsonSchema.of(
            "{\"type\":\"object\",\"properties\":{" +
            "\"items\":{\"type\":\"array\",\"items\":{\"type\":\"object\"," +
            "\"properties\":{\"id\":{\"type\":\"integer\"}}}}}}");
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonTransforms.projector(schema.retainedPaths()))
            .into(JsonEx.createSink(gen));
        pipeline.reset();
        byte[] bytes = "{\"items\":[{\"id\":1,\"x\":9},{\"id\":2}],\"k\":0} ".getBytes(UTF_8);
        pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{\"items\":[{\"id\":1},{\"id\":2}]} ", new String(out, UTF_8));
    }

    @Test
    void shouldDriveProjectorEndToEndWithAdditionalProperties()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonSchema schema = JsonSchema.of(
            "{\"type\":\"object\",\"properties\":{\"connector.class\":{\"type\":\"string\"}}," +
            "\"additionalProperties\":true}");
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonTransforms.projector(schema.retainedPaths()))
            .into(JsonEx.createSink(gen));
        pipeline.reset();
        byte[] bytes = "{\"connector.class\":\"FileStreamSource\",\"file\":\"/tmp/x\",\"topic\":\"t\"} "
            .getBytes(UTF_8);
        pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{\"connector.class\":\"FileStreamSource\",\"file\":\"/tmp/x\",\"topic\":\"t\"} ",
            new String(out, UTF_8));
    }

    @Test
    void shouldDriveProjectorEndToEndRejectingNamedPropertyOverWildcard()
    {
        // "connector" shares the same object as connector.class and the open-ended config fields (the
        // shape of a tools/call arguments object carrying both a path parameter and a generic body), and
        // must stay excluded even though additionalProperties would otherwise keep it via the wildcard
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonSchema schema = JsonSchema.of(
            "{\"type\":\"object\",\"properties\":{\"connector.class\":{\"type\":\"string\"}," +
            "\"connector\":false},\"additionalProperties\":true}");
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonTransforms.projector(schema))
            .into(JsonEx.createSink(gen));
        pipeline.reset();
        byte[] bytes = ("{\"connector\":\"connector1\",\"connector.class\":\"FileStreamSource\"," +
            "\"topic\":\"t\"} ").getBytes(UTF_8);
        pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{\"connector.class\":\"FileStreamSource\",\"topic\":\"t\"} ", new String(out, UTF_8));
    }

    private static List<String> retained(
        String schema)
    {
        return JsonSchema.of(schema).retainedPaths();
    }

    private static List<String> rejected(
        String schema)
    {
        return JsonSchema.of(schema).rejectedPaths();
    }
}
