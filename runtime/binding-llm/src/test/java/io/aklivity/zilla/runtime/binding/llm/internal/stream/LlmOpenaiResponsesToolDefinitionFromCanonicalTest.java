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

public class LlmOpenaiResponsesToolDefinitionFromCanonicalTest
{
    private final LlmOpenaiResponsesToolDefinitionFromCanonical same = new LlmOpenaiResponsesToolDefinitionFromCanonical(true);
    private final LlmOpenaiResponsesToolDefinitionFromCanonical foreign =
        new LlmOpenaiResponsesToolDefinitionFromCanonical(false);

    @Test
    public void shouldFlattenFunctionTool()
    {
        assertTransforms(same,
            json("{'type':'function','function':{'name':'get_weather','description':'Get','parameters':{'a':1}," +
                "'strict':true}}"),
            json("{'type':'function','name':'get_weather','description':'Get','parameters':{'a':1},'strict':true}"));
    }

    @Test
    public void shouldRestoreOwnPrefixedMembers()
    {
        assertTransforms(same,
            json("{'type':'function','function':{'name':'get_weather','openai:defer_loading':true}}"),
            json("{'type':'function','name':'get_weather','defer_loading':true}"));
    }

    @Test
    public void shouldDropOtherDialectMembers()
    {
        assertTransforms(foreign,
            json("{'type':'function','function':{'name':'get_weather','openai:defer_loading':true,'kept':1}}"),
            json("{'type':'function','name':'get_weather','kept':1}"));
    }

    @Test
    public void shouldRejectMissingName()
    {
        assertRejects(same, json("{'type':'function','function':{'description':'Get'}}"));
    }
}
