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

import org.junit.Test;

import io.aklivity.zilla.runtime.common.json.JsonEvent;

public class LlmLegalityTest
{
    private static final int LIMIT = 4096;

    private final LlmCapturedValue value = new LlmCapturedValue();

    @Test
    public void shouldTreatNullAsHarmlessForEveryBehaviouralMember()
    {
        scalar(JsonEvent.VALUE_NULL, null);

        assertThat(LlmLegality.harmless("stop", value), equalTo(true));
        assertThat(LlmLegality.harmless("tool_choice", value), equalTo(true));
        assertThat(LlmLegality.harmless("parallel_tool_calls", value), equalTo(true));
        assertThat(LlmLegality.harmless("response_format", value), equalTo(true));
    }

    @Test
    public void shouldTreatParallelToolCallsTrueAsHarmless()
    {
        scalar(JsonEvent.VALUE_TRUE, null);

        assertThat(LlmLegality.harmless("parallel_tool_calls", value), equalTo(true));
    }

    @Test
    public void shouldNotTreatParallelToolCallsFalseAsHarmless()
    {
        scalar(JsonEvent.VALUE_FALSE, null);

        assertThat(LlmLegality.harmless("parallel_tool_calls", value), equalTo(false));
    }

    @Test
    public void shouldNotTreatParallelToolCallsThatIsNotBooleanAsHarmless()
    {
        scalar(JsonEvent.VALUE_STRING, "true");

        assertThat(LlmLegality.harmless("parallel_tool_calls", value), equalTo(false));
    }

    @Test
    public void shouldTreatToolChoiceAutoAsHarmless()
    {
        scalar(JsonEvent.VALUE_STRING, "auto");

        assertThat(LlmLegality.harmless("tool_choice", value), equalTo(true));
    }

    @Test
    public void shouldNotTreatOtherToolChoiceAsHarmless()
    {
        scalar(JsonEvent.VALUE_STRING, "required");
        assertThat(LlmLegality.harmless("tool_choice", value), equalTo(false));

        scalar(JsonEvent.VALUE_STRING, "none");
        assertThat(LlmLegality.harmless("tool_choice", value), equalTo(false));

        scalar(JsonEvent.VALUE_STRING, "Auto");
        assertThat(LlmLegality.harmless("tool_choice", value), equalTo(false));

        value.clear();
        value.add(JsonEvent.START_OBJECT, null, LIMIT);
        value.add(JsonEvent.END_OBJECT, null, LIMIT);
        assertThat(LlmLegality.harmless("tool_choice", value), equalTo(false));
    }

    @Test
    public void shouldTreatResponseFormatTextAsHarmless()
    {
        type("text");

        assertThat(LlmLegality.harmless("response_format", value), equalTo(true));
    }

    @Test
    public void shouldNotTreatOtherResponseFormatAsHarmless()
    {
        type("json_object");
        assertThat(LlmLegality.harmless("response_format", value), equalTo(false));

        type("json_schema");
        assertThat(LlmLegality.harmless("response_format", value), equalTo(false));
    }

    @Test
    public void shouldNotTreatResponseFormatWithMoreMembersAsHarmless()
    {
        value.clear();
        value.add(JsonEvent.START_OBJECT, null, LIMIT);
        value.add(JsonEvent.KEY_NAME, "type", LIMIT);
        value.add(JsonEvent.VALUE_STRING, "text", LIMIT);
        value.add(JsonEvent.KEY_NAME, "extra", LIMIT);
        value.add(JsonEvent.VALUE_TRUE, null, LIMIT);
        value.add(JsonEvent.END_OBJECT, null, LIMIT);

        assertThat(LlmLegality.harmless("response_format", value), equalTo(false));
    }

    @Test
    public void shouldNotTreatResponseFormatWithoutTypeAsHarmless()
    {
        value.clear();
        value.add(JsonEvent.START_OBJECT, null, LIMIT);
        value.add(JsonEvent.END_OBJECT, null, LIMIT);

        assertThat(LlmLegality.harmless("response_format", value), equalTo(false));
    }

    @Test
    public void shouldNotTreatResponseFormatThatIsNotObjectAsHarmless()
    {
        scalar(JsonEvent.VALUE_STRING, "text");

        assertThat(LlmLegality.harmless("response_format", value), equalTo(false));
    }

    @Test
    public void shouldNotTreatAnyStopAsHarmless()
    {
        scalar(JsonEvent.VALUE_STRING, "END");
        assertThat(LlmLegality.harmless("stop", value), equalTo(false));

        value.clear();
        value.add(JsonEvent.START_ARRAY, null, LIMIT);
        value.add(JsonEvent.END_ARRAY, null, LIMIT);
        assertThat(LlmLegality.harmless("stop", value), equalTo(false));
    }

    private void scalar(
        JsonEvent event,
        String text)
    {
        value.clear();
        value.add(event, text, LIMIT);
    }

    private void type(
        String type)
    {
        value.clear();
        value.add(JsonEvent.START_OBJECT, null, LIMIT);
        value.add(JsonEvent.KEY_NAME, "type", LIMIT);
        value.add(JsonEvent.VALUE_STRING, type, LIMIT);
        value.add(JsonEvent.END_OBJECT, null, LIMIT);
    }
}
