/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;

import org.junit.Test;

public class RetainedHeapMeterTest
{
    private static final int ITEMS = 1_000;
    private static final int ITEM_LENGTH = 1_024;

    private byte[][] retained;

    @Test
    public void shouldMeasureRetainedHeapPerItem() throws Exception
    {
        final RetainedHeapMeter meter = new RetainedHeapMeter();
        meter.mark();

        retained = new byte[ITEMS][];
        for (int i = 0; i < ITEMS; i++)
        {
            retained[i] = new byte[ITEM_LENGTH];
        }

        final long perItem = meter.deltaPerItem(ITEMS);

        assertThat(perItem, greaterThanOrEqualTo((long) ITEM_LENGTH));
        assertThat(perItem, lessThan(ITEM_LENGTH * 2L));
    }
}
