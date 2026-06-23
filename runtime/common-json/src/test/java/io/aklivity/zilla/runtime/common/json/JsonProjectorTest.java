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
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonProjectorTest
{
    @Test
    void shouldRetainTopLevelProperty()
    {
        assertEquals("{\"name\":\"x\"}",
            project(List.of("/name"), "{\"name\":\"x\",\"secret\":\"y\"}"));
    }

    @Test
    void shouldRetainNestedLeaf()
    {
        assertEquals("{\"a\":{\"b\":1}}",
            project(List.of("/a/b"), "{\"a\":{\"b\":1,\"c\":2},\"d\":3}"));
    }

    @Test
    void shouldRetainArrayWildcardField()
    {
        assertEquals("{\"items\":[{\"id\":1},{\"id\":2}]}",
            project(List.of("/items/-/id"),
                "{\"items\":[{\"id\":1,\"x\":9},{\"id\":2,\"y\":8}],\"k\":0}"));
    }

    @Test
    void shouldRetainExplicitArrayIndex()
    {
        assertEquals("{\"items\":[\"b\"]}",
            project(List.of("/items/1"), "{\"items\":[\"a\",\"b\",\"c\"]}"));
    }

    @Test
    void shouldNotMatchInvalidArrayIndexes()
    {
        assertEquals("{\"items\":[]}",
            project(List.of("/items/01", "/items/999999999999999999999"),
                "{\"items\":[\"a\",\"b\",\"c\"]}"));
    }

    @Test
    void shouldKeepWholeRetainedSubtree()
    {
        assertEquals("{\"a\":{\"b\":1,\"c\":[1,2]}}",
            project(List.of("/a"), "{\"a\":{\"b\":1,\"c\":[1,2]},\"z\":9}"));
    }

    @Test
    void shouldRetainMultiplePointers()
    {
        assertEquals("{\"a\":1,\"c\":3}",
            project(List.of("/a", "/c"), "{\"a\":1,\"b\":2,\"c\":3}"));
    }

    @Test
    void shouldProjectRootAsCompactIdentity()
    {
        assertEquals("{\"a\":[1,{\"b\":2}],\"c\":null}",
            project(List.of(""), "{ \"a\" : [1, {\"b\": 2}], \"c\": null }"));
    }

    @Test
    void shouldEmitEmptyObjectWhenNothingRetained()
    {
        assertEquals("{}", project(List.of("/none"), "{\"a\":1,\"b\":2}"));
    }

    @Test
    void shouldDropScalarWhereDeeperPathRetained()
    {
        // /a/b is retained but a is a scalar in the data — nothing to descend into
        assertEquals("{}", project(List.of("/a/b"), "{\"a\":5}"));
    }

    @Test
    void shouldRetainSelectedKeysInNestedObject()
    {
        assertEquals("{\"meta\":{\"id\":7}}",
            project(List.of("/meta/id"),
                "{\"meta\":{\"id\":7,\"ts\":\"now\"},\"body\":{\"big\":\"drop\"}}"));
    }

    @Test
    void shouldProjectAcrossFramesWithoutReset()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBufferEx(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/a", "/c")))
            .into(JsonEx.createSink(gen));

        byte[] bytes = "{\"a\":1,\"b\":2,\"c\":3} ".getBytes(UTF_8);
        UnsafeBufferEx in = new UnsafeBufferEx(bytes);

        pipeline.reset();
        assertEquals(Status.STARVED, pipeline.transform(in, 0, 7, false));
        Status status = pipeline.transform(in, 7, bytes.length);

        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{\"a\":1,\"c\":3} ", new String(out, UTF_8));
    }

    @Test
    void shouldResetForReuseAcrossValues()
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBufferEx(new byte[1024]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/x")))
            .into(JsonEx.createSink(gen));

        gen.wrap(buffer, 0, buffer.capacity());
        feed(pipeline, "{\"x\":1,\"y\":2} ");
        byte[] out1 = new byte[gen.length()];
        buffer.getBytes(0, out1);
        assertEquals("{\"x\":1} ", new String(out1, UTF_8));

        gen.wrap(buffer, 0, buffer.capacity());
        feed(pipeline, "{\"x\":\"two\"} ");
        byte[] out2 = new byte[gen.length()];
        buffer.getBytes(0, out2);
        assertEquals("{\"x\":\"two\"} ", new String(out2, UTF_8));
    }

    @Test
    void shouldProjectRootScalar()
    {
        assertEquals("42", project(List.of(""), "42"));
    }

    @Test
    void shouldProjectArrayOfScalarsWithIndex()
    {
        assertEquals("[\"a\",\"c\"]", project(List.of("/0", "/2"), "[\"a\",\"b\",\"c\"]"));
    }

    @Test
    void shouldMatchNumericObjectKeyByIndexPointer()
    {
        // an index-looking segment matches a literal object key with that name when the data is an object
        assertEquals("{\"items\":{\"1\":\"x\"}}",
            project(List.of("/items/1"), "{\"items\":{\"1\":\"x\",\"2\":\"y\"}}"));
    }

    @Test
    void shouldRetainDeeplyNestedArrayWildcardLeaf()
    {
        assertEquals("{\"a\":[{\"b\":1},{\"b\":3}]}",
            project(List.of("/a/-/b"), "{\"a\":[{\"b\":1,\"x\":2},{\"b\":3,\"y\":4}],\"z\":9}"));
    }

    @Test
    void shouldRetainLiteralHyphenObjectKey()
    {
        // "-" is the array wildcard, but against an object it matches a literal "-" key, not every member
        assertEquals("{\"-\":1}",
            project(List.of("/-"), "{\"-\":1,\"b\":2}"));
    }

    @Test
    void shouldNotDuplicateKeptKeysAcrossOutputBoundSuspends()
    {
        // kept keys are forwarded live at the key event, so a key write can land on an output-bound
        // boundary and suspend; the drain/resume must advance to the value without re-emitting the key
        assertEquals("{\"alpha\":1,\"gamma\":3,\"epsilon\":5} ",
            projectBounded(List.of("/alpha", "/gamma", "/epsilon"),
                "{\"alpha\":1,\"beta\":2,\"gamma\":3,\"delta\":4,\"epsilon\":5}", 12));
    }

    private static String project(
        List<String> retained,
        String input)
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBufferEx(new byte[1024]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(retained))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        feed(pipeline, input + " ");
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }

    // Drives the projection through an output buffer bounded at outBound, draining and re-targeting the
    // generator on each SUSPENDED and concatenating the drained chunks into one document.
    private static String projectBounded(
        List<String> retained,
        String input,
        int outBound)
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBufferEx(new byte[outBound]);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(retained))
            .into(JsonEx.createSink(gen));

        byte[] bytes = (input + " ").getBytes(UTF_8);
        UnsafeBufferEx in = new UnsafeBufferEx(bytes);
        StringBuilder result = new StringBuilder();
        pipeline.reset();
        gen.wrap(buffer, 0, outBound);
        int guard = 0;
        Status status;
        do
        {
            status = pipeline.transform(in, 0, bytes.length);
            byte[] chunk = new byte[gen.length()];
            buffer.getBytes(0, chunk);
            result.append(new String(chunk, UTF_8));
            if (status == Status.SUSPENDED)
            {
                gen.wrap(buffer, 0, outBound);
            }
            guard++;
        }
        while (status == Status.SUSPENDED && guard < 10_000);
        assertEquals(Status.COMPLETED, status);
        return result.toString();
    }

    private static Status feed(
        JsonPipeline pipeline,
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        pipeline.reset();
        return pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);
    }
}
