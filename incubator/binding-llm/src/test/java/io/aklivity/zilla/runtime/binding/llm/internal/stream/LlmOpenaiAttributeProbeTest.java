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

public class LlmOpenaiAttributeProbeTest
{
    private final LlmOpenaiAttributeProbe probe = new LlmOpenaiAttributeProbe();

    @Test
    public void shouldCarryAttributesForMediaAndToolSectionsOnly()
    {
        assertThat(LlmOpenaiAttributeProbe.carries("user-image"), equalTo(true));
        assertThat(LlmOpenaiAttributeProbe.carries("user-document"), equalTo(true));
        assertThat(LlmOpenaiAttributeProbe.carries("user-audio"), equalTo(true));
        assertThat(LlmOpenaiAttributeProbe.carries("tool-call"), equalTo(true));
        assertThat(LlmOpenaiAttributeProbe.carries("tool-definition"), equalTo(true));
        assertThat(LlmOpenaiAttributeProbe.carries("user-text"), equalTo(false));
        assertThat(LlmOpenaiAttributeProbe.carries("system-instruction"), equalTo(false));
        assertThat(LlmOpenaiAttributeProbe.carries("assistant-text"), equalTo(false));
        assertThat(LlmOpenaiAttributeProbe.carries("tool-result"), equalTo(false));
        assertThat(LlmOpenaiAttributeProbe.carries("unknown"), equalTo(false));
    }

    @Test
    public void shouldProbeImageUrl()
    {
        final String json = "{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://example.com/cat.png\",\"detail\":\"low\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-image", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("url"));
        assertThat(probe.attributes().uri(), equalTo("https://example.com/cat.png"));
        assertThat(probe.attributes().mediaType(), nullValue());
        assertThat(probe.attributes().refId(), nullValue());
    }

    @Test
    public void shouldProbeImageDataUrl()
    {
        final String json = "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,AAAA\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-image", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), equalTo("image/png"));
        assertThat(probe.attributes().uri(), nullValue());
    }

    @Test
    public void shouldProbeFragmentedImageDataUrl()
    {
        final String json = "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64," +
            "A".repeat(4096) + "\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-image", null, json, 96), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), equalTo("image/png"));
    }

    @Test
    public void shouldRejectFragmentedImageUrl()
    {
        final String json = "{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://example.com/" +
            "a".repeat(4096) + "\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-image", null, json, 96), equalTo(REJECTED));
    }

    @Test
    public void shouldCompleteImageWithoutUrl()
    {
        final String json = "{\"type\":\"image_url\",\"image_url\":{\"detail\":\"low\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-image", null, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().source(), nullValue());
        assertThat(probe.attributes().uri(), nullValue());
    }

    @Test
    public void shouldProbeDocumentReference()
    {
        final String json = "{\"type\":\"file\",\"file\":{\"file_id\":\"file-abc123\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-document", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("reference"));
        assertThat(probe.attributes().refId(), equalTo("file-abc123"));
        assertThat(probe.attributes().mediaType(), nullValue());
    }

    @Test
    public void shouldProbeDocumentDataWithFilename()
    {
        final String json = "{\"type\":\"file\",\"file\":{\"filename\":\"contract.pdf\"," +
            "\"file_data\":\"data:application/pdf;base64,AAAA\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-document", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), equalTo("application/pdf"));
        assertThat(probe.attributes().filename(), equalTo("contract.pdf"));
    }

    @Test
    public void shouldProbeFilenameAfterDocumentDataOnlyToEnd()
    {
        final String json = "{\"type\":\"file\",\"file\":{\"file_data\":\"data:application/pdf;base64,AAAA\"," +
            "\"filename\":\"contract.pdf\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-document", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().mediaType(), equalTo("application/pdf"));
        assertThat(probe.attributes().filename(), nullValue());

        assertThat(runToEnd(probe, "user-document", null, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().mediaType(), equalTo("application/pdf"));
        assertThat(probe.attributes().filename(), equalTo("contract.pdf"));
    }

    @Test
    public void shouldProbeDocumentDataWithoutDataUrl()
    {
        final String json = "{\"type\":\"file\",\"file\":{\"file_data\":\"AAAA\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-document", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), nullValue());
    }

    @Test
    public void shouldProbeAudioWithFormatBeforeData()
    {
        final String json = "{\"type\":\"input_audio\",\"input_audio\":{\"format\":\"wav\",\"data\":\"AAAA\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-audio", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), equalTo("audio/wav"));
    }

    @Test
    public void shouldProbeMp3AudioAsMpeg()
    {
        final String json = "{\"type\":\"input_audio\",\"input_audio\":{\"format\":\"mp3\",\"data\":\"AAAA\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-audio", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().mediaType(), equalTo("audio/mpeg"));
    }

    @Test
    public void shouldProbeAudioFormatAfterDataOnlyToEnd()
    {
        final String json = "{\"type\":\"input_audio\",\"input_audio\":{\"data\":\"AAAA\",\"format\":\"wav\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-audio", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo("inline"));
        assertThat(probe.attributes().mediaType(), nullValue());

        assertThat(runToEnd(probe, "user-audio", null, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().mediaType(), equalTo("audio/wav"));
    }

    @Test
    public void shouldProbeToolCallNameAfterArguments()
    {
        final String json = "{\"type\":\"function\",\"id\":\"call_1\",\"function\":{\"arguments\":\"{}\"," +
            "\"name\":\"get_weather\"}}";

        assertThat(runToEnd(probe, "tool-call", null, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().toolCallId(), equalTo("call_1"));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
    }

    @Test
    public void shouldProbeToolDefinitionNameAfterLargeDescription()
    {
        final String json = "{\"type\":\"function\",\"function\":{\"description\":\"" + "x".repeat(4096) +
            "\",\"name\":\"get_weather\"}}";

        assertThat(runToEnd(probe, "tool-definition", null, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
    }

    @Test
    public void shouldProbeToolCall()
    {
        final String json = "{\"type\":\"function\",\"id\":\"call_1\",\"function\":{\"name\":\"get_weather\"," +
            "\"arguments\":\"{}\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "tool-call", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().toolCallId(), equalTo("call_1"));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
    }

    @Test
    public void shouldCompleteToolCallWithoutName()
    {
        final String json = "{\"type\":\"function\",\"id\":\"call_1\",\"function\":{\"arguments\":\"{}\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "tool-call", null, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().toolCallId(), equalTo("call_1"));
        assertThat(probe.attributes().toolName(), nullValue());
    }

    @Test
    public void shouldProbeToolDefinition()
    {
        final String json = "{\"type\":\"function\",\"function\":{\"name\":\"get_weather\",\"description\":\"Weather\"," +
            "\"parameters\":{\"type\":\"object\"}}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "tool-definition", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
        assertThat(probe.attributes().toolCallId(), nullValue());
    }

    @Test
    public void shouldProbeCustomToolDefinition()
    {
        final String json = "{\"type\":\"custom\",\"custom\":{\"name\":\"run_code\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "tool-definition", null, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().toolName(), equalTo("run_code"));
    }

    @Test
    public void shouldNotProbeAttributesForSectionsThatDoNotCarryThem()
    {
        final String json = "{\"type\":\"image_url\",\"id\":\"call_1\",\"image_url\":{\"url\":\"https://example.com/cat.png\"}," +
            "\"function\":{\"name\":\"get_weather\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, "user-text", null, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().source(), nullValue());
        assertThat(probe.attributes().uri(), nullValue());
        assertThat(probe.attributes().toolCallId(), nullValue());
        assertThat(probe.attributes().toolName(), nullValue());
    }

    @Test
    public void shouldClearAttributesBetweenSections()
    {
        final String image = "{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://example.com/cat.png\"}}";
        final String file = "{\"type\":\"file\",\"file\":{\"file_id\":\"file-abc123\"}}";

        LlmAttributeProbeRunner.run(probe, "user-image", null, image, WHOLE);
        LlmAttributeProbeRunner.run(probe, "user-document", null, file, WHOLE);

        assertThat(probe.attributes().source(), equalTo("reference"));
        assertThat(probe.attributes().uri(), nullValue());
    }
}
