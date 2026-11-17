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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class LlmAnthropicClientFactoryTest
{
    private static final String[] BOTH = {"5m", "1h"};
    private static final String[] SHORTEST = {"5m"};

    @Test
    public void shouldSelectRequestedTtlOffered()
    {
        assertThat(select("5m", BOTH), equalTo("5m"));
        assertThat(select("1h", BOTH), equalTo("1h"));
        assertThat(select("300s", BOTH), equalTo("5m"));
        assertThat(select("60m", BOTH), equalTo("1h"));
    }

    @Test
    public void shouldSelectSmallestTtlAtLeastRequested()
    {
        assertThat(select("90s", BOTH), equalTo("5m"));
        assertThat(select("0s", BOTH), equalTo("5m"));
        assertThat(select("6m", BOTH), equalTo("1h"));
        assertThat(select("30m", BOTH), equalTo("1h"));
    }

    @Test
    public void shouldSelectLargestTtlWhenRequestedExceedsEveryOffer()
    {
        assertThat(select("2h", BOTH), equalTo("1h"));
        assertThat(select("1d", BOTH), equalTo("1h"));
        assertThat(select("1h", SHORTEST), equalTo("5m"));
        assertThat(select("24h", SHORTEST), equalTo("5m"));
    }

    @Test
    public void shouldSelectNoTtlForAbsentOrMalformedDuration()
    {
        assertThat(select("", BOTH), nullValue());
        assertThat(select("m", BOTH), nullValue());
        assertThat(select("5", BOTH), nullValue());
        assertThat(select("5x", BOTH), nullValue());
        assertThat(select("-5m", BOTH), nullValue());
        assertThat(select("1.5h", BOTH), nullValue());
        assertThat(select("99999999999h", BOTH), nullValue());
        assertThat(LlmAnthropicClientFactory.roundUpTtl(-2L, BOTH), nullValue());
    }

    private static String select(
        String requested,
        String[] offered)
    {
        return LlmAnthropicClientFactory.roundUpTtl(LlmAnthropicClientFactory.ttlSeconds(requested), offered);
    }
}
