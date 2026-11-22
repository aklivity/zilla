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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KafkaProduceFlushTest
{
    @Test
    public void shouldRequireFlushWhenFailing()
    {
        assertTrue(KafkaProduceFlush.required(true, 3L, 3L));
    }

    @Test
    public void shouldRequireFlushWhenFailingBeforeAnyProgress()
    {
        assertTrue(KafkaProduceFlush.required(true, -1L, -1L));
    }

    @Test
    public void shouldRequireFlushWhenProgressed()
    {
        assertTrue(KafkaProduceFlush.required(false, 5L, 3L));
    }

    @Test
    public void shouldRequireFlushWhenFirstEntryAcknowledged()
    {
        assertTrue(KafkaProduceFlush.required(false, 0L, -1L));
    }

    @Test
    public void shouldNotRequireFlushWhenNoProgress()
    {
        assertFalse(KafkaProduceFlush.required(false, 3L, 3L));
    }

    @Test
    public void shouldNotRequireFlushWhenBehind()
    {
        assertFalse(KafkaProduceFlush.required(false, 2L, 3L));
    }

    @Test
    public void shouldNotRequireFlushBeforeAnyProgress()
    {
        assertFalse(KafkaProduceFlush.required(false, -1L, -1L));
    }
}
