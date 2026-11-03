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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAttributeProbe.Status.RESOLVED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

public class LlmAttributeProbeRestartTest
{
    private static final int OBJECT_PREFIX_EVENTS = 2;

    @Test
    public void shouldKeepAttributesAndWalkAgainAfterRestart()
    {
        final LlmOpenaiAttributeProbe probe = new LlmOpenaiAttributeProbe();
        final String json = "{\"type\":\"function\",\"id\":\"call_1\",\"function\":{\"name\":\"get_weather\"," +
            "\"arguments\":\"{}\"}}";

        probe.begin(LlmSectionType.TOOL_CALL);

        assertThat(walk(probe, json, false), equalTo(RESOLVED));
        assertThat(probe.depth(), equalTo(2));

        probe.restart();

        assertThat(probe.depth(), equalTo(1));
        assertThat(probe.attributes().toolCallId(), equalTo("call_1"));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));

        assertThat(walk(probe, json, true), equalTo(COMPLETE));
        assertThat(probe.depth(), equalTo(0));
        assertThat(probe.attributes().toolCallId(), equalTo("call_1"));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
    }

    @Test
    public void shouldForgetTheMemberKeyOfTheEarlierWalkAfterRestart()
    {
        final LlmOpenaiAttributeProbe probe = new LlmOpenaiAttributeProbe();
        final String json = "{\"type\":\"function\",\"id\":\"call_1\",\"function\":{\"arguments\":\"{}\"," +
            "\"name\":\"get_weather\"}}";

        probe.begin(LlmSectionType.TOOL_CALL);

        assertThat(walk(probe, json, true), equalTo(COMPLETE));

        probe.restart();

        assertThat(probe.key(1), equalTo(LlmAttributeProbe.NO_KEY));
        assertThat(probe.depth(), equalTo(1));
        assertThat(probe.attributes().toolName(), equalTo("get_weather"));
    }

    @Test
    public void shouldCountFunctionResponseMembersOnceAcrossRestart()
    {
        final LlmGeminiAttributeProbe probe = new LlmGeminiAttributeProbe();
        final String json =
            "{\"functionResponse\":{\"name\":\"get_weather\",\"id\":\"call_1\",\"response\":{\"output\":\"sunny\"}}}";

        probe.begin(LlmSectionType.TOOL_RESULT);

        assertThat(walk(probe, json, true), equalTo(COMPLETE));
        assertThat(probe.settle(), equalTo(LlmGeminiAttributeProbe.SHAPE_OUTPUT));

        probe.restart();

        assertThat(walk(probe, json, true), equalTo(COMPLETE));
        assertThat(probe.settle(), equalTo(LlmGeminiAttributeProbe.SHAPE_OUTPUT));
        assertThat(probe.attributes().isError(), nullValue());
    }

    private static LlmAttributeProbe.Status walk(
        LlmAttributeProbe probe,
        String json,
        boolean toEnd)
    {
        final JsonParserEx parser = open(json);

        LlmAttributeProbe.Status status = PENDING;
        while ((toEnd ? status != COMPLETE : status == PENDING) && parser.hasNextEvent())
        {
            status = probe.onEvent(parser.nextEvent(), parser);
        }

        return status;
    }

    private static JsonParserEx open(
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();

        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        for (int event = 0; event < OBJECT_PREFIX_EVENTS; event++)
        {
            parser.nextEvent();
        }

        return parser;
    }
}
