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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRoundTrips;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmGeminiServerToolCallCanonicalTest
{
    private static final String CODE = "print(1)\\n".repeat(600);

    private final LlmGeminiServerToolCallToCanonical toCanonical = new LlmGeminiServerToolCallToCanonical();
    private final LlmGeminiServerToolCallFromCanonical fromCanonical = new LlmGeminiServerToolCallFromCanonical(true);

    @Test
    public void shouldCanonicalizeExecutableCodeAsArguments()
    {
        assertTransforms(toCanonical,
            json("{'executableCode':{'language':'PYTHON','code':'print(1)'}}"),
            json("{'arguments':{'language':'PYTHON','code':'print(1)'}}"));
    }

    @Test
    public void shouldKeepWireIdAsPeerOfArguments()
    {
        assertTransforms(toCanonical,
            json("{'executableCode':{'language':'PYTHON','code':'print(1)','id':'call_1'}}"),
            json("{'arguments':{'language':'PYTHON','code':'print(1)'},'gemini:id':'call_1'}"));
    }

    @Test
    public void shouldCanonicalizeSnakeCaseExecutableCode()
    {
        assertTransforms(toCanonical,
            json("{'executable_code':{'language':'PYTHON','code':'print(1)'}}"),
            json("{'arguments':{'language':'PYTHON','code':'print(1)'}}"));
    }

    @Test
    public void shouldCarryThoughtSignatureWithPrefix()
    {
        assertTransforms(toCanonical,
            json("{'executableCode':{'code':'print(1)'},'thoughtSignature':'c2ln'}"),
            json("{'arguments':{'code':'print(1)'},'gemini:thoughtSignature':'c2ln'}"));
    }

    @Test
    public void shouldCanonicalizeCodeLargerThanWindow()
    {
        assertTransforms(toCanonical,
            json("{'executableCode':{'language':'PYTHON','code':'" + CODE + "'}}"),
            json("{'arguments':{'language':'PYTHON','code':'" + CODE + "'}}"));
    }

    @Test
    public void shouldRestoreExecutableCodeWithWireIdMember()
    {
        assertTransforms(fromCanonical,
            json("{'arguments':{'language':'PYTHON','code':'print(1)'},'gemini:id':'call_1','gemini:thoughtSignature':'c2ln'}"),
            json("{'executableCode':{'language':'PYTHON','code':'print(1)','id':'call_1'},'thoughtSignature':'c2ln'}"));
    }

    @Test
    public void shouldNotWriteIdFromAttribute()
    {
        assertTransforms(fromCanonical, attributes -> attributes.toolCallId("call_2"), null,
            json("{'arguments':{'language':'PYTHON','code':'print(1)'}}"),
            json("{'executableCode':{'language':'PYTHON','code':'print(1)'}}"));
    }

    @Test
    public void shouldDropOtherDialectMembers()
    {
        assertTransforms(fromCanonical,
            json("{'arguments':{'code':'print(1)'},'anthropic:cache_control':{'type':'ephemeral'},'unknown':1}"),
            json("{'executableCode':{'code':'print(1)'}}"));
    }

    @Test
    public void shouldRoundTripExecutableCode()
    {
        assertRoundTrips(toCanonical, fromCanonical,
            json("{'executableCode':{'language':'PYTHON','code':'" + CODE + "','id':'call_1'},'thoughtSignature':'c2ln'}"));
    }
}
