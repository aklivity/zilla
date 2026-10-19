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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAttributeProbeRunner.runToEnd;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;

public class LlmAnthropicAttributeProbeTest
{
    private final LlmAnthropicAttributeProbe probe = new LlmAnthropicAttributeProbe();

    @Test
    public void shouldCarryAttributesForMediaAndToolSectionsOnly()
    {
        assertThat(LlmAnthropicAttributeProbe.carries(LlmSectionType.USER_IMAGE), equalTo(true));
        assertThat(LlmAnthropicAttributeProbe.carries(LlmSectionType.USER_DOCUMENT), equalTo(true));
        assertThat(LlmAnthropicAttributeProbe.carries(LlmSectionType.TOOL_CALL), equalTo(true));
        assertThat(LlmAnthropicAttributeProbe.carries(LlmSectionType.TOOL_DEFINITION), equalTo(true));
        assertThat(LlmAnthropicAttributeProbe.carries(LlmSectionType.SERVER_TOOL_RESULT), equalTo(true));
        assertThat(LlmAnthropicAttributeProbe.carries(LlmSectionType.USER_TEXT), equalTo(false));
        assertThat(LlmAnthropicAttributeProbe.carries(LlmSectionType.SYSTEM_INSTRUCTION), equalTo(false));
        assertThat(LlmAnthropicAttributeProbe.carries(LlmSectionType.ASSISTANT_TEXT), equalTo(false));
        assertThat(LlmAnthropicAttributeProbe.carries(LlmSectionType.ASSISTANT_REASONING), equalTo(false));
        assertThat(LlmAnthropicAttributeProbe.carries(LlmSectionType.CITATIONS), equalTo(false));
        assertThat(LlmAnthropicAttributeProbe.carries(LlmSectionType.TOOL_RESULT), equalTo(false));
        assertThat(LlmAnthropicAttributeProbe.carries(LlmSectionType.CONTROL), equalTo(false));
    }

    @Test
    public void shouldProbeImageUrl()
    {
        final String json = "{\"type\":\"image\",\"source\":{\"type\":\"url\",\"url\":\"https://example.com/cat.png\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.USER_IMAGE, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("url"));
        assertThat(probe.attributes().mediaType(), nullValue());
    }

    @Test
    public void shouldProbeImageBase64()
    {
        final String json = "{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"image/png\"," +
            "\"data\":\"AAAA\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.USER_IMAGE, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), equalTo("image/png"));
    }

    @Test
    public void shouldProbeFragmentedImageBase64()
    {
        final String json = "{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"image/jpeg\"," +
            "\"data\":\"" + "A".repeat(4096) + "\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.USER_IMAGE, json, 96), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), equalTo("image/jpeg"));
    }

    @Test
    public void shouldProbeImageFileReference()
    {
        final String json = "{\"type\":\"image\",\"source\":{\"type\":\"file\",\"file_id\":\"file_011abc\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.USER_IMAGE, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("reference"));
        assertThat(probe.attributes().refId(), equalTo("file_011abc"));
    }

    @Test
    public void shouldProbeDocumentUrl()
    {
        final String json = "{\"type\":\"document\",\"title\":\"Contract\",\"source\":{\"type\":\"url\"," +
            "\"url\":\"https://example.com/contract.pdf\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.USER_DOCUMENT, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("url"));
        assertThat(probe.attributes().filename(), nullValue());
    }

    @Test
    public void shouldProbeDocumentPlainText()
    {
        final String json = "{\"type\":\"document\",\"source\":{\"type\":\"text\",\"media_type\":\"text/plain\"," +
            "\"data\":\"hello\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.USER_DOCUMENT, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), equalTo("text/plain"));
    }

    @Test
    public void shouldProbeDocumentContentBlocks()
    {
        final String json = "{\"type\":\"document\",\"source\":{\"type\":\"content\",\"content\":[" +
            "{\"type\":\"text\",\"text\":\"hello\"}]}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.USER_DOCUMENT, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), nullValue());
    }

    @Test
    public void shouldProbeFragmentedUrl()
    {
        final String json = "{\"type\":\"image\",\"source\":{\"type\":\"url\",\"url\":\"https://example.com/" +
            "a".repeat(4096) + "\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.USER_IMAGE, json, 96), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("url"));
    }

    @Test
    public void shouldRejectFragmentedFileReference()
    {
        final String json = "{\"type\":\"image\",\"source\":{\"type\":\"file\",\"file_id\":\"" +
            "a".repeat(4096) + "\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.USER_IMAGE, json, 96), equalTo(REJECTED));
    }

    @Test
    public void shouldCompleteImageWithoutSourceContent()
    {
        final String json = "{\"type\":\"image\",\"source\":{\"type\":\"base64\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.USER_IMAGE, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().source(), nullValue());
    }

    @Test
    public void shouldProbeToolUse()
    {
        final String json = "{\"type\":\"tool_use\",\"id\":\"toolu_01\",\"name\":\"get_weather\"," +
            "\"input\":{\"location\":\"Paris\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.TOOL_CALL, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().toolCallId(), equalTo("toolu_01"));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
    }

    @Test
    public void shouldProbeToolUseNameAfterInput()
    {
        final String json = "{\"type\":\"tool_use\",\"id\":\"toolu_01\",\"input\":{\"name\":\"nested\"}," +
            "\"name\":\"get_weather\"}";

        assertThat(runToEnd(probe, LlmSectionType.TOOL_CALL, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().toolCallId(), equalTo("toolu_01"));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
    }

    @Test
    public void shouldProbeToolDefinitionNameAfterLargeDescription()
    {
        final String json = "{\"type\":\"custom\",\"description\":\"" + "x".repeat(4096) +
            "\",\"name\":\"get_weather\",\"input_schema\":{\"type\":\"object\"}}";

        assertThat(runToEnd(probe, LlmSectionType.TOOL_DEFINITION, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
    }

    @Test
    public void shouldProbeServerToolUse()
    {
        final String json = "{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_01\",\"name\":\"web_search\"," +
            "\"input\":{\"query\":\"zilla\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.SERVER_TOOL_CALL, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().toolCallId(), equalTo("srvtoolu_01"));
        assertThat(probe.attributes().toolName(), equalTo("web_search"));
    }

    @Test
    public void shouldProbeServerToolResult()
    {
        final String json = "{\"type\":\"web_search_tool_result\",\"tool_use_id\":\"srvtoolu_01\",\"content\":[]}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.SERVER_TOOL_RESULT, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().toolCallId(), equalTo("srvtoolu_01"));
        assertThat(probe.attributes().toolName(), nullValue());
    }

    @Test
    public void shouldProbeToolDefinition()
    {
        final String json = "{\"type\":\"custom\",\"name\":\"get_weather\",\"description\":\"Weather\"," +
            "\"input_schema\":{\"type\":\"object\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.TOOL_DEFINITION, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
        assertThat(probe.attributes().toolCallId(), nullValue());
        assertThat(probe.type(), equalTo(LlmSectionType.TOOL_DEFINITION));
    }

    @Test
    public void shouldProbeServerToolDefinition()
    {
        final String json = "{\"type\":\"web_search_20250305\",\"name\":\"web_search\",\"max_uses\":5}";

        assertThat(LlmAttributeProbeRunner.runObject(probe, LlmSectionType.TOOL_DEFINITION, json, WHOLE, false),
            equalTo(RESOLVED));
        assertThat(probe.attributes().toolName(), equalTo("web_search"));
        assertThat(probe.type(), equalTo(LlmSectionType.SERVER_TOOL_DEFINITION));
    }

    @Test
    public void shouldProbeToolDefinitionTypeAfterName()
    {
        final String json = "{\"name\":\"web_search\",\"type\":\"web_search_20250305\"}";

        assertThat(LlmAttributeProbeRunner.runObject(probe, LlmSectionType.TOOL_DEFINITION, json, WHOLE, true),
            equalTo(COMPLETE));
        assertThat(probe.attributes().toolName(), equalTo("web_search"));
        assertThat(probe.type(), equalTo(LlmSectionType.TOOL_DEFINITION));
    }

    @Test
    public void shouldNotProbeAttributesForSectionsThatDoNotCarryThem()
    {
        final String json = "{\"type\":\"thinking\",\"id\":\"toolu_01\",\"name\":\"get_weather\"," +
            "\"source\":{\"type\":\"url\",\"url\":\"https://example.com/cat.png\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.ASSISTANT_REASONING, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().source(), nullValue());
        assertThat(probe.attributes().toolCallId(), nullValue());
        assertThat(probe.attributes().toolName(), nullValue());
    }

    @Test
    public void shouldClearAttributesBetweenSections()
    {
        final String serverToolUse = "{\"type\":\"server_tool_use\",\"id\":\"srvtoolu_01\",\"name\":\"web_search\"}";
        final String toolUse = "{\"type\":\"tool_use\",\"id\":\"toolu_01\",\"name\":\"get_weather\"}";

        LlmAttributeProbeRunner.run(probe, LlmSectionType.SERVER_TOOL_CALL, serverToolUse, WHOLE);
        LlmAttributeProbeRunner.run(probe, LlmSectionType.TOOL_CALL, toolUse, WHOLE);

        assertThat(probe.attributes().toolCallId(), equalTo("toolu_01"));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
    }
}
