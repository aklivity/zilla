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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRejects;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class LlmOpenaiResponsesControlFromCanonicalTest
{
    private static final String LONG = "x".repeat(300);
    private static final String HUGE = "x".repeat(5000);

    private final LlmOpenaiResponsesControlFromCanonical transform = new LlmOpenaiResponsesControlFromCanonical(true);
    private final LlmOpenaiResponsesControlFromCanonical foreign = new LlmOpenaiResponsesControlFromCanonical(false);

    @Test
    public void shouldRestoreOwnPrefixedMember()
    {
        assertTransforms(transform, json("{'openai:store':false}"), "false");

        assertThat(transform.member(), equalTo("store"));
    }

    @Test
    public void shouldRestoreObjectValueUnchanged()
    {
        assertTransforms(transform,
            json("{'openai:text':{'format':{'type':'json_schema','schema':{'a':[1,{'b':'" + LONG + "'}],'anthropic:c':true}}}}"),
            json("{'format':{'type':'json_schema','schema':{'a':[1,{'b':'" + LONG + "'}],'anthropic:c':true}}}"));

        assertThat(transform.member(), equalTo("text"));
    }

    @Test
    public void shouldRestoreValueLargerThanWindow()
    {
        assertTransforms(transform,
            json("{'openai:metadata':{'note':'" + HUGE + "'}}"),
            json("{'note':'" + HUGE + "'}"));

        assertThat(transform.member(), equalTo("metadata"));
    }

    @Test
    public void shouldDropOwnPrefixedMemberAcrossDialects()
    {
        assertTransforms(foreign, json("{'openai:store':false}"), "null");

        assertThat(foreign.member(), nullValue());
    }

    @Test
    public void shouldPassUnprefixedMemberAcrossDialects()
    {
        assertTransforms(foreign, json("{'temperature':0.2}"), "0.2");

        assertThat(foreign.member(), equalTo("temperature"));
    }

    @Test
    public void shouldDropOtherDialectPrefixedMember()
    {
        assertTransforms(transform, json("{'anthropic:top_k':5}"), "null");

        assertThat(transform.member(), nullValue());
    }

    @Test
    public void shouldPassStream()
    {
        assertTransforms(transform, json("{'stream':true}"), "true");

        assertThat(transform.member(), equalTo("stream"));
    }

    @Test
    public void shouldDropStreamOptionsWithOnlyUsage()
    {
        assertTransforms(transform, json("{'stream_options':{'include_usage':true}}"), "null");

        assertThat(transform.member(), nullValue());
    }

    @Test
    public void shouldRestoreNativeOptionMembersAndDropUsage()
    {
        assertTransforms(transform,
            json("{'stream_options':{'include_usage':true,'openai:include_obfuscation':false}}"),
            json("{'include_obfuscation':false}"));

        assertThat(transform.member(), equalTo("stream_options"));
    }

    @Test
    public void shouldRestoreNativeOptionMembersAndDropForeign()
    {
        assertTransforms(transform,
            json("{'stream_options':{'openai:include_obfuscation':false,'anthropic:other':{'a':[1,'" + LONG +
                "']},'openai:extra':{'a':[1]},'include_usage':true}}"),
            json("{'include_obfuscation':false,'extra':{'a':[1]}}"));

        assertThat(transform.member(), equalTo("stream_options"));
    }

    @Test
    public void shouldDropForeignOptionMembersAcrossDialects()
    {
        assertTransforms(foreign,
            json("{'stream_options':{'include_usage':true,'openai:include_obfuscation':false}}"),
            "null");

        assertThat(foreign.member(), nullValue());
    }

    @Test
    public void shouldRejectMemberThatIsNotAnObject()
    {
        assertRejects(transform, json("[1]"));
    }
}
