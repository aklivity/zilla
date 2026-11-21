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

import static io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheRoute.LEADER_UNKNOWN;
import static io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheRoute.resolveLeaderId;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KafkaCacheRouteTest
{
    @Test
    public void shouldKeepKnownLeaderOverAffinity()
    {
        assertEquals(1, resolveLeaderId(1, 2L));
    }

    @Test
    public void shouldResolveUnknownLeaderFromAffinity()
    {
        assertEquals(2, resolveLeaderId(LEADER_UNKNOWN, 2L));
    }

    @Test
    public void shouldResolveUnknownLeaderFromAffinityOfBrokerIdZero()
    {
        assertEquals(0, resolveLeaderId(LEADER_UNKNOWN, 0L));
    }

    @Test
    public void shouldKeepUnknownLeaderWhenAffinityIsUnknownLeader()
    {
        assertEquals(LEADER_UNKNOWN, resolveLeaderId(LEADER_UNKNOWN, LEADER_UNKNOWN));
    }
}
