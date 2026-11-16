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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.quote;

import java.util.function.Consumer;

import org.junit.Test;

public class LlmCacheBreakpointDropTest
{
    private static final String BREAKPOINT = "'prompt_cache_breakpoint':{'mode':'explicit','ttl':'1h'}";

    private final Consumer<LlmSectionAttributes> id = attributes -> attributes.toolCallId("call_1");
    private final Consumer<LlmSectionAttributes> name = attributes -> attributes.toolName("get_weather");
    private final Consumer<LlmSectionAttributes> both = id.andThen(name);

    @Test
    public void shouldDropBreakpointFromOpenaiImage()
    {
        assertTransforms(LlmOpenaiMediaFromCanonical.image("openai/ccs"),
            json("{'type':'image_url'," + BREAKPOINT + ",'image_url':{'url':'https://example.com/a.png'}}"),
            json("{'type':'image_url','image_url':{'url':'https://example.com/a.png'}}"));
    }

    @Test
    public void shouldDropBreakpointFromGeminiToolCall()
    {
        assertTransforms(new LlmGeminiToolCallFromCanonical(false), both, null,
            json("{'arguments':{'a':1}," + BREAKPOINT + "}"),
            json("{'functionCall':{'name':'get_weather','args':{'a':1},'id':'call_1'}}"));
    }

    @Test
    public void shouldDropBreakpointAndStrictFromGeminiToolDefinition()
    {
        assertTransforms(new LlmGeminiToolDefinitionFromCanonical(false),
            json("{'type':'function','function':{'name':'ping','strict':true," + BREAKPOINT + "}}"),
            json("{'name':'ping'}"));
    }
}
