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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import org.junit.Test;

public class LlmCircuitIdTest
{
    @Test
    public void shouldHashModelWithFnv1a()
    {
        assertThat(LlmCircuitId.of("gpt-4"), equalTo(1565145157792618507L));
        assertThat(LlmCircuitId.of("gpt-4o-mini"), equalTo(8124771531499716292L));
        assertThat(LlmCircuitId.of("claude-3-opus-20240229"), equalTo(-4343623895904507693L));
    }

    @Test
    public void shouldGiveSameCircuitForSameModel()
    {
        assertThat(LlmCircuitId.of(new String("gpt-4")), equalTo(LlmCircuitId.of("gpt-4")));
    }

    @Test
    public void shouldGiveDifferentCircuitForDifferentModel()
    {
        assertThat(LlmCircuitId.of("gpt-4"), not(equalTo(LlmCircuitId.of("gpt-4o"))));
    }

    @Test
    public void shouldHashUtf8BytesOfModel()
    {
        final String model = "méta-中文-😀";

        assertThat(LlmCircuitId.of(model), equalTo(hash(model.getBytes(UTF_8))));
    }

    @Test
    public void shouldRemapZeroHashToOne()
    {
        assertThat(LlmCircuitId.nonZero(0L), equalTo(1L));
        assertThat(LlmCircuitId.nonZero(7L), equalTo(7L));
    }

    @Test
    public void shouldNotScopeCircuitWithoutModel()
    {
        assertThat(LlmCircuitId.of(null), equalTo(0L));
    }

    private static long hash(
        byte[] bytes)
    {
        long hash = 0xcbf29ce484222325L;
        for (byte b : bytes)
        {
            hash ^= b & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
