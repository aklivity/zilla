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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmHeaderBytesTest
{
    private static final Pattern CONTENT_TYPE = Pattern.compile("\\s*([^;\\s]+)\\s*(?:;.*)?");

    private static final String[] CONTENT_TYPES =
    {
        "application/json",
        "application/json; charset=utf-8",
        "application/json;charset=utf-8",
        "  application/json  ",
        "\tapplication/json\t;q=1",
        "application/json ;",
        "application/json;",
        "application/jsonx",
        "application/jso",
        "Application/JSON",
        "application/json x",
        "application/json\n",
        "application/json; a\nb",
        "application/json; a\rb",
        "text/plain",
        ";application/json",
        " ",
        ""
    };

    @Test
    public void shouldMatchExactValue()
    {
        assertThat(LlmHeaderBytes.equalTo(buffer("POST"), "POST"), equalTo(true));
        assertThat(LlmHeaderBytes.equalTo(buffer("POS"), "POST"), equalTo(false));
        assertThat(LlmHeaderBytes.equalTo(buffer("POSTS"), "POST"), equalTo(false));
        assertThat(LlmHeaderBytes.equalTo(buffer("post"), "POST"), equalTo(false));
        assertThat(LlmHeaderBytes.equalTo(null, "POST"), equalTo(false));
    }

    @Test
    public void shouldMatchValueIgnoringCase()
    {
        assertThat(LlmHeaderBytes.equalToIgnoreCase(buffer("post"), "POST"), equalTo(true));
        assertThat(LlmHeaderBytes.equalToIgnoreCase(buffer("PoSt"), "POST"), equalTo(true));
        assertThat(LlmHeaderBytes.equalToIgnoreCase(buffer("posts"), "POST"), equalTo(false));
        assertThat(LlmHeaderBytes.equalToIgnoreCase(buffer("pos"), "POST"), equalTo(false));
        assertThat(LlmHeaderBytes.equalToIgnoreCase(null, "POST"), equalTo(false));
    }

    @Test
    public void shouldMatchMediaTypeLikeTheContentTypePattern()
    {
        for (String contentType : CONTENT_TYPES)
        {
            assertThat(contentType, LlmHeaderBytes.isMediaType(buffer(contentType), "application/json"),
                equalTo(expected(contentType, "application/json")));
        }
    }

    @Test
    public void shouldNotMatchAbsentMediaType()
    {
        assertThat(LlmHeaderBytes.isMediaType(null, "application/json"), equalTo(false));
    }

    private static boolean expected(
        String contentType,
        String mediaType)
    {
        final Matcher matcher = CONTENT_TYPE.matcher(contentType);

        return matcher.matches() && mediaType.equals(matcher.group(1));
    }

    private static DirectBufferEx buffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }
}
