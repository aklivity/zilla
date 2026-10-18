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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.quote;

import java.util.function.Consumer;

import org.junit.Test;

public class LlmOpenaiToolCallFromCanonicalTest
{
    private static final String HUGE = "x".repeat(5000);

    private final LlmOpenaiToolCallFromCanonical transform = new LlmOpenaiToolCallFromCanonical();

    private final Consumer<LlmSectionAttributes> id = attributes -> attributes.toolCallId("call_1");
    private final Consumer<LlmSectionAttributes> name = attributes -> attributes.toolName("get_weather");
    private final Consumer<LlmSectionAttributes> both = id.andThen(name);

    @Test
    public void shouldEscapeArgumentsWithAttributesKnownAtBegin()
    {
        assertTransforms(transform, both, null,
            json("{'arguments':{'city':'Paris'}}"),
            json("{'id':'call_1','type':'function','function':{'name':'get_weather','arguments':") +
                quote(json("{'city':'Paris'}")) + "}}");
    }

    @Test
    public void shouldEscapeNestedArguments()
    {
        final String arguments = "{\"q\":\"say \\\"hi\\\" \\\\ \u00e9 \\n\",\"n\":[1,2.5,true,null,{\"k\":\"v\"}],\"e\":{}}";

        assertTransforms(transform, both, null,
            "{\"arguments\":" + arguments + "}",
            json("{'id':'call_1','type':'function','function':{'name':'get_weather','arguments':") +
                quote(arguments) + "}}");
    }

    @Test
    public void shouldEscapeValueLargerThanWindow()
    {
        final String arguments = json("{'text':'" + HUGE + "'}");

        assertTransforms(transform, both, null,
            "{\"arguments\":" + arguments + "}",
            json("{'id':'call_1','type':'function','function':{'name':'get_weather','arguments':") +
                quote(arguments) + "}}");
    }

    @Test
    public void shouldWriteEmptyArgumentsWhenAbsent()
    {
        assertTransforms(transform, both, null,
            json("{}"),
            json("{'id':'call_1','type':'function','function':{'name':'get_weather','arguments':'{}'}}"));
    }

    @Test
    public void shouldWriteIdAfterArgumentsWhenKnownLate()
    {
        final String arguments = json("{'text':'" + HUGE + "'}");

        assertTransforms(transform, name, id,
            "{\"arguments\":" + arguments + "}",
            json("{'type':'function','function':{'name':'get_weather','arguments':") + quote(arguments) +
                json("},'id':'call_1'}"));
    }

    @Test
    public void shouldWriteNameAfterArgumentsWhenKnownLate()
    {
        final String arguments = json("{'text':'" + HUGE + "'}");

        assertTransforms(transform, id, name,
            "{\"arguments\":" + arguments + "}",
            json("{'id':'call_1','type':'function','function':{'arguments':") + quote(arguments) +
                json(",'name':'get_weather'}}"));
    }

    @Test
    public void shouldWriteIdAndNameAfterArgumentsWhenKnownLate()
    {
        final String arguments = json("{'text':'" + HUGE + "'}");

        assertTransforms(transform, null, both,
            "{\"arguments\":" + arguments + "}",
            json("{'type':'function','function':{'arguments':") + quote(arguments) +
                json(",'name':'get_weather'},'id':'call_1'}"));
    }

    @Test
    public void shouldRestoreOwnMembersBeforeAndAfterArguments()
    {
        assertTransforms(transform, both, null,
            json("{'openai:index':0,'arguments':{'a':1},'openai:note':'" + HUGE + "'}"),
            json("{'id':'call_1','type':'function','index':0,'function':{'name':'get_weather','arguments':") +
                quote(json("{'a':1}")) + json("},'note':'" + HUGE + "'}"));
    }

    @Test
    public void shouldDropOtherDialectMembersAndKeepUnprefixedMembers()
    {
        assertTransforms(transform, both, null,
            json("{'anthropic:cache_control':{'type':'ephemeral'},'arguments':{'a':1},'kept':true}"),
            json("{'id':'call_1','type':'function','function':{'name':'get_weather','arguments':") +
                quote(json("{'a':1}")) + json("},'kept':true}"));
    }

    @Test
    public void shouldRejectMissingId()
    {
        assertRejects(transform, name, null, json("{'arguments':{'city':'Paris'}}"));
    }

    @Test
    public void shouldRejectMissingName()
    {
        assertRejects(transform, id, null, json("{'arguments':{'city':'Paris'}}"));
    }

    @Test
    public void shouldRejectArgumentsThatAreNotAnObject()
    {
        assertRejects(transform, both, null, json("{'arguments':[1]}"));
        assertRejects(transform, both, null, json("{'arguments':5}"));
        assertRejects(transform, both, null, json("{'arguments':'x'}"));
    }

    @Test
    public void shouldRejectToolCallThatIsNotAnObject()
    {
        assertRejects(transform, both, null, json("[1]"));
        assertRejects(transform, both, null, json("5"));
        assertRejects(transform, both, null, " ");
    }
}
