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
    private final LlmGeminiServerToolUseFromCanonical useFromCanonical = new LlmGeminiServerToolUseFromCanonical(true);

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

    @Test
    public void shouldCanonicalizeToolCallInWireOrder()
    {
        assertTransforms(toCanonical,
            json("{'toolCall':{'toolType':'GOOGLE_SEARCH_WEB','args':{'queries':['paris']},'id':'call_1'}}"),
            json("{'gemini:toolType':'GOOGLE_SEARCH_WEB','arguments':{'queries':['paris']},'gemini:id':'call_1'}"));
    }

    @Test
    public void shouldCanonicalizeSnakeCaseToolCall()
    {
        assertTransforms(toCanonical,
            json("{'tool_call':{'tool_type':'GOOGLE_SEARCH_WEB','tool_name':'google_search','args':{}}}"),
            json("{'gemini:toolType':'GOOGLE_SEARCH_WEB','gemini:toolName':'google_search','arguments':{}}"));
    }

    @Test
    public void shouldCarryThoughtSignatureOfToolCall()
    {
        assertTransforms(toCanonical,
            json("{'toolCall':{'toolType':'URL_CONTEXT','args':{}},'thoughtSignature':'c2ln'}"),
            json("{'gemini:toolType':'URL_CONTEXT','arguments':{},'gemini:thoughtSignature':'c2ln'}"));
    }

    @Test
    public void shouldRestoreToolCall()
    {
        assertTransforms(useFromCanonical,
            json("{'gemini:toolType':'GOOGLE_SEARCH_WEB','arguments':{'queries':['paris']},'gemini:id':'call_1'," +
                "'gemini:thoughtSignature':'c2ln'}"),
            json("{'toolCall':{'toolType':'GOOGLE_SEARCH_WEB','args':{'queries':['paris']},'id':'call_1'}," +
                "'thoughtSignature':'c2ln'}"));
    }

    @Test
    public void shouldDropOtherDialectMembersOfToolCall()
    {
        assertTransforms(useFromCanonical,
            json("{'gemini:toolType':'URL_CONTEXT','arguments':{},'anthropic:server_name':'x','unknown':1}"),
            json("{'toolCall':{'toolType':'URL_CONTEXT','args':{}}}"));
    }

    @Test
    public void shouldRoundTripToolCall()
    {
        assertRoundTrips(toCanonical, useFromCanonical,
            json("{'toolCall':{'toolType':'GOOGLE_SEARCH_WEB','toolName':'google_search','args':{'queries':['" +
                CODE + "']},'id':'call_1'},'thoughtSignature':'c2ln'}"));
    }
}
