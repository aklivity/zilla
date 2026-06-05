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

import java.util.function.Consumer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class StreamingJsonGeneratorTest
{
    @Test
    void shouldWriteEmptyObject()
    {
        assertEquals("{}", generate(g -> g.startObject().end()));
    }

    @Test
    void shouldWriteEmptyArray()
    {
        assertEquals("[]", generate(g -> g.startArray().end()));
    }

    @Test
    void shouldWriteFlatObject()
    {
        assertEquals("{\"a\":1,\"b\":\"x\"}", generate(g -> g
            .startObject()
            .key("a").numberValue("1")
            .key("b").stringValue("x")
            .end()));
    }

    @Test
    void shouldWriteScalarArray()
    {
        assertEquals("[1,true,null,\"x\"]", generate(g -> g
            .startArray()
            .numberValue("1")
            .booleanValue(true)
            .nullValue()
            .stringValue("x")
            .end()));
    }

    @Test
    void shouldWriteNestedStructures()
    {
        assertEquals("{\"a\":[1,2,{\"b\":true}],\"c\":null}", generate(g -> g
            .startObject()
            .key("a").startArray()
                .numberValue("1")
                .numberValue("2")
                .startObject().key("b").booleanValue(true).end()
            .end()
            .key("c").nullValue()
            .end()));
    }

    @Test
    void shouldWriteNumberForms()
    {
        assertEquals("[-1,1.5,-2.5e3]", generate(g -> g
            .startArray()
            .numberValue("-1")
            .numberValue("1.5")
            .numberValue("-2.5e3")
            .end()));
    }

    @Test
    void shouldEscapeControlAndSpecialCharacters()
    {
        assertEquals("\"a\\\"b\\\\c\\nd\\te\\r\\b\\f\\u0001\"", generate(g ->
            g.stringValue("a\"b\\c\nd\te\r\b\f")));
    }

    @Test
    void shouldEncodeMultibyteUtf8()
    {
        assertEquals("\"é€😀\"", generate(g -> g.stringValue("é€😀")));
    }

    @Test
    void shouldWriteTopLevelScalar()
    {
        assertEquals("\"hello\"", generate(g -> g.stringValue("hello")));
    }

    @Test
    void shouldWriteRawValueVerbatim()
    {
        MutableDirectBuffer source = new UnsafeBuffer("42".getBytes(UTF_8));
        assertEquals("[42]", generate(g -> g
            .startArray()
            .rawValue(source, 0, 2)
            .end()));
    }

    @Test
    void shouldReportLength()
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[64]);
        StreamingJsonGenerator generator = new StreamingJsonGenerator().wrap(buffer, 0);
        generator.startObject().key("a").numberValue("1").end();
        assertEquals("{\"a\":1}".length(), generator.length());
    }

    @Test
    void shouldWriteAtNonZeroOffset()
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[64]);
        StreamingJsonGenerator generator = new StreamingJsonGenerator().wrap(buffer, 8);
        generator.startArray().numberValue("1").end();
        byte[] out = new byte[generator.length()];
        buffer.getBytes(8, out);
        assertEquals("[1]", new String(out, UTF_8));
    }

    private static String generate(
        Consumer<StreamingJsonGenerator> writer)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[512]);
        StreamingJsonGenerator generator = new StreamingJsonGenerator().wrap(buffer, 0);
        writer.accept(generator);
        byte[] out = new byte[generator.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }
}
