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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAttributeProbe.Status.COMPLETE;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAttributeProbe.Status.REJECTED;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAttributeProbe.Status.RESOLVED;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAttributeProbeRunner.WHOLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class LlmAnthropicAttributeProbeTest
{
    private final LlmAnthropicAttributeProbe probe = new LlmAnthropicAttributeProbe();

    @Test
    public void shouldCarryAttributesForMediaAndToolSectionsOnly()
    {
        assertThat(LlmAnthropicAttributeProbe.carries("user-image"), equalTo(true));
        assertThat(LlmAnthropicAttributeProbe.carries("user-document"), equalTo(true));
        assertThat(LlmAnthropicAttributeProbe.carries("tool-call"), equalTo(true));
        assertThat(LlmAnthropicAttributeProbe.carries("tool-definition"), equalTo(true));
        assertThat(LlmAnthropicAttributeProbe.carries("server-tool-result"), equalTo(true));
        assertThat(LlmAnthropicAttributeProbe.carries("user-text"), equalTo(false));
        assertThat(LlmAnthropicAttributeProbe.carries("system-instruction"), equalTo(false));
        assertThat(LlmAnthropicAttributeProbe.carries("assistant-text"), equalTo(false));
        assertThat(LlmAnthropicAttributeProbe.carries("assistant-reasoning"), equalTo(false));
        assertThat(LlmAnthropicAttributeProbe.carries("citations"), equalTo(false));
        assertThat(LlmAnthropicAttributeProbe.carries("tool-result"), equalTo(false));
        assertThat(LlmAnthropicAttributeProbe.carries("unknown"), equalTo(false));
    }

    @Test
    public void shouldProbeImageUrl()
    {
        final String json = "{\"type\":\"image\",\"source\":{\"type\":\"url\",\"url\":\"https://example.com/cat.png\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-image", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("url"));
        assertThat(probe.attributes().uri(), equalTo("https://example.com/cat.png"));
        assertThat(probe.attributes().mediaType(), nullValue());
    }

    @Test
    public void shouldProbeImageBase64()
    {
        final String json = "{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"image/png\"," +
            "\"data\":\"AAAA\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-image", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), equalTo("image/png"));
        assertThat(probe.attributes().uri(), nullValue());
    }

    @Test
    public void shouldProbeFragmentedImageBase64()
    {
        final String json = "{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"image/jpeg\"," +
            "\"data\":\"" + "A".repeat(4096) + "\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-image", null, json, 96), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), equalTo("image/jpeg"));
    }

    @Test
    public void shouldProbeImageFileReference()
    {
        final String json = "{\"type\":\"image\",\"source\":{\"type\":\"file\",\"file_id\":\"file_011abc\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-image", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("reference"));
        assertThat(probe.attributes().refId(), equalTo("file_011abc"));
    }

    @Test
    public void shouldProbeDocumentUrl()
    {
        final String json = "{\"type\":\"document\",\"title\":\"Contract\",\"source\":{\"type\":\"url\"," +
            "\"url\":\"https://example.com/contract.pdf\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-document", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("url"));
        assertThat(probe.attributes().uri(), equalTo("https://example.com/contract.pdf"));
        assertThat(probe.attributes().filename(), nullValue());
    }

    @Test
    public void shouldProbeDocumentPlainText()
    {
        final String json = "{\"type\":\"document\",\"source\":{\"type\":\"text\",\"media_type\":\"text/plain\"," +
            "\"data\":\"hello\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-document", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), equalTo("text/plain"));
    }

    @Test
    public void shouldProbeDocumentContentBlocks()
    {
        final String json = "{\"type\":\"document\",\"source\":{\"type\":\"content\",\"content\":[" +
            "{\"type\":\"text\",\"text\":\"hello\"}]}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-document", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), nullValue());
    }

    @Test
    public void shouldRejectFragmentedUrl()
    {
        final String json = "{\"type\":\"image\",\"source\":{\"type\":\"url\",\"url\":\"https://example.com/" +
            "a".repeat(4096) + "\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-image", null, json, 96), equalTo(REJECTED));
    }

    @Test
    public void shouldCompleteImageWithoutSourceContent()
    {
        final String json = "{\"type\":\"image\",\"source\":{\"type\":\"base64\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-image", null, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().source(), nullValue());
    }

    @Test
    public void shouldProbeToolUse()
    {
        final String json = "{\"type\":\"tool_use\",\"id\":\"toolu_01\",\"name\":\"get_weather\"," +
            "\"input\":{\"location\":\"Paris\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "tool-call", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().toolCallId(), equalTo("toolu_01"));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
        assertThat(probe.attributes().providerExecuted(), nullValue());
    }

    @Test
    public void shouldProbeServerToolUse()
    {
        final String json = "{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_01\",\"name\":\"web_search\"," +
            "\"input\":{\"query\":\"zilla\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "server-tool-result", "server_tool_use", json, WHOLE),
            equalTo(RESOLVED));
        assertThat(probe.attributes().toolCallId(), equalTo("srvtoolu_01"));
        assertThat(probe.attributes().toolName(), equalTo("web_search"));
        assertThat(probe.attributes().providerExecuted(), equalTo("true"));
    }

    @Test
    public void shouldProbeServerToolResult()
    {
        final String json = "{\"type\":\"web_search_tool_result\",\"tool_use_id\":\"srvtoolu_01\",\"content\":[]}";

        assertThat(LlmAttributeProbeRunner.run(probe, "server-tool-result", "web_search_tool_result", json, WHOLE),
            equalTo(RESOLVED));
        assertThat(probe.attributes().toolCallId(), equalTo("srvtoolu_01"));
        assertThat(probe.attributes().toolName(), nullValue());
        assertThat(probe.attributes().providerExecuted(), equalTo("true"));
    }

    @Test
    public void shouldProbeToolDefinition()
    {
        final String json = "{\"type\":\"custom\",\"name\":\"get_weather\",\"description\":\"Weather\"," +
            "\"input_schema\":{\"type\":\"object\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "tool-definition", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
        assertThat(probe.attributes().toolCallId(), nullValue());
    }

    @Test
    public void shouldNotProbeAttributesForSectionsThatDoNotCarryThem()
    {
        final String json = "{\"type\":\"thinking\",\"id\":\"toolu_01\",\"name\":\"get_weather\"," +
            "\"source\":{\"type\":\"url\",\"url\":\"https://example.com/cat.png\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "assistant-reasoning", null, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().source(), nullValue());
        assertThat(probe.attributes().toolCallId(), nullValue());
        assertThat(probe.attributes().toolName(), nullValue());
        assertThat(probe.attributes().providerExecuted(), nullValue());
    }

    @Test
    public void shouldClearAttributesBetweenSections()
    {
        final String serverToolUse = "{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_01\",\"name\":\"web_search\"}";
        final String toolUse = "{\"type\":\"tool_use\",\"id\":\"toolu_01\",\"name\":\"get_weather\"}";

        LlmAttributeProbeRunner.run(probe, "server-tool-result", "server_tool_use", serverToolUse, WHOLE);
        LlmAttributeProbeRunner.run(probe, "tool-call", null, toolUse, WHOLE);

        assertThat(probe.attributes().toolCallId(), equalTo("toolu_01"));
        assertThat(probe.attributes().providerExecuted(), nullValue());
    }
}
