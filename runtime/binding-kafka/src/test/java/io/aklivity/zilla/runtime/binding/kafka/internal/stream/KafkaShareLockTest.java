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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KafkaShareLockTest
{
    @Test
    public void shouldKeepLockTimeoutWhenNoTimeElapsed()
    {
        assertEquals(30000, KafkaShareLock.remainingMillis(30000, 1000L, 1000L));
    }

    @Test
    public void shouldReduceLockTimeoutByElapsedTime()
    {
        assertEquals(25000, KafkaShareLock.remainingMillis(30000, 1000L, 6000L));
    }

    @Test
    public void shouldReachZeroWhenElapsedTimeEqualsLockTimeout()
    {
        assertEquals(0, KafkaShareLock.remainingMillis(30000, 1000L, 31000L));
    }

    @Test
    public void shouldFloorAtZeroWhenElapsedTimeExceedsLockTimeout()
    {
        assertEquals(0, KafkaShareLock.remainingMillis(30000, 1000L, 1000000L));
    }

    @Test
    public void shouldNotExceedLockTimeoutWhenClockMovesBackwards()
    {
        assertEquals(30000, KafkaShareLock.remainingMillis(30000, 6000L, 1000L));
    }

    @Test
    public void shouldFloorAtZeroWhenLockTimeoutIsNotPositive()
    {
        assertEquals(0, KafkaShareLock.remainingMillis(0, 1000L, 1000L));
        assertEquals(0, KafkaShareLock.remainingMillis(-1, 1000L, 1000L));
    }
}
