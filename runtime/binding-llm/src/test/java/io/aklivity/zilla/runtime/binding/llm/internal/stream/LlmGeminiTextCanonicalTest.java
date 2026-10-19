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

public class LlmGeminiTextCanonicalTest
{
    private static final String TEXT = "Hello, how are you today? ".repeat(400);

    private final LlmGeminiTextToCanonical toCanonical = new LlmGeminiTextToCanonical();
    private final LlmGeminiTextFromCanonical fromCanonical = new LlmGeminiTextFromCanonical();

    @Test
    public void shouldConvertText()
    {
        assertTransforms(toCanonical,
            json("{'text':'Hello'}"),
            json("{'text':'Hello'}"));
    }

    @Test
    public void shouldCarryThoughtSignatureAfterText()
    {
        assertTransforms(toCanonical,
            json("{'text':'Hello','thoughtSignature':'sig-1'}"),
            json("{'text':'Hello','gemini:thoughtSignature':'sig-1'}"));
    }

    @Test
    public void shouldCarryThoughtSignatureBeforeText()
    {
        assertTransforms(toCanonical,
            json("{'thoughtSignature':'sig-1','text':'Hello'}"),
            json("{'gemini:thoughtSignature':'sig-1','text':'Hello'}"));
    }

    @Test
    public void shouldCarrySnakeCaseMemberWithSourceSpelling()
    {
        assertTransforms(toCanonical,
            json("{'text':'Hello','thought_signature':'sig-1'}"),
            json("{'text':'Hello','gemini:thought_signature':'sig-1'}"));
    }

    @Test
    public void shouldCarryObjectMember()
    {
        assertTransforms(toCanonical,
            json("{'text':'Hello','videoMetadata':{'startOffset':'1s','endOffset':'2s'}}"),
            json("{'text':'Hello','gemini:videoMetadata':{'startOffset':'1s','endOffset':'2s'}}"));
    }

    @Test
    public void shouldNotCarryThought()
    {
        assertTransforms(toCanonical,
            json("{'text':'Hello','thought':true}"),
            json("{'text':'Hello'}"));
    }

    @Test
    public void shouldConvertTextLargerThanWindow()
    {
        assertTransforms(toCanonical,
            json("{'text':'" + TEXT + "','thoughtSignature':'sig-1'}"),
            json("{'text':'" + TEXT + "','gemini:thoughtSignature':'sig-1'}"));
    }

    @Test
    public void shouldConvertEscapedText()
    {
        assertTransforms(toCanonical,
            json("{'text':'line one\\nline \\\"two\\\"'}"),
            json("{'text':'line one\\nline \\\"two\\\"'}"));
    }

    @Test
    public void shouldRejectNonStringText()
    {
        assertRejects(toCanonical, json("{'text':1}"));
    }

    @Test
    public void shouldRestoreText()
    {
        assertTransforms(fromCanonical,
            json("{'text':'Hello'}"),
            json("{'text':'Hello'}"));
    }

    @Test
    public void shouldRestoreOwnPrefixedMember()
    {
        assertTransforms(fromCanonical,
            json("{'text':'Hello','gemini:thoughtSignature':'sig-1'}"),
            json("{'text':'Hello','thoughtSignature':'sig-1'}"));
    }

    @Test
    public void shouldRestoreObjectMemberUnchanged()
    {
        assertTransforms(fromCanonical,
            json("{'text':'Hello','gemini:videoMetadata':{'startOffset':'1s'}}"),
            json("{'text':'Hello','videoMetadata':{'startOffset':'1s'}}"));
    }

    @Test
    public void shouldDropOtherDialectPrefixedMember()
    {
        assertTransforms(fromCanonical,
            json("{'text':'Hello','anthropic:signature':'sig-1','openai:refusal':null}"),
            json("{'text':'Hello'}"));
    }

    @Test
    public void shouldRoundTripText()
    {
        assertRoundTrips(toCanonical, fromCanonical,
            json("{'text':'Hello','thoughtSignature':'sig-1'}"));
    }
}
