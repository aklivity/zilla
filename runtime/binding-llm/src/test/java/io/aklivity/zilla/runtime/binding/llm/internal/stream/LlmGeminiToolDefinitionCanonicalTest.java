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

import org.junit.Test;

public class LlmGeminiToolDefinitionCanonicalTest
{
    private final LlmGeminiToolDefinitionFromCanonical from = new LlmGeminiToolDefinitionFromCanonical(true);

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
}
