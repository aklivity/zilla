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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.json.JsonEvent;

public class LlmCacheBreakpointTest
{
    private static final String[] BOTH = {"5m", "1h"};
    private static final String[] SHORTEST = {"5m"};
    private static final int LIMIT = 4096;

    private final LlmCapturedValue value = new LlmCapturedValue();

    @Test
    public void shouldSelectRequestedTtlOffered()
    {
        assertThat(LlmCacheBreakpoint.select("5m", BOTH), equalTo("5m"));
        assertThat(LlmCacheBreakpoint.select("1h", BOTH), equalTo("1h"));
        assertThat(LlmCacheBreakpoint.select("300s", BOTH), equalTo("5m"));
        assertThat(LlmCacheBreakpoint.select("60m", BOTH), equalTo("1h"));
    }

    @Test
    public void shouldSelectSmallestTtlAtLeastRequested()
    {
        assertThat(LlmCacheBreakpoint.select("90s", BOTH), equalTo("5m"));
        assertThat(LlmCacheBreakpoint.select("0s", BOTH), equalTo("5m"));
        assertThat(LlmCacheBreakpoint.select("6m", BOTH), equalTo("1h"));
        assertThat(LlmCacheBreakpoint.select("30m", BOTH), equalTo("1h"));
    }

    @Test
    public void shouldSelectLargestTtlWhenRequestedExceedsEveryOffer()
    {
        assertThat(LlmCacheBreakpoint.select("2h", BOTH), equalTo("1h"));
        assertThat(LlmCacheBreakpoint.select("1d", BOTH), equalTo("1h"));
        assertThat(LlmCacheBreakpoint.select("1h", SHORTEST), equalTo("5m"));
        assertThat(LlmCacheBreakpoint.select("24h", SHORTEST), equalTo("5m"));
    }

    @Test
    public void shouldSelectNoTtlForAbsentOrMalformedDuration()
    {
        assertThat(LlmCacheBreakpoint.select(null, BOTH), nullValue());
        assertThat(LlmCacheBreakpoint.select("", BOTH), nullValue());
        assertThat(LlmCacheBreakpoint.select("m", BOTH), nullValue());
        assertThat(LlmCacheBreakpoint.select("5", BOTH), nullValue());
        assertThat(LlmCacheBreakpoint.select("5x", BOTH), nullValue());
        assertThat(LlmCacheBreakpoint.select("-5m", BOTH), nullValue());
        assertThat(LlmCacheBreakpoint.select("1.5h", BOTH), nullValue());
        assertThat(LlmCacheBreakpoint.select("99999999999h", BOTH), nullValue());
    }

    @Test
    public void shouldAdmitExplicitBreakpointWithTtl()
    {
        final LlmCacheBreakpoint breakpoints = new LlmCacheBreakpoint(4, "5m", "1h");

        breakpoint("explicit", "30m");

        assertThat(breakpoints.admit(value), is(true));
        assertThat(breakpoints.ttl(), equalTo("1h"));
    }

    @Test
    public void shouldAdmitExplicitBreakpointWithoutTtl()
    {
        final LlmCacheBreakpoint breakpoints = new LlmCacheBreakpoint(4, "5m", "1h");

        breakpoint("explicit", null);

        assertThat(breakpoints.admit(value), is(true));
        assertThat(breakpoints.ttl(), nullValue());
    }

    @Test
    public void shouldNotAdmitBreakpointThatIsNotExplicit()
    {
        final LlmCacheBreakpoint breakpoints = new LlmCacheBreakpoint(4, "5m", "1h");

        breakpoint("auto", "5m");

        assertThat(breakpoints.admit(value), is(false));
    }

    @Test
    public void shouldStopAdmittingAtTheLimitWithoutRejecting()
    {
        final LlmCacheBreakpoint breakpoints = new LlmCacheBreakpoint(2, "5m", "1h");

        breakpoint("explicit", "1h");

        assertThat(breakpoints.admit(value), is(true));
        assertThat(breakpoints.admit(value), is(true));
        assertThat(breakpoints.admit(value), is(false));
        assertThat(breakpoints.admit(value), is(false));
    }

    @Test
    public void shouldAdmitAgainAfterReset()
    {
        final LlmCacheBreakpoint breakpoints = new LlmCacheBreakpoint(1, "5m", "1h");

        breakpoint("explicit", null);

        assertThat(breakpoints.admit(value), is(true));
        assertThat(breakpoints.admit(value), is(false));

        breakpoints.reset();

        assertThat(breakpoints.admit(value), is(true));
    }

    private void breakpoint(
        String mode,
        String ttl)
    {
        value.clear();
        value.add(JsonEvent.START_OBJECT, null, LIMIT);
        value.add(JsonEvent.KEY_NAME, "mode", LIMIT);
        value.add(JsonEvent.VALUE_STRING, mode, LIMIT);

        if (ttl != null)
        {
            value.add(JsonEvent.KEY_NAME, "ttl", LIMIT);
            value.add(JsonEvent.VALUE_STRING, ttl, LIMIT);
        }

        value.add(JsonEvent.END_OBJECT, null, LIMIT);
    }
}
