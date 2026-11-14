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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LlmMemberKeysTest
{
    private final LlmMemberKeys keys = new LlmMemberKeys("openai/rs");

    @Test
    public void shouldCopyUnprefixedKeyVerbatim()
    {
        assertEquals(LlmMemberKeys.COPY, keys.select("namespace"));
        assertEquals("namespace", keys.target().toString());
    }

    @Test
    public void shouldStripAdmittedDialectPrefix()
    {
        assertEquals(LlmMemberKeys.COPY, keys.select("openai/rs:strict"));
        assertEquals("strict", keys.target().toString());
        assertEquals(LlmMemberKeys.COPY, keys.select("openai:strict"));
        assertEquals("strict", keys.target().toString());
    }

    @Test
    public void shouldDropSharedForeignAndCacheBreakpointKeys()
    {
        assertEquals(LlmMemberKeys.DROP, keys.select("x:temperature"));
        assertEquals(LlmMemberKeys.DROP, keys.select("anthropic:thinking"));
        assertEquals(LlmMemberKeys.DROP, keys.select("prompt_cache_breakpoint"));
    }

    @Test
    public void shouldJudgeListedForeignBehaviourKeys()
    {
        assertEquals(LlmMemberKeys.JUDGE, keys.select("openai/ccs:n"));
        assertEquals(LlmMemberKeys.COPY, keys.select("openai/rs:conversation"));
    }
}
