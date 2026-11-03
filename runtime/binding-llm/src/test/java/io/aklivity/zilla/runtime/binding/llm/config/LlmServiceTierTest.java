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
package io.aklivity.zilla.runtime.binding.llm.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;

import org.junit.Test;

public class LlmServiceTierTest
{
    @Test
    public void shouldResolveTiersByName()
    {
        assertThat(LlmServiceTier.of("auto"), equalTo(LlmServiceTier.AUTO));
        assertThat(LlmServiceTier.of("default"), equalTo(LlmServiceTier.DEFAULT));
        assertThat(LlmServiceTier.of("flex"), equalTo(LlmServiceTier.FLEX));
        assertThat(LlmServiceTier.of("priority"), equalTo(LlmServiceTier.PRIORITY));
    }

    @Test
    public void shouldNotResolveUnknownTier()
    {
        assertThat(LlmServiceTier.of("scale"), equalTo(LlmServiceTier.INVALID));
        assertThat(LlmServiceTier.of(""), equalTo(LlmServiceTier.INVALID));
        assertThat(LlmServiceTier.of("AUTO"), equalTo(LlmServiceTier.INVALID));
    }

    @Test
    public void shouldNameTiers()
    {
        for (int tier = 0; tier < LlmServiceTier.COUNT; tier++)
        {
            assertThat(LlmServiceTier.of(LlmServiceTier.nameOf(tier)), equalTo(tier));
        }
    }

    @Test
    public void shouldMaskTiers()
    {
        assertThat(LlmServiceTier.maskOf(List.of()), equalTo(0));
        assertThat(LlmServiceTier.maskOf(List.of("auto", "priority")), equalTo(0b1001));
        assertThat(LlmServiceTier.maskOf(List.of("auto", "default", "flex", "priority")), equalTo(LlmServiceTier.ALL));
        assertThat(LlmServiceTier.maskOf(List.of("unknown", "flex")), equalTo(0b0100));
    }
}
