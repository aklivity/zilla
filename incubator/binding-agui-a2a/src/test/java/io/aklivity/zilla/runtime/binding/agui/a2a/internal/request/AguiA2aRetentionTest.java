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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.request;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.buffer.DefaultBufferPool;

public class AguiA2aRetentionTest
{
    private static final int SLOT = 1024;

    @Test
    public void shouldAcquireOnceAndReleaseIdempotently()
    {
        final DefaultBufferPool pool = new DefaultBufferPool(SLOT, SLOT);
        final AguiA2aRetention retention = new AguiA2aRetention(pool);

        assertThat(retention.acquired(), is(false));
        assertThat(retention.acquire(1L), is(true));
        assertThat(retention.acquire(1L), is(true));
        assertThat(retention.acquired(), is(true));
        assertThat(retention.capacity(), equalTo(SLOT));
        assertThat(pool.acquiredSlots(), equalTo(1));

        retention.release();
        retention.release();

        assertThat(retention.acquired(), is(false));
        assertThat(pool.acquiredSlots(), equalTo(0));
    }

    @Test
    public void shouldFailToAcquireWhenBoundedPoolIsExhausted()
    {
        final DefaultBufferPool pool = new DefaultBufferPool(SLOT, SLOT);
        final AguiA2aRetention first = new AguiA2aRetention(pool);
        final AguiA2aRetention second = new AguiA2aRetention(pool);

        assertThat(first.acquire(1L), is(true));
        assertThat(second.acquire(2L), is(false));
        assertThat(second.acquired(), is(false));

        first.release();

        assertThat(second.acquire(2L), is(true));
        second.release();
        assertThat(pool.acquiredSlots(), equalTo(0));
    }
}
