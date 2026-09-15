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
package io.aklivity.zilla.runtime.binding.llm.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Base64;

import org.junit.Test;

public class LlmDataUrlStreamHandlerTest
{
    private final LlmDataUrlStreamHandler handler = new LlmDataUrlStreamHandler();

    @Test
    public void shouldDecodeBase64Payload() throws IOException
    {
        String text = "{\"hello\":\"world\"}";
        String encoded = Base64.getEncoder().encodeToString(text.getBytes(UTF_8));
        URL url = url("data:application/json;base64," + encoded);

        String decoded;
        try (InputStream input = url.openStream())
        {
            decoded = new String(input.readAllBytes(), UTF_8);
        }

        assertThat(decoded, equalTo(text));
    }

    @Test(expected = IOException.class)
    public void shouldRejectMissingComma() throws IOException
    {
        URL url = url("data:application/json;base64");

        try (InputStream input = url.openStream())
        {
            input.readAllBytes();
        }
    }

    @Test(expected = IOException.class)
    public void shouldRejectNonBase64Payload() throws IOException
    {
        URL url = url("data:text/plain,hello");

        try (InputStream input = url.openStream())
        {
            input.readAllBytes();
        }
    }

    private URL url(
        String spec) throws MalformedURLException
    {
        return URL.of(URI.create(spec), handler);
    }
}
