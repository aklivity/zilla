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
import static io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheRoute.partitionPlacementKey;
import static io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheRoute.resolveLeaderId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class KafkaCacheRouteTest
{
    private static final int WORKERS_MAX = 64;
    private static final String[] MQTT_TOPICS = {"mqtt-devices", "mqtt-messages", "mqtt-retained", "mqtt-sessions"};

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

    @Test
    public void shouldPlacePartitionsOfOneTopicOnDistinctWorkersWhenPartitionsDoNotExceedWorkers()
    {
        for (int workers = 1; workers <= WORKERS_MAX; workers++)
        {
            for (String topic : MQTT_TOPICS)
            {
                final Set<Integer> placed = new HashSet<>();
                for (int partitionId = 0; partitionId < workers; partitionId++)
                {
                    placed.add(worker(topic, partitionId, workers));
                }

                assertEquals(topic + " over " + workers + " workers", workers, placed.size());
            }
        }
    }

    @Test
    public void shouldWrapPartitionsBeyondWorkersAroundTheSameWorkers()
    {
        for (int workers = 1; workers <= WORKERS_MAX; workers++)
        {
            for (int partitionId = 0; partitionId < 2 * workers + 3; partitionId++)
            {
                assertEquals(worker("mqtt-devices", partitionId, workers),
                    worker("mqtt-devices", partitionId + workers, workers));
            }
        }
    }

    @Test
    public void shouldNotStackTopicsOnTheSameWorkersWhenWorkersIsAPowerOfTwo()
    {
        for (int workers = 2; workers <= WORKERS_MAX; workers *= 2)
        {
            final Set<Integer> firstPartitionWorkers = new HashSet<>();
            for (int topic = 0; topic < 4 * WORKERS_MAX; topic++)
            {
                firstPartitionWorkers.add(worker("topic-" + topic, 0, workers));
            }

            assertTrue("topics stack on " + firstPartitionWorkers + " of " + workers,
                firstPartitionWorkers.size() >= Math.min(workers, 2 * workers / 3 + 1));
        }
    }

    @Test
    public void shouldGiveSingleTopicsDifferentWorkersAcrossTheMqttTopics()
    {
        final Set<Integer> firstPartitionWorkers = new HashSet<>();
        for (String topic : MQTT_TOPICS)
        {
            firstPartitionWorkers.add(worker(topic, 0, 32));
        }

        assertTrue("single partition topics stack: " + firstPartitionWorkers, firstPartitionWorkers.size() >= 3);
    }

    @Test
    public void shouldPlaceTheSameTopicNameOnTheSameWorkerRegardlessOfStringInstance()
    {
        for (int workers = 1; workers <= WORKERS_MAX; workers++)
        {
            for (int partitionId = 0; partitionId < 20; partitionId++)
            {
                assertEquals(
                    partitionPlacementKey(new String("mqtt-messages"), partitionId),
                    partitionPlacementKey(new StringBuilder("mqtt-").append("messages").toString(), partitionId));
            }
        }
    }

    @Test
    public void shouldPlaceDifferentTopicNamesWithDifferentKeys()
    {
        final Set<Long> keys = new HashSet<>();
        for (int topic = 0; topic < 1000; topic++)
        {
            keys.add(partitionPlacementKey("topic-" + topic, 0));
        }

        assertEquals(1000, keys.size());
        assertNotEquals(partitionPlacementKey("mqtt-sessions", 0), partitionPlacementKey("mqtt-retained", 0));
    }

    @Test
    public void shouldKeepPlacementKeyNonNegativeAndMonotonicInPartition()
    {
        for (String topic : MQTT_TOPICS)
        {
            for (int partitionId = 0; partitionId < 1024; partitionId++)
            {
                assertTrue(partitionPlacementKey(topic, partitionId) >= 0L);
                assertEquals(partitionPlacementKey(topic, partitionId) + 1L, partitionPlacementKey(topic, partitionId + 1));
            }
        }
    }

    @Test
    public void shouldSpreadTopicsEvenlyAcrossWorkers()
    {
        final int workers = 32;
        final int topics = 3200;
        final int[] counts = new int[workers];
        for (int topic = 0; topic < topics; topic++)
        {
            counts[worker("topic-" + topic, 0, workers)]++;
        }

        for (int count : counts)
        {
            assertTrue("uneven topic spread " + count, count > topics / workers / 2 && count < topics / workers * 3 / 2);
        }
    }

    @Test
    public void shouldPlaceTopicsAtStableWorkersAcrossJvmsAndNodes()
    {
        assertEquals(2344969489334106605L, partitionPlacementKey("mqtt-devices", 0));
        assertEquals(646276582970317104L, partitionPlacementKey("mqtt-messages", 0));
        assertEquals(3440201293770876168L, partitionPlacementKey("mqtt-retained", 0));
        assertEquals(2494877171855898649L, partitionPlacementKey("mqtt-sessions", 0));

        assertEquals(13, worker("mqtt-devices", 0, 32));
        assertEquals(16, worker("mqtt-messages", 0, 32));
        assertEquals(8, worker("mqtt-retained", 0, 32));
        assertEquals(25, worker("mqtt-sessions", 0, 32));
    }

    private static int worker(
        String topic,
        int partitionId,
        int workers)
    {
        return (int) Math.floorMod(partitionPlacementKey(topic, partitionId), (long) workers);
    }
}
