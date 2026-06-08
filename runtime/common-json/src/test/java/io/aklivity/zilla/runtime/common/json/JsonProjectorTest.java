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

import io.aklivity.zilla.runtime.common.json.JsonEventConsumer.Status;

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
    void shouldProjectByFeedingEventByEvent()
    {
        JsonGeneratorEx gen = StreamingJson.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0);
        JsonProjector projector = StreamingJson.createProjector(List.of("/a", "/c"), JsonEventConsumer.of(gen));
        JsonParser parser = parserFor("{\"a\":1,\"b\":2,\"c\":3} ");
        Status status = Status.PENDING;
        while (status == Status.PENDING && parser.hasNext())
        {
            status = projector.feed(parser.next(), parser);
        }
        assertEquals(Status.COMPLETE, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        assertEquals("{\"a\":1,\"c\":3}", new String(out, UTF_8));
    }

    @Test
    void shouldResetForReuseAcrossValues()
    {
        JsonGeneratorEx gen = StreamingJson.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        JsonProjector projector = StreamingJson.createProjector(List.of("/x"), JsonEventConsumer.of(gen));

        gen.wrap(buffer, 0);
        projector.reset();
        projector.pump(parserFor("{\"x\":1,\"y\":2} "));
        byte[] out1 = new byte[gen.length()];
        buffer.getBytes(0, out1);
        assertEquals("{\"x\":1}", new String(out1, UTF_8));

        gen.wrap(buffer, 0);
        projector.reset();
        projector.pump(parserFor("{\"x\":\"two\"} "));
        byte[] out2 = new byte[gen.length()];
        buffer.getBytes(0, out2);
        assertEquals("{\"x\":\"two\"}", new String(out2, UTF_8));
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

    private static String project(
        List<String> retained,
        String input)
    {
        JsonGeneratorEx gen = StreamingJson.createGenerator();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        gen.wrap(buffer, 0);
        JsonProjector projector = StreamingJson.createProjector(retained, JsonEventConsumer.of(gen));
        projector.reset();
        projector.pump(parserFor(input + " "));
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
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
