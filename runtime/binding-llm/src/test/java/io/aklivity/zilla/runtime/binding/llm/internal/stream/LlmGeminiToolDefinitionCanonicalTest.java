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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRoundTrips;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmGeminiToolDefinitionCanonicalTest
{
    private static final String HUGE = "x".repeat(5000);

    private final LlmGeminiToolDefinitionToCanonical to = new LlmGeminiToolDefinitionToCanonical();
    private final LlmGeminiToolDefinitionFromCanonical from = new LlmGeminiToolDefinitionFromCanonical();

    @Test
    public void shouldWrapDeclarationAsFunction()
    {
        assertTransforms(to,
            json("{'name':'get_weather','description':'Get the weather','parametersJsonSchema':{'type':'object'}}"),
            json("{'type':'function','function':{'name':'get_weather','description':'Get the weather'," +
                "'parameters':{'type':'object'}}}"));
    }

    @Test
    public void shouldWrapOpenApiParametersAsParameters()
    {
        assertTransforms(to,
            json("{'name':'get_weather','parameters':{'type':'OBJECT'}}"),
            json("{'type':'function','function':{'name':'get_weather','parameters':{'type':'OBJECT'}}}"));
    }

    @Test
    public void shouldWrapMinimalDeclaration()
    {
        assertTransforms(to,
            json("{'name':'ping'}"),
            json("{'type':'function','function':{'name':'ping'}}"));
    }

    @Test
    public void shouldDropNonStringDescription()
    {
        assertTransforms(to,
            json("{'name':'ping','description':null}"),
            json("{'type':'function','function':{'name':'ping'}}"));
    }

    @Test
    public void shouldCarryNativeOnlyMembers()
    {
        assertTransforms(to,
            json("{'name':'ping','behavior':'NON_BLOCKING','parametersJsonSchema':{'a':1}}"),
            json("{'type':'function','function':{'name':'ping','gemini:behavior':'NON_BLOCKING','parameters':{'a':1}}}"));
    }

    @Test
    public void shouldWrapParametersLargerThanWindow()
    {
        assertTransforms(to,
            json("{'name':'ping','parametersJsonSchema':{'description':'" + HUGE + "'}}"),
            json("{'type':'function','function':{'name':'ping','parameters':{'description':'" + HUGE + "'}}}"));
    }

    @Test
    public void shouldRejectDeclarationWithoutName()
    {
        assertRejects(to, json("{'description':'Get the weather'}"));
    }

    @Test
    public void shouldUnwrapFunctionAsDeclaration()
    {
        assertTransforms(from,
            json("{'type':'function','function':{'name':'get_weather','description':'Get the weather'," +
                "'parameters':{'type':'object'}}}"),
            json("{'name':'get_weather','description':'Get the weather','parametersJsonSchema':{'type':'object'}}"));
    }

    @Test
    public void shouldUnwrapFunctionWithoutParameters()
    {
        assertTransforms(from,
            json("{'type':'function','function':{'name':'ping'}}"),
            json("{'name':'ping'}"));
    }

    @Test
    public void shouldRestoreOwnPrefixedMembers()
    {
        assertTransforms(from,
            json("{'type':'function','function':{'name':'ping','gemini:behavior':'NON_BLOCKING','openai:strict':true}}"),
            json("{'name':'ping','behavior':'NON_BLOCKING'}"));
    }

    @Test
    public void shouldRejectFunctionWithoutName()
    {
        assertRejects(from, json("{'type':'function','function':{'description':'x'}}"));
    }

    @Test
    public void shouldRoundTripDeclaration()
    {
        assertRoundTrips(to, from,
            json("{'name':'get_weather','description':'Get the weather','behavior':'NON_BLOCKING'," +
                "'parametersJsonSchema':{'type':'object','properties':{'city':{'type':'string'}}}}"));
    }
}
