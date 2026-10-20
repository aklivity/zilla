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

import java.util.function.Consumer;

import org.junit.Test;

public class LlmGeminiToolCallCanonicalTest
{
    private static final String HUGE = "x".repeat(5000);

    private final LlmGeminiToolCallToCanonical to = new LlmGeminiToolCallToCanonical();
    private final LlmGeminiToolCallFromCanonical from = new LlmGeminiToolCallFromCanonical();

    private final Consumer<LlmSectionAttributes> id = attributes -> attributes.toolCallId("call_1");
    private final Consumer<LlmSectionAttributes> name = attributes -> attributes.toolName("get_weather");
    private final Consumer<LlmSectionAttributes> both = id.andThen(name);

    @Test
    public void shouldWrapArgsAsArguments()
    {
        assertTransforms(to,
            json("{'functionCall':{'name':'get_weather','args':{'city':'Paris'},'id':'call_1'}}"),
            json("{'arguments':{'city':'Paris'}}"));
    }

    @Test
    public void shouldWrapAbsentArgsAsEmptyArguments()
    {
        assertTransforms(to,
            json("{'functionCall':{'name':'ping'}}"),
            json("{'arguments':{}}"));
    }

    @Test
    public void shouldWrapArgsInAnyOrder()
    {
        assertTransforms(to,
            json("{'functionCall':{'id':'call_1','args':{'a':1},'name':'ping'}}"),
            json("{'arguments':{'a':1}}"));
    }

    @Test
    public void shouldCarryThoughtSignature()
    {
        assertTransforms(to,
            json("{'functionCall':{'name':'ping','args':{'a':1}},'thoughtSignature':'c2ln'}"),
            json("{'arguments':{'a':1},'gemini:thoughtSignature':'c2ln'}"));
    }

    @Test
    public void shouldCarryThoughtSignatureBeforeFunctionCall()
    {
        assertTransforms(to,
            json("{'thoughtSignature':'c2ln','functionCall':{'name':'ping','args':{'a':1}}}"),
            json("{'gemini:thoughtSignature':'c2ln','arguments':{'a':1}}"));
    }

    @Test
    public void shouldWrapArgsLargerThanWindow()
    {
        assertTransforms(to,
            json("{'functionCall':{'name':'ping','args':{'text':'" + HUGE + "'},'id':'call_1'}}"),
            json("{'arguments':{'text':'" + HUGE + "'}}"));
    }

    @Test
    public void shouldRejectArgsThatAreNotAnObject()
    {
        assertRejects(to, json("{'functionCall':{'name':'ping','args':'x'}}"));
        assertRejects(to, json("{'functionCall':{'name':'ping','args':[1]}}"));
    }

    @Test
    public void shouldWriteFunctionCallWithAttributesKnownAtBegin()
    {
        assertTransforms(from, both, null,
            json("{'arguments':{'city':'Paris'}}"),
            json("{'functionCall':{'name':'get_weather','args':{'city':'Paris'},'id':'call_1'}}"));
    }

    @Test
    public void shouldWriteEmptyArgsWhenArgumentsAbsent()
    {
        assertTransforms(from, both, null,
            json("{}"),
            json("{'functionCall':{'name':'get_weather','args':{},'id':'call_1'}}"));
    }

    @Test
    public void shouldWriteFunctionCallWithoutIdWhenAbsent()
    {
        assertTransforms(from, name, null,
            json("{'arguments':{'a':1}}"),
            json("{'functionCall':{'name':'get_weather','args':{'a':1}}}"));
    }

    @Test
    public void shouldWriteNameAfterArgsWhenKnownLate()
    {
        assertTransforms(from, id, name,
            json("{'arguments':{'text':'" + HUGE + "'}}"),
            json("{'functionCall':{'args':{'text':'" + HUGE + "'},'name':'get_weather','id':'call_1'}}"));
    }

    @Test
    public void shouldPreferWireIdOverAttribute()
    {
        assertTransforms(from, both, null,
            json("{'arguments':{'a':1},'gemini:id':'call_16347'}"),
            json("{'functionCall':{'name':'get_weather','args':{'a':1},'id':'call_16347'}}"));
    }

    @Test
    public void shouldWriteThoughtSignatureAfterFunctionCall()
    {
        assertTransforms(from, both, null,
            json("{'arguments':{'a':1},'gemini:thoughtSignature':'c2ln'}"),
            json("{'functionCall':{'name':'get_weather','args':{'a':1},'id':'call_1'},'thoughtSignature':'c2ln'}"));
    }

    @Test
    public void shouldWritePlaceholderSignatureWhenRequested()
    {
        from.placeholder(true);

        assertTransforms(from, both, null,
            json("{'arguments':{'a':1}}"),
            json("{'functionCall':{'name':'get_weather','args':{'a':1},'id':'call_1'}," +
                "'thoughtSignature':'skip_thought_signature_validator'}"));
    }

    @Test
    public void shouldKeepRestoredSignatureWhenPlaceholderRequested()
    {
        from.placeholder(true);

        assertTransforms(from, both, null,
            json("{'arguments':{'a':1},'gemini:thoughtSignature':'c2ln'}"),
            json("{'functionCall':{'name':'get_weather','args':{'a':1},'id':'call_1'},'thoughtSignature':'c2ln'}"));
    }

    @Test
    public void shouldRejectFunctionCallWithoutName()
    {
        assertRejects(from, json("{'arguments':{'a':1}}"));
    }

    @Test
    public void shouldRoundTripFunctionCall()
    {
        assertRoundTrips(to, from, both,
            json("{'functionCall':{'name':'get_weather','args':{'city':'Paris'},'id':'call_1'}," +
                "'thoughtSignature':'c2ln'}"));
    }
}
