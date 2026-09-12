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

import io.aklivity.zilla.runtime.binding.llm.dialect.HttpRequestBody;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmJsonRequestBodyTest
{
    @Test
    public void shouldResolveTrueForStreamingRequest()
    {
        HttpRequestBody body = bodyOf("{\"model\":\"gpt-4\",\"stream\":true,\"messages\":[]}");

        assertThat(body.value("stream"), equalTo("true"));
    }

    @Test
    public void shouldResolveFalseForNonStreamingRequest()
    {
        HttpRequestBody body = bodyOf("{\"model\":\"gpt-4\",\"stream\":false}");

        assertThat(body.value("stream"), equalTo("false"));
    }

    @Test
    public void shouldReturnNullWhenMemberAbsent()
    {
        HttpRequestBody body = bodyOf("{\"model\":\"gpt-4\"}");

        assertThat(body.value("stream"), nullValue());
    }

    @Test
    public void shouldResolveStringMember()
    {
        HttpRequestBody body = bodyOf("{\"model\":\"gpt-4\",\"stream\":true}");

        assertThat(body.value("model"), equalTo("gpt-4"));
    }

    @Test
    public void shouldResolveNumberMember()
    {
        HttpRequestBody body = bodyOf("{\"max_tokens\":42,\"stream\":true}");

        assertThat(body.value("max_tokens"), equalTo("42"));
    }

    @Test
    public void shouldIgnoreNestedMemberOfSameName()
    {
        HttpRequestBody body = bodyOf("{\"options\":{\"stream\":true},\"stream\":false}");

        assertThat(body.value("stream"), equalTo("false"));
    }

    @Test
    public void shouldReturnNullForNonScalarMember()
    {
        HttpRequestBody body = bodyOf("{\"messages\":[{\"role\":\"user\"}]}");

        assertThat(body.value("messages"), nullValue());
    }

    @Test
    public void shouldReturnNullForMalformedJson()
    {
        HttpRequestBody body = bodyOf("{\"stream\":");

        assertThat(body.value("stream"), nullValue());
    }

    @Test
    public void shouldReturnNullMemberValueAsAbsent()
    {
        HttpRequestBody body = bodyOf("{\"stream\":null}");

        assertThat(body.value("stream"), nullValue());
    }

    private static HttpRequestBody bodyOf(
        String json)
    {
        byte[] bytes = json.getBytes(UTF_8);
        UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        return new LlmJsonRequestBody(buffer, 0, bytes.length);
    }
}
