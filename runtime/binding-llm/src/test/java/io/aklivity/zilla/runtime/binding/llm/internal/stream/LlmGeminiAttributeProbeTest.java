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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAttributeProbe.Status.PENDING;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAttributeProbe.Status.REJECTED;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAttributeProbe.Status.RESOLVED;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAttributeProbeRunner.WHOLE;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAttributeProbeRunner.runObject;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAttributeProbeRunner.runToEnd;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;

public class LlmGeminiAttributeProbeTest
{
    private final LlmGeminiAttributeProbe probe = new LlmGeminiAttributeProbe();

    @Test
    public void shouldCarryAttributesForToolSectionsOnly()
    {
        assertThat(LlmGeminiAttributeProbe.carries(LlmSectionType.TOOL_CALL), equalTo(true));
        assertThat(LlmGeminiAttributeProbe.carries(LlmSectionType.TOOL_RESULT), equalTo(true));
        assertThat(LlmGeminiAttributeProbe.carries(LlmSectionType.TOOL_DEFINITION), equalTo(true));
        assertThat(LlmGeminiAttributeProbe.carries(LlmSectionType.USER_IMAGE), equalTo(true));
        assertThat(LlmGeminiAttributeProbe.carries(LlmSectionType.USER_DOCUMENT), equalTo(true));
        assertThat(LlmGeminiAttributeProbe.carries(LlmSectionType.USER_AUDIO), equalTo(true));
        assertThat(LlmGeminiAttributeProbe.carries(LlmSectionType.USER_VIDEO), equalTo(true));
        assertThat(LlmGeminiAttributeProbe.carries(LlmSectionType.USER_TEXT), equalTo(false));
        assertThat(LlmGeminiAttributeProbe.carries(LlmSectionType.ASSISTANT_TEXT), equalTo(true));
        assertThat(LlmGeminiAttributeProbe.carries(LlmSectionType.ASSISTANT_REASONING), equalTo(false));
        assertThat(LlmGeminiAttributeProbe.carries(LlmSectionType.CONTROL), equalTo(false));
    }

    @Test
    public void shouldProbeFunctionCallNameAndId()
    {
        final String json = "{\"functionCall\":{\"name\":\"get_weather\",\"args\":{\"name\":\"nested\"},\"id\":\"call_1\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.TOOL_CALL, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
        assertThat(probe.attributes().toolCallId(), equalTo("call_1"));
    }

    @Test
    public void shouldProbeFunctionCallWithoutId()
    {
        final String json = "{\"functionCall\":{\"name\":\"ping\"}}";

        assertThat(runToEnd(probe, LlmSectionType.TOOL_CALL, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().toolName(), equalTo("ping"));
        assertThat(probe.attributes().toolCallId(), nullValue());
    }

    @Test
    public void shouldRejectFragmentedFunctionCallName()
    {
        final String json = "{\"functionCall\":{\"name\":\"" + "x".repeat(300) + "\",\"id\":\"call_1\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.TOOL_CALL, json, 96), equalTo(REJECTED));
    }

    @Test
    public void shouldProbeFunctionResponseShapeOutput()
    {
        final String json =
            "{\"functionResponse\":{\"name\":\"get_weather\",\"id\":\"call_1\",\"response\":{\"output\":\"sunny\"}}}";

        assertThat(runToEnd(probe, LlmSectionType.TOOL_RESULT, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().toolCallId(), equalTo("call_1"));
        assertThat(probe.settle(), equalTo(LlmGeminiAttributeProbe.SHAPE_OUTPUT));
        assertThat(probe.attributes().isError(), nullValue());
    }

    @Test
    public void shouldProbeFunctionResponseShapeError()
    {
        final String json = "{\"functionResponse\":{\"name\":\"get_weather\",\"response\":{\"error\":\"boom\"}}}";

        assertThat(runToEnd(probe, LlmSectionType.TOOL_RESULT, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.settle(), equalTo(LlmGeminiAttributeProbe.SHAPE_ERROR));
        assertThat(probe.attributes().isError(), equalTo("true"));
    }

    @Test
    public void shouldProbeFunctionResponseShapeOther()
    {
        final String json = "{\"functionResponse\":{\"name\":\"get_weather\",\"response\":{\"temp\":20}}}";

        assertThat(runToEnd(probe, LlmSectionType.TOOL_RESULT, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.settle(), equalTo(LlmGeminiAttributeProbe.SHAPE_OTHER));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
    }

    @Test
    public void shouldProbeFunctionResponseWithManyMembersAsOther()
    {
        final String json = "{\"functionResponse\":{\"name\":\"get_weather\",\"response\":{\"output\":\"sunny\",\"extra\":1}}}";

        assertThat(runToEnd(probe, LlmSectionType.TOOL_RESULT, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.settle(), equalTo(LlmGeminiAttributeProbe.SHAPE_OTHER));
    }

    @Test
    public void shouldProbeDeclarationName()
    {
        final String json = "{\"description\":\"desc\",\"name\":\"get_weather\",\"parametersJsonSchema\":{\"name\":\"n\"}}";

        assertThat(runObject(probe, LlmSectionType.TOOL_DEFINITION, json, WHOLE, false), equalTo(RESOLVED));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
    }

    @Test
    public void shouldProbeInlineDataTypeFromMediaType()
    {
        final String json = "{\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"QUJD\"}}";

        assertThat(runToEnd(probe, LlmSectionType.USER_DOCUMENT, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.type(), equalTo(LlmSectionType.USER_IMAGE));
        assertThat(probe.attributes().mediaType(), equalTo("image/png"));
        assertThat(probe.attributes().source(), equalTo(LlmSectionAttributes.SOURCE_INLINE));
        assertThat(probe.unclassified(), equalTo(false));
    }

    @Test
    public void shouldProbeInlineDataWithDataFirst()
    {
        final String json = "{\"inline_data\":{\"data\":\"QUJD\",\"mime_type\":\"application/pdf\"}}";

        assertThat(runToEnd(probe, LlmSectionType.USER_DOCUMENT, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.type(), equalTo(LlmSectionType.USER_DOCUMENT));
        assertThat(probe.attributes().mediaType(), equalTo("application/pdf"));
        assertThat(probe.attributes().source(), equalTo(LlmSectionAttributes.SOURCE_INLINE));
    }

    @Test
    public void shouldProbeMediaTypeFamilies()
    {
        for (String[] family : new String[][]
        {
            {"audio/wav", "USER_AUDIO"},
            {"video/mp4", "USER_VIDEO"},
            {"image/jpeg", "USER_IMAGE"},
            {"text/plain", "USER_DOCUMENT"}
        })
        {
            final String json = "{\"inlineData\":{\"mimeType\":\"" + family[0] + "\",\"data\":\"QUJD\"}}";

            assertThat(runToEnd(probe, LlmSectionType.USER_DOCUMENT, json, WHOLE), equalTo(COMPLETE));
            assertThat(probe.type(), equalTo(LlmSectionType.valueOf(family[1])));
        }
    }

    @Test
    public void shouldProbeFileDataUrl()
    {
        final String json = "{\"fileData\":{\"mimeType\":\"image/png\",\"fileUri\":\"https://example.com/a.png\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.USER_DOCUMENT, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo(LlmSectionAttributes.SOURCE_URL));
        assertThat(probe.attributes().mediaType(), equalTo("image/png"));
    }

    @Test
    public void shouldProbeFileDataFilesHandleAsReference()
    {
        final String uri = "https://generativelanguage.googleapis.com/v1beta/files/abc123";
        final String json = "{\"fileData\":{\"fileUri\":\"" + uri + "\",\"mimeType\":\"application/pdf\"}}";

        assertThat(runToEnd(probe, LlmSectionType.USER_DOCUMENT, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().source(), equalTo(LlmSectionAttributes.SOURCE_REFERENCE));
        assertThat(probe.attributes().refId(), equalTo(uri));
    }

    @Test
    public void shouldProbeFileDataStorageUriAsReference()
    {
        final String json = "{\"fileData\":{\"mimeType\":\"image/png\",\"fileUri\":\"gs://bucket/a.png\"}}";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.USER_DOCUMENT, json, WHOLE), equalTo(RESOLVED));
        assertThat(probe.attributes().source(), equalTo(LlmSectionAttributes.SOURCE_REFERENCE));
        assertThat(probe.attributes().refId(), equalTo("gs://bucket/a.png"));
    }

    @Test
    public void shouldCompleteFileDataWithoutMediaType()
    {
        final String json = "{\"fileData\":{\"fileUri\":\"https://example.com/a.png\"}}";

        assertThat(runToEnd(probe, LlmSectionType.USER_DOCUMENT, json, WHOLE), equalTo(COMPLETE));
        assertThat(probe.attributes().mediaType(), nullValue());
        assertThat(probe.unclassified(), equalTo(true));
    }

    @Test
    public void shouldStayUnclassifiedUntilMediaTypeAfterData()
    {
        final String json = "{\"inlineData\":{\"data\":\"QUJD\"";

        assertThat(LlmAttributeProbeRunner.run(probe, LlmSectionType.USER_DOCUMENT, json, WHOLE), equalTo(PENDING));
        assertThat(probe.unclassified(), equalTo(true));
    }

    @Test
    public void shouldNotClassifyTextSectionsAsMedia()
    {
        probe.begin(LlmSectionType.USER_TEXT);

        assertThat(probe.unclassified(), equalTo(false));
    }
}
