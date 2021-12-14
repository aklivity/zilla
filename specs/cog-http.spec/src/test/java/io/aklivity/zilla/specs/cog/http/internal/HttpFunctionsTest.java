/*
 * Copyright 2021-2021 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.specs.cog.http.internal;

import static io.aklivity.zilla.specs.cog.http.internal.HttpFunctions.copyOfRange;
import static io.aklivity.zilla.specs.cog.http.internal.HttpFunctions.randomAscii;
import static io.aklivity.zilla.specs.cog.http.internal.HttpFunctions.randomBytes;
import static io.aklivity.zilla.specs.cog.http.internal.HttpFunctions.randomBytesInvalidUTF8;
import static io.aklivity.zilla.specs.cog.http.internal.HttpFunctions.randomBytesUTF8;
import static io.aklivity.zilla.specs.cog.http.internal.HttpFunctions.randomBytesUnalignedUTF8;
import static io.aklivity.zilla.specs.cog.http.internal.HttpFunctions.randomCaseNot;
import static io.aklivity.zilla.specs.cog.http.internal.HttpFunctions.randomHeaderNot;
import static io.aklivity.zilla.specs.cog.http.internal.HttpFunctions.randomInvalidVersion;
import static io.aklivity.zilla.specs.cog.http.internal.HttpFunctions.randomMethodNot;
import static io.aklivity.zilla.specs.cog.http.internal.HttpFunctions.randomizeLetterCase;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.MalformedInputException;

import javax.el.ELContext;
import javax.el.FunctionMapper;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;

import io.aklivity.zilla.specs.cog.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.specs.cog.http.internal.types.stream.HttpChallengeExFW;
import io.aklivity.zilla.specs.cog.http.internal.types.stream.HttpDataExFW;
import io.aklivity.zilla.specs.cog.http.internal.types.stream.HttpEndExFW;

public class HttpFunctionsTest
{
    @Test
    public void shouldResolveFunction() throws Exception
    {
        final ELContext ctx = new ExpressionContext();
        final FunctionMapper mapper = ctx.getFunctionMapper();
        final Method function = mapper.resolveFunction("http", "randomInvalidVersion");

        assertNotNull(function);
        assertSame(HttpFunctions.class, function.getDeclaringClass());
    }

    @Test
    public void shouldRandomizeInvalidVersion() throws Exception
    {
        final String version = randomInvalidVersion();

        assertNotEquals("HTTP/1.1", version);
    }

    @Test
    public void shouldRandomizeMethodNotGet() throws Exception
    {
        final String method = randomMethodNot("GET");

        assertNotEquals("GET", method);
    }

    @Test
    public void shouldRandomizeHeaderNotAuthorization() throws Exception
    {
        final String header = randomHeaderNot("authorization");

        assertNotEquals("authorization", header);
    }

    @Test
    public void shouldRandomizeCase() throws Exception
    {
        final String randomizedCase = randomizeLetterCase("aBcdEfGHiJ");

        assertEquals("abcdefghij", randomizedCase.toLowerCase());
    }

    @Test
    public void shouldRandomizeCaseNotIdentical() throws Exception
    {
        final String randomizedCase = randomCaseNot("aBcdEfGHiJ");

        assertNotEquals("aBcdEfGHiJ", randomizedCase);
        assertEquals("abcdefghij", randomizedCase.toLowerCase());
    }

    @Test
    public void shouldRandomizeBytes() throws Exception
    {
        final byte[] bytes = randomBytes(42);

        assertNotNull(bytes);
        assertEquals(42, bytes.length);
    }

    @Test
    public void shouldCopyRangeOfBytes() throws Exception
    {
        final byte[] bytes = new byte[42];
        for (int i = 0; i < bytes.length; i++)
        {
            bytes[i] = (byte) i;
        }

        final byte[] range = copyOfRange(bytes, 5, 10);

        assertNotNull(range);
        assertEquals(5, range.length);

        for (int i = 0; i < range.length; i++)
        {
            assertEquals(i + 5, range[i]);
        }
    }

    @Test
    public void shouldRandomizeBytesAscii() throws Exception
    {
        final byte[] ascii = randomAscii(42);

        assertNotNull(ascii);
        assertEquals(42, ascii.length);

        US_ASCII.newDecoder().decode(ByteBuffer.wrap(ascii));
    }

    @Test
    public void shouldRandomizeBytesUTF8() throws Exception
    {
        final byte[] bytes = randomBytesUTF8(42);

        assertNotNull(bytes);
        assertEquals(42, bytes.length);

        UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
    }

    @Test
    public void shouldRandomizeBytesUnalignedUTF8() throws Exception
    {
        final byte[] bytes = randomBytesUnalignedUTF8(42, 20);

        assertNotNull(bytes);
        assertEquals(42, bytes.length);

        UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
    }

    @Test(expected = AssertionError.class)
    public void shouldRandomizeBytesUnalignedUTF8OutOfBounds() throws Exception
    {
        randomBytesUnalignedUTF8(42, 43);
    }

    @Test(expected = AssertionError.class)
    public void shouldRandomizeBytesUnalignedUTF8Negative() throws Exception
    {
        randomBytesUnalignedUTF8(42, -1);
    }

    @Test(expected = MalformedInputException.class)
    public void shouldRandomizeBytesInvalidUTF8() throws Exception
    {
        final byte[] bytes = randomBytesInvalidUTF8(42);

        assertNotNull(bytes);
        assertEquals(42, bytes.length);

        UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
    }

    @Test
    public void shouldGenerateBeginExtension()
    {
        byte[] build = HttpFunctions.beginEx()
                                    .typeId(0x01)
                                    .header("name", "value")
                                    .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        HttpBeginExFW beginEx = new HttpBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, beginEx.typeId());
        beginEx.headers().forEach(onlyHeader ->
        {
            assertEquals("name", onlyHeader.name().asString());
            assertEquals("value", onlyHeader.value().asString());
        });
        assertTrue(beginEx.headers().sizeof() > 0);
    }

    @Test
    public void shouldMatchBeginExtension() throws Exception
    {
        BytesMatcher matcher = HttpFunctions.matchBeginEx()
                                            .typeId(0x01)
                                            .header("name", "value")
                                            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new HttpBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .headersItem(h -> h.name("name")
                               .value("value"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchBeginExtensionWithRegex() throws Exception
    {
        BytesMatcher matcher = HttpFunctions.matchBeginEx()
                                            .typeId(0x01)
                                            .headerRegex("name", "value")
                                            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new HttpBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .headersItem(h -> h.name("name")
                               .value("value"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailWhenDoNotSetTypeId() throws Exception
    {
        BytesMatcher matcher = HttpFunctions.matchBeginEx()
                                            .headerRegex("name", "value")
                                            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new HttpBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .headersItem(h -> h.name("name")
                               .value("value"))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldFailWhenTypeIdDoNotMatch() throws Exception
    {
        BytesMatcher matcher = HttpFunctions.matchBeginEx()
                                            .typeId(0x01)
                                            .headerRegex("name", "value")
                                            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new HttpBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x02)
            .headersItem(h -> h.name("name")
                               .value("value"))
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldFailWhenDoNotMatchBeginExtensionWithRegex() throws Exception
    {
        BytesMatcher matcher = HttpFunctions.matchBeginEx()
                                            .typeId(0x01)
                                            .headerRegex("name", "regex")
                                            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new HttpBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .headersItem(h -> h.name("name")
                               .value("value"))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldFailWhenBufferDoNotHaveEnoughSpace() throws Exception
    {
        BytesMatcher matcher = HttpFunctions.matchBeginEx()
                                            .typeId(0x01)
                                            .headerRegex("name", "value")
                                            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(0);

        assertNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateDataExtension()
    {
        byte[] build = HttpFunctions.dataEx()
                                    .typeId(0x01)
                                    .promise("name", "value")
                                    .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        HttpDataExFW dataEx = new HttpDataExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, dataEx.typeId());
        dataEx.promise().forEach(onlyHeader ->
        {
            assertEquals("name", onlyHeader.name().asString());
            assertEquals("value", onlyHeader.value().asString());
        });
        assertTrue(dataEx.promise().sizeof() > 0);
    }

    @Test
    public void shouldGenerateEndExtension()
    {
        byte[] build = HttpFunctions.endEx()
                                    .typeId(0x01)
                                    .trailer("name", "value")
                                    .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        HttpEndExFW endEx = new HttpEndExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, endEx.typeId());
        endEx.trailers().forEach(onlyHeader ->
        {
            assertEquals("name", onlyHeader.name().asString());
            assertEquals("value", onlyHeader.value().asString());
        });
        assertTrue(endEx.trailers().sizeof() > 0);
    }

    @Test
    public void shouldGenerateChallengeExtension()
    {
        byte[] build = HttpFunctions.challengeEx()
                                    .typeId(0x01)
                                    .header("name", "value")
                                    .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        HttpChallengeExFW challengeEx = new HttpChallengeExFW().wrap(buffer, 0, buffer.capacity());
        assertEquals(0x01, challengeEx.typeId());
        challengeEx.headers().forEach(onlyHeader ->
        {
            assertEquals("name", onlyHeader.name().asString());
            assertEquals("value", onlyHeader.value().asString());
        });
        assertTrue(challengeEx.headers().sizeof() > 0);
    }
}
