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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class LlmGeminiSignatureFromCanonicalTest
{
    private final LlmGeminiSignatureFromCanonical transform = new LlmGeminiSignatureFromCanonical();

    @Test
    public void shouldExtractSignatureAfterText()
    {
        assertTransforms(transform, json("{'text':'Hello','gemini:thoughtSignature':'c2ln'}"), "{}");

        assertThat(transform.signature(), equalTo("c2ln"));
        assertThat(transform.signature(), nullValue());
    }

    @Test
    public void shouldExtractSignatureBeforeText()
    {
        assertTransforms(transform, json("{'gemini:thoughtSignature':'c2ln','text':'Hello'}"), "{}");

        assertThat(transform.signature(), equalTo("c2ln"));
    }

    @Test
    public void shouldIgnoreOtherMembers()
    {
        assertTransforms(transform, json("{'text':'Hello','anthropic:signature':'sig','gemini:thought':true}"), "{}");

        assertThat(transform.signature(), nullValue());
    }

    @Test
    public void shouldAcceptPlainSignatures()
    {
        assertThat(LlmGeminiSignatureFromCanonical.plain("c2ln+/="), equalTo(true));
        assertThat(LlmGeminiSignatureFromCanonical.plain(""), equalTo(true));
    }

    @Test
    public void shouldRejectSignaturesThatNeedEscaping()
    {
        assertThat(LlmGeminiSignatureFromCanonical.plain("a\"b"), equalTo(false));
        assertThat(LlmGeminiSignatureFromCanonical.plain("a\\b"), equalTo(false));
        assertThat(LlmGeminiSignatureFromCanonical.plain("a\nb"), equalTo(false));
        assertThat(LlmGeminiSignatureFromCanonical.plain("a b"), equalTo(false));
        assertThat(LlmGeminiSignatureFromCanonical.plain("é"), equalTo(false));
    }
}
