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

import org.junit.Test;

public class LlmGeminiToolResultToCanonicalTest
{
    private static final String HUGE = "x".repeat(5000);

    private final LlmGeminiToolResultToCanonical transform = new LlmGeminiToolResultToCanonical();

    @Test
    public void shouldUnwrapOutputAsText()
    {
        transform.shape(LlmGeminiAttributeProbe.SHAPE_OUTPUT);

        assertTransforms(transform,
            json("{'functionResponse':{'name':'get_weather','id':'call_1','response':{'output':'sunny'}}}"),
            json("{'text':'sunny'}"));
    }

    @Test
    public void shouldUnwrapErrorAsText()
    {
        transform.shape(LlmGeminiAttributeProbe.SHAPE_ERROR);

        assertTransforms(transform,
            json("{'functionResponse':{'name':'get_weather','id':'call_1','response':{'error':'boom'}}}"),
            json("{'text':'boom'}"));
    }

    @Test
    public void shouldUnwrapOutputLargerThanWindow()
    {
        transform.shape(LlmGeminiAttributeProbe.SHAPE_OUTPUT);

        assertTransforms(transform,
            json("{'functionResponse':{'name':'get_weather','response':{'output':'" + HUGE + "'}}}"),
            json("{'text':'" + HUGE + "'}"));
    }

    @Test
    public void shouldStringifyOtherResponses()
    {
        transform.shape(LlmGeminiAttributeProbe.SHAPE_OTHER);

        assertTransforms(transform,
            json("{'functionResponse':{'name':'get_weather','id':'call_1','response':{'temp':20,'unit':'C'}}}"),
            json("{'text':'{\\\"temp\\\":20,\\\"unit\\\":\\\"C\\\"}'}"));
    }

    @Test
    public void shouldCarryNativeOnlyMembers()
    {
        transform.shape(LlmGeminiAttributeProbe.SHAPE_OUTPUT);

        assertTransforms(transform,
            json("{'functionResponse':{'name':'get_weather','response':{'output':'sunny'},'willContinue':false}}"),
            json("{'text':'sunny','gemini:willContinue':false}"));
    }
}
