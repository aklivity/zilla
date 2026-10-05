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

public class LlmOptionsTest
{
    @Test
    public void shouldResolveSlotsByMember()
    {
        final LlmOptions options = new LlmOptions("stream", "temperature", "top_p");

        assertEquals(0, options.slot("stream"));
        assertEquals(2, options.slot("top_p"));
        assertEquals("temperature", options.member(1));
        assertEquals(LlmOptions.NONE, options.slot("model"));
        assertEquals(LlmOptions.NONE, options.slot("temp"));
    }
}
