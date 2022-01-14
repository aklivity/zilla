/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.http.internal;

import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableBoolean;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.specs.binding.http.internal.types.stream.HttpChallengeExFW;
import io.aklivity.zilla.specs.binding.http.internal.types.stream.HttpDataExFW;
import io.aklivity.zilla.specs.binding.http.internal.types.stream.HttpEndExFW;

public final class HttpFunctions
{
    @Function
    public static HttpBeginExBuilder beginEx()
    {
        return new HttpBeginExBuilder();
    }

    @Function
    public static HttpBeginExMatcherBuilder matchBeginEx()
    {
        return new HttpBeginExMatcherBuilder();
    }

    @Function
    public static HttpDataExBuilder dataEx()
    {
        return new HttpDataExBuilder();
    }

    @Function
    public static HttpEndExBuilder endEx()
    {
        return new HttpEndExBuilder();
    }

    @Function
    public static HttpChallengeExBuilder challengeEx()
    {
        return new HttpChallengeExBuilder();
    }

    @Function
    public static String randomInvalidVersion()
    {
        Random random = ThreadLocalRandom.current();
        String randomVersion = null;
        Pattern validVersionPattern = Pattern.compile("HTTP/1\\.(\\d)+");
        Matcher validVersionMatcher = null;
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                       "1234567890!@#$%^&*()_+-=`~[]\\{}|;':\",./<>?";
        StringBuilder result;
        do
        {
            result = new StringBuilder();
            int randomLength = random.nextInt(30) + 1;
            for (int i = 0; i < randomLength; i++)
            {
                result.append(chars.charAt(random.nextInt(chars.length())));
            }
            randomVersion = result.toString();
            validVersionMatcher = validVersionPattern.matcher(randomVersion);
        }
        while (randomVersion.length() > 1 && validVersionMatcher.matches());
        return randomVersion;
    }

    @Function
    public static String randomMethodNot(
        String method)
    {
        Random random = ThreadLocalRandom.current();
        String[] methods = new String[]{"GET", "OPTIONS", "HEAD", "POST", "PUT", "DELETE", "TRACE", "CONNECT"};
        String result;
        do
        {
            result = methods[random.nextInt(methods.length)];
        } while (result.equalsIgnoreCase(method));
        return result;
    }

    @Function
    public static String randomHeaderNot(
        String header)
    {
        Random random = ThreadLocalRandom.current();
        // random strings from bytes can generate random bad chars like \n \r \f \v etc which are not allowed
        // except under special conditions, and will crash the http pipeline
        String commonHeaderChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                "1234567890!@#$%^&*()_+-=`~[]\\{}|;':\",./<>?";
        StringBuilder result = new StringBuilder();
        do
        {
            int maxHeaderLength = 200;
            int randomHeaderLength = random.nextInt(maxHeaderLength) + 1;
            for (int i = 0; i < randomHeaderLength; i++)
            {
                result.append(commonHeaderChars.charAt(random.nextInt(commonHeaderChars.length())));
            }
        } while (result.toString().equalsIgnoreCase(header));
        return result.toString();
    }

    @Function
    public static String randomizeLetterCase(
        String text)
    {
        Random random = ThreadLocalRandom.current();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (random.nextBoolean())
            {
                c = toUpperCase(c);
            }
            else
            {
                c = toLowerCase(c);
            }
            result.append(c);
        }
        return result.toString();
    }

    @Function
    public static String randomCaseNot(
        String value)
    {
        Random random = ThreadLocalRandom.current();

        String result;
        char[] resultChars = new char[value.length()];

        do
        {
            for (int i = 0; i < value.length(); i++)
            {
                char c = value.charAt(i);
                resultChars[i] = random.nextBoolean() ? toUpperCase(c) : toLowerCase(c);
            }
            result = new String(resultChars);
        } while (result.equals(value));

        return result;
    }

    @Function
    public static byte[] copyOfRange(
        byte[] original,
        int from,
        int to)
    {
        return Arrays.copyOfRange(original, from, to);
    }

    @Function
    public static byte[] randomAscii(
        int length)
    {
        Random random = ThreadLocalRandom.current();
        byte[] result = new byte[length];
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                          "1234567890!@#$%^&*()_+-=`~[]\\{}|;':\",./<>?";
        for (int i = 0; i < length; i++)
        {
            result[i] = (byte) alphabet.charAt(random.nextInt(alphabet.length()));
        }
        return result;
    }

    @Function
    public static byte[] randomBytes(
        int length)
    {
        Random random = ThreadLocalRandom.current();
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++)
        {
            bytes[i] = (byte) random.nextInt(0x100);
        }
        return bytes;
    }

    @Function
    public static byte[] randomBytesUTF8(
        int length)
    {
        byte[] bytes = new byte[length];
        randomBytesUTF8(bytes, 0, length);
        return bytes;
    }

    @Function
    public static byte[] randomBytesInvalidUTF8(
        int length)
    {
        // TODO: make invalid UTF-8 bytes less like valid UTF-8 (!)
        byte[] bytes = new byte[length];
        bytes[0] = (byte) 0x80;
        randomBytesUTF8(bytes, 1, length - 1);
        return bytes;
    }

    @Function
    public static byte[] randomBytesUnalignedUTF8(
        int length,
        int unalignAt)
    {
        assert -1 < unalignAt && unalignAt < length;

        Random random = ThreadLocalRandom.current();
        byte[] bytes = new byte[length];
        int straddleWidth = random.nextInt(3) + 2;
        int straddleAt = unalignAt - straddleWidth + 1;
        randomBytesUTF8(bytes, 0, straddleAt);
        int realignAt = randomCharBytesUTF8(bytes, straddleAt, straddleWidth);
        randomBytesUTF8(bytes, realignAt, length);
        return bytes;
    }

    public static final class HttpBeginExBuilder
    {
        private final HttpBeginExFW.Builder beginExRW;

        private HttpBeginExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.beginExRW = new HttpBeginExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public HttpBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public HttpBeginExBuilder header(
            String name,
            String value)
        {
            beginExRW.headersItem(b -> b.name(name).value(value));
            return this;
        }

        public byte[] build()
        {
            final HttpBeginExFW beginEx = beginExRW.build();
            final byte[] array = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), array);
            return array;
        }
    }

    public static final class HttpBeginExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final HttpBeginExFW beginExRO = new HttpBeginExFW();

        private final Map<String, Predicate<String>> headers = new LinkedHashMap<>();

        private Integer typeId;

        public HttpBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public HttpBeginExMatcherBuilder header(
            String name,
            String value)
        {
            headers.put(name, value::equals);
            return this;
        }

        public HttpBeginExMatcherBuilder headerRegex(
            String name,
            String regex)
        {
            Pattern pattern = Pattern.compile(regex);
            headers.put(name, v -> pattern.matcher(v).matches());
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null ? this::match : buf -> null;
        }

        private HttpBeginExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final HttpBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (beginEx != null &&
                matchTypeId(beginEx) &&
                matchHeaders(beginEx))
            {
                byteBuf.position(byteBuf.position() + beginEx.sizeof());
                return beginEx;
            }

            throw new Exception(beginEx.toString());
        }

        private boolean matchHeaders(
            HttpBeginExFW beginEx)
        {
            MutableBoolean match = new MutableBoolean(true);
            headers.forEach((k, v) -> match.value &= beginEx.headers().anyMatch(h -> k.equals(h.name().asString()) &&
                                                                           v.test(h.value().asString())));
            return match.value;
        }

        private boolean matchTypeId(
            HttpBeginExFW beginEx)
        {
            return typeId == beginEx.typeId();
        }
    }

    public static final class HttpDataExBuilder
    {
        private final HttpDataExFW.Builder dataExRW;

        private HttpDataExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.dataExRW = new HttpDataExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public HttpDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public HttpDataExBuilder promise(
            String name,
            String value)
        {
            dataExRW.promiseItem(b -> b.name(name).value(value));
            return this;
        }

        public byte[] build()
        {
            final HttpDataExFW dataEx = dataExRW.build();
            final byte[] array = new byte[dataEx.sizeof()];
            dataEx.buffer().getBytes(dataEx.offset(), array);
            return array;
        }
    }

    public static final class HttpEndExBuilder
    {
        private final HttpEndExFW.Builder endExRW;

        private HttpEndExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.endExRW = new HttpEndExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public HttpEndExBuilder typeId(
            int typeId)
        {
            endExRW.typeId(typeId);
            return this;
        }

        public HttpEndExBuilder trailer(
            String name,
            String value)
        {
            endExRW.trailersItem(b -> b.name(name).value(value));
            return this;
        }

        public byte[] build()
        {
            final HttpEndExFW endEx = endExRW.build();
            final byte[] array = new byte[endEx.sizeof()];
            endEx.buffer().getBytes(endEx.offset(), array);
            return array;
        }
    }

    public static final class HttpChallengeExBuilder
    {
        private final HttpChallengeExFW.Builder challengeExRW;

        private HttpChallengeExBuilder()
        {
            MutableDirectBuffer writeExBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.challengeExRW = new HttpChallengeExFW.Builder().wrap(writeExBuffer, 0, writeExBuffer.capacity());
        }

        public HttpChallengeExBuilder typeId(
            int typeId)
        {
            challengeExRW.typeId(typeId);
            return this;
        }

        public HttpChallengeExBuilder header(
            String name,
            String value)
        {
            challengeExRW.headersItem(b -> b.name(name).value(value));
            return this;
        }

        public byte[] build()
        {
            final HttpChallengeExFW challengeEx = challengeExRW.build();
            final byte[] array = new byte[challengeEx.sizeof()];
            challengeEx.buffer().getBytes(challengeEx.offset(), array);
            return array;
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(HttpFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "http";
        }
    }

    private static void randomBytesUTF8(
        byte[] bytes,
        int start,
        int end)
    {
        Random random = ThreadLocalRandom.current();
        for (int offset = start; offset < end;)
        {
            int remaining = end - offset;
            int width = Math.min(random.nextInt(4) + 1, remaining);

            offset = randomCharBytesUTF8(bytes, offset, width);
        }
    }

    private static int randomCharBytesUTF8(
        byte[] bytes,
        int offset,
        int width)
    {
        Random random = ThreadLocalRandom.current();
        switch (width)
        {
        case 1:
            bytes[offset++] = (byte) random.nextInt(0x80);
            break;
        case 2:
            bytes[offset++] = (byte) (0xc0 | random.nextInt(0x20) | 1 << (random.nextInt(4) + 1));
            bytes[offset++] = (byte) (0x80 | random.nextInt(0x40));
            break;
        case 3:
            // UTF-8 not legal for 0xD800 through 0xDFFF (see RFC 3269)
            bytes[offset++] = (byte) (0xe0 | random.nextInt(0x08) | 1 << random.nextInt(3));
            bytes[offset++] = (byte) (0x80 | random.nextInt(0x40));
            bytes[offset++] = (byte) (0x80 | random.nextInt(0x40));
            break;
        case 4:
            // UTF-8 ends at 0x10FFFF (see RFC 3269)
            bytes[offset++] = (byte) (0xf0 | random.nextInt(0x04) | 1 << random.nextInt(2));
            bytes[offset++] = (byte) (0x80 | random.nextInt(0x10));
            bytes[offset++] = (byte) (0x80 | random.nextInt(0x40));
            bytes[offset++] = (byte) (0x80 | random.nextInt(0x40));
            break;
        }
        return offset;
    }

    private HttpFunctions()
    {
        // utility
    }
}
