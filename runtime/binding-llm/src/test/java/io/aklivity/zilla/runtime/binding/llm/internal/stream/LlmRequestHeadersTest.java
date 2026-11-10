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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmRequestHeadersTest
{
    private final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[512]);
    private final LlmRequestHeaders headers = new LlmRequestHeaders();

    @Test
    public void shouldCountHeadersByName()
    {
        headers.wrap(request("content-type", "application/json", "x-id", "1", "x-id", "2"));

        assertThat(headers.count("content-type"), equalTo(1));
        assertThat(headers.count("x-id"), equalTo(2));
        assertThat(headers.count("missing"), equalTo(0));
    }

    @Test
    public void shouldGetValueAsZeroBasedBuffer()
    {
        headers.wrap(request(":path", "/v1/chat/completions", "content-type", "application/json"));

        final DirectBufferEx value = headers.get("content-type", 0);

        assertThat(value.capacity(), equalTo("application/json".length()));
        assertThat(value.getStringWithoutLengthUtf8(0, value.capacity()), equalTo("application/json"));
    }

    @Test
    public void shouldGetRepeatedHeadersInOrder()
    {
        headers.wrap(request("x-id", "first", "other", "x", "x-id", "second"));

        assertThat(text(headers.get("x-id", 0)), equalTo("first"));
        assertThat(text(headers.get("x-id", 1)), equalTo("second"));
        assertThat(headers.get("x-id", 2), nullValue());
    }

    @Test
    public void shouldNotGetMissingHeader()
    {
        headers.wrap(request(":path", "/a"));

        assertThat(headers.get("content-type", 0), nullValue());
    }

    @Test
    public void shouldMatchWholeNamesOnly()
    {
        headers.wrap(request("content-type", "application/json", "content-typed", "x"));

        assertThat(headers.count("content-type"), equalTo(1));
        assertThat(headers.count("content-typ"), equalTo(0));
        assertThat(headers.count("content-typedd"), equalTo(0));
    }

    @Test
    public void shouldRepointToTheNextRequest()
    {
        headers.wrap(request("x-id", "first"));
        assertThat(text(headers.get("x-id", 0)), equalTo("first"));

        headers.wrap(request(":path", "/b"));

        assertThat(headers.get("x-id", 0), nullValue());
        assertThat(text(headers.get(":path", 0)), equalTo("/b"));
    }

    @Test
    public void shouldGetMultiByteValue()
    {
        headers.wrap(request("x-name", "café €"));

        final DirectBufferEx value = headers.get("x-name", 0);

        assertThat(value.capacity(), equalTo("café €".getBytes(UTF_8).length));
        assertThat(value.getStringWithoutLengthUtf8(0, value.capacity()), equalTo("café €"));
    }

    private HttpBeginExFW request(
        String... namesAndValues)
    {
        final HttpBeginExFW.Builder builder = new HttpBeginExFW.Builder().wrap(buffer, 0, buffer.capacity()).typeId(1);

        for (int index = 0; index < namesAndValues.length; index += 2)
        {
            final String name = namesAndValues[index];
            final String value = namesAndValues[index + 1];

            builder.headersItem(h -> h.name(name).value(value));
        }

        return builder.build();
    }

    private static String text(
        DirectBufferEx value)
    {
        return value.getStringWithoutLengthUtf8(0, value.capacity());
    }
}
