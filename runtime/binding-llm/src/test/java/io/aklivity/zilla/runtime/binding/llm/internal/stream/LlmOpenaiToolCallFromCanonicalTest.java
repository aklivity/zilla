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
    public void shouldWrapArgumentsWithAttributesKnownAtBegin()
    {
        assertTransforms(transform, both, null,
            json("{'city':'Paris'}"),
            json("{'id':'call_1','type':'function','function':{'name':'get_weather','arguments':") +
                quote(json("{'city':'Paris'}")) + "}}");
    }

    @Test
    public void shouldWrapEscapedAndNestedArguments()
    {
        final String arguments = "{\"q\":\"say \\\"hi\\\" \\\\ \u00e9 \\n\",\"n\":[1,2.5,true,null,{\"k\":\"v\"}],\"e\":{}}";

        assertTransforms(transform, both, null,
            arguments,
            json("{'id':'call_1','type':'function','function':{'name':'get_weather','arguments':") +
                quote(arguments) + "}}");
    }

    @Test
    public void shouldWrapValueLargerThanWindow()
    {
        final String arguments = json("{'text':'" + HUGE + "'}");

        assertTransforms(transform, both, null,
            arguments,
            json("{'id':'call_1','type':'function','function':{'name':'get_weather','arguments':") +
                quote(arguments) + "}}");
    }

    @Test
    public void shouldWriteIdAfterArgumentsWhenKnownLate()
    {
        final String arguments = json("{'text':'" + HUGE + "'}");

        assertTransforms(transform, name, id,
            arguments,
            json("{'type':'function','function':{'name':'get_weather','arguments':") + quote(arguments) +
                json("},'id':'call_1'}"));
    }

    @Test
    public void shouldWriteNameAfterArgumentsWhenKnownLate()
    {
        final String arguments = json("{'text':'" + HUGE + "'}");

        assertTransforms(transform, id, name,
            arguments,
            json("{'id':'call_1','type':'function','function':{'arguments':") + quote(arguments) +
                json(",'name':'get_weather'}}"));
    }

    @Test
    public void shouldWriteIdAndNameAfterArgumentsWhenKnownLate()
    {
        final String arguments = json("{'text':'" + HUGE + "'}");

        assertTransforms(transform, null, both,
            arguments,
            json("{'type':'function','function':{'arguments':") + quote(arguments) +
                json(",'name':'get_weather'},'id':'call_1'}"));
    }

    @Test
    public void shouldRejectMissingId()
    {
        assertRejects(transform, name, null, json("{'city':'Paris'}"));
    }

    @Test
    public void shouldRejectMissingName()
    {
        assertRejects(transform, id, null, json("{'city':'Paris'}"));
    }

    @Test
    public void shouldRejectArgumentsThatAreNotAnObject()
    {
        assertRejects(transform, both, null, json("[1]"));
        assertRejects(transform, both, null, json("5"));
        assertRejects(transform, both, null, json("'x'"));
    }

    @Test
    public void shouldRejectMissingArguments()
    {
        assertRejects(transform, both, null, " ");
    }
}
