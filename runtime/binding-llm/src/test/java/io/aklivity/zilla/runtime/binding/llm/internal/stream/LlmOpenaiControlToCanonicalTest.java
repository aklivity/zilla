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
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class LlmOpenaiControlToCanonicalTest
{
    private static final String LONG = "x".repeat(300);
    private static final String HUGE = "x".repeat(5000);

    private final LlmOpenaiControlToCanonical transform = new LlmOpenaiControlToCanonical();

    @Test
    public void shouldPrefixScalarMember()
    {
        assertTransforms(transform,
            json("{'temperature':0.7}"),
            json("{'openai:temperature':0.7}"));
    }

    @Test
    public void shouldPrefixStringMember()
    {
        assertTransforms(transform,
            json("{'user':'" + LONG + "'}"),
            json("{'openai:user':'" + LONG + "'}"));
    }

    @Test
    public void shouldPrefixNullMember()
    {
        assertTransforms(transform,
            json("{'logprobs':null}"),
            json("{'openai:logprobs':null}"));
    }

    @Test
    public void shouldMoveObjectValueUnchanged()
    {
        assertTransforms(transform,
            json("{'response_format':{'type':'json_schema','json_schema':{'name':'x','schema':{'a':[1,{'b':'" + LONG +
                "'}],'openai:c':true}}}}"),
            json("{'openai:response_format':{'type':'json_schema','json_schema':{'name':'x','schema':{'a':[1,{'b':'" + LONG +
                "'}],'openai:c':true}}}}"));
    }

    @Test
    public void shouldMoveArrayValueUnchanged()
    {
        assertTransforms(transform,
            json("{'stop':['a','b',{'c':1}]}"),
            json("{'openai:stop':['a','b',{'c':1}]}"));
    }

    @Test
    public void shouldMoveValueLargerThanWindow()
    {
        assertTransforms(transform,
            json("{'metadata':{'note':'" + HUGE + "'}}"),
            json("{'openai:metadata':{'note':'" + HUGE + "'}}"));
    }

    @Test
    public void shouldKeepAlreadyPrefixedKeyUnderOwnPrefix()
    {
        assertTransforms(transform,
            json("{'openai:x':1}"),
            json("{'openai:openai:x':1}"));
    }

    @Test
    public void shouldCarryStreamUnprefixed()
    {
        assertTransforms(transform, json("{'stream':true}"), json("{'stream':true}"));

        assertThat(transform.streamSeen(), is(true));
        assertThat(transform.streaming(), is(true));
        assertThat(transform.optionsSeen(), is(false));
    }

    @Test
    public void shouldCarryStreamFalse()
    {
        assertTransforms(transform, json("{'stream':false}"), json("{'stream':false}"));

        assertThat(transform.streamSeen(), is(true));
        assertThat(transform.streaming(), is(false));
    }

    @Test
    public void shouldCarryStreamNull()
    {
        assertTransforms(transform, json("{'stream':null}"), json("{'stream':null}"));

        assertThat(transform.streamSeen(), is(true));
        assertThat(transform.streaming(), is(false));
    }

    @Test
    public void shouldRejectStreamThatIsNotBoolean()
    {
        assertRejects(transform, json("{'stream':'yes'}"));
        assertRejects(transform, json("{'stream':{}}"));
    }

    @Test
    public void shouldForceIncludeUsageWhenFalse()
    {
        assertTransforms(transform,
            json("{'stream_options':{'include_usage':false}}"),
            json("{'stream_options':{'include_usage':true}}"));

        assertThat(transform.optionsSeen(), is(true));
        assertThat(transform.usageRequested(), is(false));
    }

    @Test
    public void shouldForceIncludeUsageWhenNull()
    {
        assertTransforms(transform,
            json("{'stream_options':{'include_usage':null}}"),
            json("{'stream_options':{'include_usage':true}}"));

        assertThat(transform.usageRequested(), is(false));
    }

    @Test
    public void shouldKeepIncludeUsageWhenTrue()
    {
        assertTransforms(transform,
            json("{'stream_options':{'include_usage':true}}"),
            json("{'stream_options':{'include_usage':true}}"));

        assertThat(transform.optionsSeen(), is(true));
        assertThat(transform.usageRequested(), is(true));
    }

    @Test
    public void shouldAppendIncludeUsageWhenAbsent()
    {
        assertTransforms(transform,
            json("{'stream_options':{}}"),
            json("{'stream_options':{'include_usage':true}}"));

        assertThat(transform.usageRequested(), is(false));
    }

    @Test
    public void shouldCreateOptionsWhenNull()
    {
        assertTransforms(transform,
            json("{'stream_options':null}"),
            json("{'stream_options':{'include_usage':true}}"));

        assertThat(transform.optionsSeen(), is(true));
        assertThat(transform.usageRequested(), is(false));
    }

    @Test
    public void shouldPrefixNativeOptionMembers()
    {
        assertTransforms(transform,
            json("{'stream_options':{'include_obfuscation':false,'include_usage':true,'extra':{'a':[1,'" + LONG + "']}}}"),
            json("{'stream_options':{'openai:include_obfuscation':false,'include_usage':true," +
                "'openai:extra':{'a':[1,'" + LONG + "']}}}"));

        assertThat(transform.usageRequested(), is(true));
    }

    @Test
    public void shouldAppendIncludeUsageAfterNativeOptionMembers()
    {
        assertTransforms(transform,
            json("{'stream_options':{'include_obfuscation':false}}"),
            json("{'stream_options':{'openai:include_obfuscation':false,'include_usage':true}}"));

        assertThat(transform.usageRequested(), is(false));
    }

    @Test
    public void shouldDetectObfuscationDisabled()
    {
        assertTransforms(transform,
            json("{'stream_options':{'include_obfuscation':false,'include_usage':true}}"),
            json("{'stream_options':{'openai:include_obfuscation':false,'include_usage':true}}"));

        assertThat(transform.obfuscationDisabled(), is(true));
    }

    @Test
    public void shouldKeepObfuscationWhenEnabled()
    {
        assertTransforms(transform,
            json("{'stream_options':{'include_obfuscation':true,'include_usage':true}}"),
            json("{'stream_options':{'openai:include_obfuscation':true,'include_usage':true}}"));

        assertThat(transform.obfuscationDisabled(), is(false));
    }

    @Test
    public void shouldRejectOptionsThatAreNotObjectOrNull()
    {
        assertRejects(transform, json("{'stream_options':'yes'}"));
        assertRejects(transform, json("{'stream_options':[1]}"));
    }

    @Test
    public void shouldRejectIncludeUsageThatIsNotBoolean()
    {
        assertRejects(transform, json("{'stream_options':{'include_usage':'yes'}}"));
    }

    @Test
    public void shouldRejectMemberThatIsNotAnObject()
    {
        assertRejects(transform, json("[1]"));
    }
}
