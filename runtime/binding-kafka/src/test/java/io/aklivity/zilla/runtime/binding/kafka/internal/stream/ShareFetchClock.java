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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

public final class ShareFetchClock extends Clock
{
    private static final AtomicLong MILLIS = new AtomicLong();
    private static final AtomicLong STEP = new AtomicLong();

    public static Clock supplyClock()
    {
        return new ShareFetchClock();
    }

    public static void advanceBy(
        long stepMillis)
    {
        STEP.set(stepMillis);
    }

    public static void freeze()
    {
        STEP.set(0L);
    }

    @Override
    public ZoneId getZone()
    {
        return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(
        ZoneId zone)
    {
        return this;
    }

    @Override
    public long millis()
    {
        return MILLIS.getAndAdd(STEP.get());
    }

    @Override
    public Instant instant()
    {
        return Instant.ofEpochMilli(millis());
    }

    private ShareFetchClock()
    {
    }
}
