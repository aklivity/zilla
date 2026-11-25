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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

public class KafkaCacheAckRecordTest
{
    @Rule
    public final TestRule timeout = new DisableOnDebug(new Timeout(30, SECONDS));

    @Test
    public void shouldNotReadBeforePublish()
    {
        KafkaCacheAckRecord record = new KafkaCacheAckRecord();
        KafkaCacheAckRecord.Snapshot snapshot = new KafkaCacheAckRecord.Snapshot();

        assertFalse(record.read(snapshot));
    }

    @Test
    public void shouldReadLatestPublished()
    {
        KafkaCacheAckRecord record = new KafkaCacheAckRecord();
        KafkaCacheAckRecord.Snapshot snapshot = new KafkaCacheAckRecord.Snapshot();

        record.publish(1L, 10L, 100L);
        record.publish(2L, 20L, 200L);

        assertTrue(record.read(snapshot));
        assertEquals(2L, snapshot.flushOffset());
        assertEquals(20L, snapshot.ackOffset());
        assertEquals(200L, snapshot.timestamp());
    }

    @Test
    public void shouldReadConsistentSnapshotWhilePublishing() throws Exception
    {
        final KafkaCacheAckRecord record = new KafkaCacheAckRecord();
        final AtomicBoolean done = new AtomicBoolean();

        final Thread writer = new Thread(() ->
        {
            for (long flush = 1L; flush <= 2_000_000L; flush++)
            {
                record.publish(flush, flush * 2L, flush * 3L);
            }
            done.set(true);
        });

        final KafkaCacheAckRecord.Snapshot snapshot = new KafkaCacheAckRecord.Snapshot();
        writer.start();

        while (!done.get())
        {
            if (record.read(snapshot))
            {
                assertEquals(snapshot.flushOffset() * 2L, snapshot.ackOffset());
                assertEquals(snapshot.flushOffset() * 3L, snapshot.timestamp());
            }
        }

        writer.join();
    }
}
