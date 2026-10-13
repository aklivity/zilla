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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class LlmSectionTranslatorTest
{
    @Test
    public void shouldTranslateOpenaiImageUrlToAnthropicUrlImage()
    {
        final String openai = "{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://example.com/cat.png\"}}";

        assertThat(LlmSectionTranslator.toAnthropic("user-image", openai),
            equalTo("{\"type\":\"image\",\"source\":{\"type\":\"url\",\"url\":\"https://example.com/cat.png\"}}"));
    }

    @Test
    public void shouldTranslateOpenaiDataUrlToAnthropicBase64Image()
    {
        final String openai = "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,AAAA\"}}";

        assertThat(LlmSectionTranslator.toAnthropic("user-image", openai),
            equalTo("{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"image/png\"," +
                "\"data\":\"AAAA\"}}"));
    }

    @Test
    public void shouldTranslateAnthropicUrlImageToOpenaiImageUrl()
    {
        final String anthropic = "{\"type\":\"image\",\"source\":{\"type\":\"url\",\"url\":\"https://example.com/cat.png\"}}";

        assertThat(LlmSectionTranslator.toOpenai("user-image", anthropic),
            equalTo("{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://example.com/cat.png\"}}"));
    }

    @Test
    public void shouldTranslateAnthropicBase64ImageToOpenaiDataUrl()
    {
        final String anthropic = "{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"image/png\"," +
            "\"data\":\"AAAA\"}}";

        assertThat(LlmSectionTranslator.toOpenai("user-image", anthropic),
            equalTo("{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,AAAA\"}}"));
    }

    @Test
    public void shouldTranslateOpenaiFileToAnthropicDocument()
    {
        final String openai = "{\"type\":\"file\",\"file\":{\"file_data\":\"data:application/pdf;base64,AAAA\"}}";

        assertThat(LlmSectionTranslator.toAnthropic("user-document", openai),
            equalTo("{\"type\":\"document\",\"source\":{\"type\":\"base64\",\"media_type\":\"application/pdf\"," +
                "\"data\":\"AAAA\"}}"));
    }

    @Test
    public void shouldTranslateAnthropicDocumentToOpenaiFile()
    {
        final String anthropic = "{\"type\":\"document\",\"source\":{\"type\":\"base64\"," +
            "\"media_type\":\"application/pdf\",\"data\":\"AAAA\"}}";

        assertThat(LlmSectionTranslator.toOpenai("user-document", anthropic),
            equalTo("{\"type\":\"file\",\"file\":{\"file_data\":\"data:application/pdf;base64,AAAA\"}}"));
    }

    @Test
    public void shouldNotTranslateAnthropicUrlDocumentToOpenai()
    {
        final String anthropic = "{\"type\":\"document\",\"source\":{\"type\":\"url\",\"url\":\"https://example.com/a.pdf\"}}";

        assertThat(LlmSectionTranslator.toOpenai("user-document", anthropic), nullValue());
    }

    @Test
    public void shouldTranslateOpenaiToolCallToAnthropicToolUse()
    {
        final String openai = "{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\"," +
            "\"arguments\":\"{\\\"city\\\":\\\"Paris\\\"}\"}}";

        assertThat(LlmSectionTranslator.toAnthropic("tool-call", openai),
            equalTo("{\"type\":\"tool_use\",\"id\":\"call_1\",\"name\":\"get_weather\",\"input\":{\"city\":\"Paris\"}}"));
    }

    @Test
    public void shouldTranslateOpenaiToolCallWithEmptyArgumentsToEmptyInput()
    {
        final String openai = "{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"ping\",\"arguments\":\"\"}}";

        assertThat(LlmSectionTranslator.toAnthropic("tool-call", openai),
            equalTo("{\"type\":\"tool_use\",\"id\":\"call_1\",\"name\":\"ping\",\"input\":{}}"));
    }

    @Test
    public void shouldNotTranslateOpenaiToolCallWithMalformedArguments()
    {
        final String openai = "{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"ping\"," +
            "\"arguments\":\"{oops\"}}";

        assertThat(LlmSectionTranslator.toAnthropic("tool-call", openai), nullValue());
    }

    @Test
    public void shouldTranslateAnthropicToolUseToOpenaiToolCall()
    {
        final String anthropic = "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"get_weather\"," +
            "\"input\":{\"city\":\"Paris\"}}";

        assertThat(LlmSectionTranslator.toOpenai("tool-call", anthropic),
            equalTo("{\"id\":\"toolu_1\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\"," +
                "\"arguments\":\"{\\\"city\\\":\\\"Paris\\\"}\"}}"));
    }

    @Test
    public void shouldTranslateOpenaiFunctionToAnthropicTool()
    {
        final String openai = "{\"type\":\"function\",\"function\":{\"name\":\"get_weather\"," +
            "\"description\":\"Get the weather\",\"parameters\":{\"type\":\"object\"}}}";

        assertThat(LlmSectionTranslator.toAnthropic("tool-definition", openai),
            equalTo("{\"name\":\"get_weather\",\"description\":\"Get the weather\"," +
                "\"input_schema\":{\"type\":\"object\"}}"));
    }

    @Test
    public void shouldTranslateAnthropicToolToOpenaiFunction()
    {
        final String anthropic = "{\"name\":\"get_weather\",\"description\":\"Get the weather\"," +
            "\"input_schema\":{\"type\":\"object\"}}";

        assertThat(LlmSectionTranslator.toOpenai("tool-definition", anthropic),
            equalTo("{\"type\":\"function\",\"function\":{\"name\":\"get_weather\"," +
                "\"description\":\"Get the weather\",\"parameters\":{\"type\":\"object\"}}}"));
    }

    @Test
    public void shouldNotTranslateAnthropicServerToolDefinitionToOpenai()
    {
        final String anthropic = "{\"type\":\"web_search_20250305\",\"name\":\"web_search\"}";

        assertThat(LlmSectionTranslator.toOpenai("tool-definition", anthropic), nullValue());
    }

    @Test
    public void shouldNotTranslateMalformedPayload()
    {
        assertThat(LlmSectionTranslator.toAnthropic("user-image", "{oops"), nullValue());
        assertThat(LlmSectionTranslator.toOpenai("tool-call", "{oops"), nullValue());
    }

    @Test
    public void shouldNotTranslateUnsupportedSectionType()
    {
        assertThat(LlmSectionTranslator.toAnthropic("user-audio", "{}"), nullValue());
        assertThat(LlmSectionTranslator.toOpenai("assistant-reasoning", "{}"), nullValue());
    }
}
