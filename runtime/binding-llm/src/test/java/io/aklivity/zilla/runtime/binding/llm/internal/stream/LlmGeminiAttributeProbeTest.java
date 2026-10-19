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
        assertThat(LlmGeminiAttributeProbe.carries(LlmSectionType.USER_TEXT), equalTo(false));
        assertThat(LlmGeminiAttributeProbe.carries(LlmSectionType.ASSISTANT_TEXT), equalTo(false));
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
}
