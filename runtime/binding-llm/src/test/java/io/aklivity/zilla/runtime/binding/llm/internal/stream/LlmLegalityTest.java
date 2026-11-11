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
    private static final String CHAT_N = "openai/ccs:n";
    private static final String CHAT_LOGPROBS = "openai/ccs:logprobs";
    private static final String CHAT_AUDIO = "openai/ccs:audio";
    private static final String CHAT_MODALITIES = "openai/ccs:modalities";
    private static final String RESPONSES_BACKGROUND = "openai/rs:background";
    private static final String RESPONSES_PREVIOUS = "openai/rs:previous_response_id";
    private static final String RESPONSES_CONVERSATION = "openai/rs:conversation";

    @Test
    public void shouldTreatNullAsHarmlessForEveryBehaviouralMember()
    {
        assertThat(judged("stop").scalar(JsonEvent.VALUE_NULL, null).harmless(), equalTo(true));
        assertThat(judged("tool_choice").scalar(JsonEvent.VALUE_NULL, null).harmless(), equalTo(true));
        assertThat(judged("parallel_tool_calls").scalar(JsonEvent.VALUE_NULL, null).harmless(), equalTo(true));
        assertThat(judged("response_format").scalar(JsonEvent.VALUE_NULL, null).harmless(), equalTo(true));
        assertThat(judged(CHAT_N).scalar(JsonEvent.VALUE_NULL, null).harmless(), equalTo(true));
        assertThat(judged(CHAT_AUDIO).scalar(JsonEvent.VALUE_NULL, null).harmless(), equalTo(true));
        assertThat(judged(RESPONSES_CONVERSATION).scalar(JsonEvent.VALUE_NULL, null).harmless(), equalTo(true));
    }

    @Test
    public void shouldTreatParallelToolCallsTrueAsHarmless()
    {
        assertThat(judged("parallel_tool_calls").scalar(JsonEvent.VALUE_TRUE, null).harmless(), equalTo(true));
    }

    @Test
    public void shouldNotTreatParallelToolCallsFalseAsHarmless()
    {
        assertThat(judged("parallel_tool_calls").scalar(JsonEvent.VALUE_FALSE, null).harmless(), equalTo(false));
    }

    @Test
    public void shouldNotTreatParallelToolCallsThatIsNotBooleanAsHarmless()
    {
        assertThat(judged("parallel_tool_calls").scalar(JsonEvent.VALUE_STRING, "true").harmless(), equalTo(false));
        assertThat(judged("parallel_tool_calls").scalar(JsonEvent.VALUE_NUMBER, "1").harmless(), equalTo(false));
        assertThat(judged("parallel_tool_calls").start(JsonEvent.START_ARRAY).scalar(JsonEvent.VALUE_TRUE, null)
            .end(JsonEvent.END_ARRAY).harmless(), equalTo(false));
    }

    @Test
    public void shouldTreatToolChoiceAutoAsHarmless()
    {
        assertThat(judged("tool_choice").scalar(JsonEvent.VALUE_STRING, "auto").harmless(), equalTo(true));
    }

    @Test
    public void shouldNotTreatOtherToolChoiceAsHarmless()
    {
        assertThat(judged("tool_choice").scalar(JsonEvent.VALUE_STRING, "required").harmless(), equalTo(false));
        assertThat(judged("tool_choice").scalar(JsonEvent.VALUE_STRING, "none").harmless(), equalTo(false));
        assertThat(judged("tool_choice").scalar(JsonEvent.VALUE_STRING, "Auto").harmless(), equalTo(false));
        assertThat(judged("tool_choice").scalar(JsonEvent.VALUE_STRING, "autos").harmless(), equalTo(false));
        assertThat(judged("tool_choice").scalar(JsonEvent.VALUE_STRING, "aut").harmless(), equalTo(false));
        assertThat(judged("tool_choice").scalar(JsonEvent.VALUE_STRING, "").harmless(), equalTo(false));
        assertThat(judged("tool_choice").scalar(JsonEvent.VALUE_NUMBER, "auto").harmless(), equalTo(false));
        assertThat(judged("tool_choice").start(JsonEvent.START_OBJECT).end(JsonEvent.END_OBJECT).harmless(), equalTo(false));
        assertThat(judged("tool_choice").start(JsonEvent.START_ARRAY).scalar(JsonEvent.VALUE_STRING, "auto")
            .end(JsonEvent.END_ARRAY).harmless(), equalTo(false));
    }

    @Test
    public void shouldTreatResponseFormatTextAsHarmless()
    {
        assertThat(type("text").harmless(), equalTo(true));
    }

    @Test
    public void shouldNotTreatOtherResponseFormatAsHarmless()
    {
        assertThat(type("json_object").harmless(), equalTo(false));
        assertThat(type("json_schema").harmless(), equalTo(false));
        assertThat(type("Text").harmless(), equalTo(false));
        assertThat(type("texts").harmless(), equalTo(false));
        assertThat(type("").harmless(), equalTo(false));
    }

    @Test
    public void shouldNotTreatResponseFormatWithMoreMembersAsHarmless()
    {
        final Feed feed = judged("response_format")
            .start(JsonEvent.START_OBJECT)
            .key("type")
            .scalar(JsonEvent.VALUE_STRING, "text")
            .key("extra")
            .scalar(JsonEvent.VALUE_TRUE, null)
            .end(JsonEvent.END_OBJECT);

        assertThat(feed.harmless(), equalTo(false));
    }

    @Test
    public void shouldNotTreatResponseFormatWithMemberBeforeTypeAsHarmless()
    {
        final Feed feed = judged("response_format")
            .start(JsonEvent.START_OBJECT)
            .key("extra")
            .scalar(JsonEvent.VALUE_TRUE, null)
            .key("type")
            .scalar(JsonEvent.VALUE_STRING, "text")
            .end(JsonEvent.END_OBJECT);

        assertThat(feed.harmless(), equalTo(false));
    }

    @Test
    public void shouldNotTreatResponseFormatWithoutTypeAsHarmless()
    {
        assertThat(judged("response_format").start(JsonEvent.START_OBJECT).end(JsonEvent.END_OBJECT).harmless(), equalTo(false));
    }

    @Test
    public void shouldNotTreatResponseFormatThatIsNotObjectAsHarmless()
    {
        assertThat(judged("response_format").scalar(JsonEvent.VALUE_STRING, "text").harmless(), equalTo(false));
        assertThat(judged("response_format").start(JsonEvent.START_ARRAY).end(JsonEvent.END_ARRAY).harmless(), equalTo(false));
    }

    @Test
    public void shouldNotTreatResponseFormatTypeThatIsNotStringAsHarmless()
    {
        final Feed object = judged("response_format")
            .start(JsonEvent.START_OBJECT)
            .key("type")
            .start(JsonEvent.START_OBJECT)
            .key("type")
            .scalar(JsonEvent.VALUE_STRING, "text")
            .end(JsonEvent.END_OBJECT)
            .end(JsonEvent.END_OBJECT);
        final Feed array = judged("response_format")
            .start(JsonEvent.START_OBJECT)
            .key("type")
            .start(JsonEvent.START_ARRAY)
            .scalar(JsonEvent.VALUE_STRING, "text")
            .end(JsonEvent.END_ARRAY)
            .end(JsonEvent.END_OBJECT);
        final Feed number = judged("response_format")
            .start(JsonEvent.START_OBJECT)
            .key("type")
            .scalar(JsonEvent.VALUE_NUMBER, "text")
            .end(JsonEvent.END_OBJECT);

        assertThat(object.harmless(), equalTo(false));
        assertThat(array.harmless(), equalTo(false));
        assertThat(number.harmless(), equalTo(false));
    }

    @Test
    public void shouldJudgeResponseFormatByFirstTypeMember()
    {
        final Feed textFirst = judged("response_format")
            .start(JsonEvent.START_OBJECT)
            .key("type")
            .scalar(JsonEvent.VALUE_STRING, "text")
            .key("type")
            .scalar(JsonEvent.VALUE_STRING, "json_object")
            .end(JsonEvent.END_OBJECT);
        final Feed objectFirst = judged("response_format")
            .start(JsonEvent.START_OBJECT)
            .key("type")
            .scalar(JsonEvent.VALUE_STRING, "json_object")
            .key("type")
            .scalar(JsonEvent.VALUE_STRING, "text")
            .end(JsonEvent.END_OBJECT);

        assertThat(textFirst.harmless(), equalTo(true));
        assertThat(objectFirst.harmless(), equalTo(false));
    }

    @Test
    public void shouldIgnoreMembersNestedInsideTypeValueOfDuplicateTypeMember()
    {
        final Feed feed = judged("response_format")
            .start(JsonEvent.START_OBJECT)
            .key("type")
            .scalar(JsonEvent.VALUE_STRING, "text")
            .key("type")
            .start(JsonEvent.START_OBJECT)
            .key("extra")
            .scalar(JsonEvent.VALUE_TRUE, null)
            .end(JsonEvent.END_OBJECT)
            .end(JsonEvent.END_OBJECT);

        assertThat(feed.harmless(), equalTo(true));
    }

    @Test
    public void shouldTreatBehaviouralNumberOfOneAsHarmless()
    {
        assertThat(judged(CHAT_N).scalar(JsonEvent.VALUE_NUMBER, "1").harmless(), equalTo(true));
    }

    @Test
    public void shouldNotTreatOtherBehaviouralNumberAsHarmless()
    {
        assertThat(judged(CHAT_N).scalar(JsonEvent.VALUE_NUMBER, "2").harmless(), equalTo(false));
        assertThat(judged(CHAT_N).scalar(JsonEvent.VALUE_NUMBER, "1.0").harmless(), equalTo(false));
        assertThat(judged(CHAT_N).scalar(JsonEvent.VALUE_NUMBER, "11").harmless(), equalTo(false));
        assertThat(judged(CHAT_N).scalar(JsonEvent.VALUE_NUMBER, "").harmless(), equalTo(false));
        assertThat(judged(CHAT_N).scalar(JsonEvent.VALUE_STRING, "1").harmless(), equalTo(false));
        assertThat(judged(CHAT_N).scalar(JsonEvent.VALUE_TRUE, null).harmless(), equalTo(false));
    }

    @Test
    public void shouldTreatBehaviouralFalseAsHarmlessForLogprobsAndBackground()
    {
        assertThat(judged(CHAT_LOGPROBS).scalar(JsonEvent.VALUE_FALSE, null).harmless(), equalTo(true));
        assertThat(judged(RESPONSES_BACKGROUND).scalar(JsonEvent.VALUE_FALSE, null).harmless(), equalTo(true));
    }

    @Test
    public void shouldNotTreatBehaviouralTrueAsHarmlessForLogprobsAndBackground()
    {
        assertThat(judged(CHAT_LOGPROBS).scalar(JsonEvent.VALUE_TRUE, null).harmless(), equalTo(false));
        assertThat(judged(RESPONSES_BACKGROUND).scalar(JsonEvent.VALUE_TRUE, null).harmless(), equalTo(false));
        assertThat(judged(CHAT_LOGPROBS).scalar(JsonEvent.VALUE_NUMBER, "0").harmless(), equalTo(false));
    }

    @Test
    public void shouldNotTreatFalseAsHarmlessForOtherBehaviouralMembers()
    {
        assertThat(judged(CHAT_N).scalar(JsonEvent.VALUE_FALSE, null).harmless(), equalTo(false));
        assertThat(judged(CHAT_AUDIO).scalar(JsonEvent.VALUE_FALSE, null).harmless(), equalTo(false));
        assertThat(judged(RESPONSES_PREVIOUS).scalar(JsonEvent.VALUE_FALSE, null).harmless(), equalTo(false));
        assertThat(judged(RESPONSES_CONVERSATION).scalar(JsonEvent.VALUE_STRING, "c").harmless(), equalTo(false));
        assertThat(judged(CHAT_MODALITIES).start(JsonEvent.START_ARRAY).end(JsonEvent.END_ARRAY).harmless(), equalTo(false));
    }

    @Test
    public void shouldNotTreatAnyStopAsHarmless()
    {
        assertThat(judged("stop").scalar(JsonEvent.VALUE_STRING, "END").harmless(), equalTo(false));
        assertThat(judged("stop").scalar(JsonEvent.VALUE_FALSE, null).harmless(), equalTo(false));
        assertThat(judged("stop").start(JsonEvent.START_ARRAY).end(JsonEvent.END_ARRAY).harmless(), equalTo(false));
    }

    private static Feed type(
        String type)
    {
        return judged("response_format")
            .start(JsonEvent.START_OBJECT)
            .key("type")
            .scalar(JsonEvent.VALUE_STRING, type)
            .end(JsonEvent.END_OBJECT);
    }

    private static Feed judged(
        String member)
    {
        return new Feed(member);
    }

    private static final class Feed
    {
        private final String member;

        private int state;
        private int depth;

        private Feed(
            String member)
        {
            this.member = member;
            this.state = LlmLegality.UNDECIDED;
        }

        private Feed start(
            JsonEvent event)
        {
            depth++;
            state = LlmLegality.advance(member, state, depth, event, null);
            return this;
        }

        private Feed end(
            JsonEvent event)
        {
            depth--;
            state = LlmLegality.advance(member, state, depth, event, null);
            return this;
        }

        private Feed key(
            String name)
        {
            state = LlmLegality.advance(member, state, depth, JsonEvent.KEY_NAME, name);
            return this;
        }

        private Feed scalar(
            JsonEvent event,
            String text)
        {
            state = LlmLegality.advance(member, state, depth, event, text);
            return this;
        }

        private boolean harmless()
        {
            return LlmLegality.harmless(state);
        }
    }
}
