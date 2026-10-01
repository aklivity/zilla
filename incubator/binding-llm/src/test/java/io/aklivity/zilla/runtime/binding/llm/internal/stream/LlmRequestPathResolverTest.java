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
import static org.hamcrest.Matchers.sameInstance;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmRequestPathResolverTest
{
    @Test
    public void shouldPassThroughTemplateWithoutPlaceholder()
    {
        String resolved = LlmRequestPathResolver.resolve("/v1/chat/completions", model("gpt-4"));

        assertThat(resolved, equalTo("/v1/chat/completions"));
    }

    @Test
    public void shouldPassThroughTemplateWithoutPlaceholderWhenModelAbsent()
    {
        String resolved = LlmRequestPathResolver.resolve("/v1/chat/completions", null);

        assertThat(resolved, equalTo("/v1/chat/completions"));
    }

    @Test
    public void shouldSubstitutePlaceholderWithModel()
    {
        String resolved = LlmRequestPathResolver.resolve("/v1/models/{model}/chat", model("gpt-4"));

        assertThat(resolved, equalTo("/v1/models/gpt-4/chat"));
    }

    @Test
    public void shouldSubstitutePlaceholderAtEndOfTemplate()
    {
        String resolved = LlmRequestPathResolver.resolve("/v1/{model}", model("gpt-4"));

        assertThat(resolved, equalTo("/v1/gpt-4"));
    }

    @Test
    public void shouldPercentEncodeModelAsPathSegment()
    {
        String resolved = LlmRequestPathResolver.resolve("/v1/models/{model}", model("gpt-4 turbo/preview"));

        assertThat(resolved, equalTo("/v1/models/gpt-4%20turbo%2Fpreview"));
    }

    @Test
    public void shouldReturnNullWhenPlaceholderPresentButModelAbsent()
    {
        String resolved = LlmRequestPathResolver.resolve("/v1/models/{model}", null);

        assertThat(resolved, nullValue());
    }

    @Test
    public void shouldReturnSameTemplateInstanceWhenNoPlaceholder()
    {
        String template = "/v1/chat/completions";

        String resolved = LlmRequestPathResolver.resolve(template, null);

        assertThat(resolved, sameInstance(template));
    }

    private static UnsafeBufferEx model(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }
}
