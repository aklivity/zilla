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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KafkaShareStagingTest
{
    private static final int FILE_NAME_BYTES_MAX = 255;

    @Test
    public void shouldDeriveSameDirectoryForSameWorkerGroupAndTopic()
    {
        assertEquals(KafkaShareStaging.directory(1, "zilla:share:g:Zg:0", "messages"),
            KafkaShareStaging.directory(1, "zilla:share:g:Zg:0", "messages"));
    }

    @Test
    public void shouldDeriveDistinctDirectoryPerWorker()
    {
        assertNotEquals(KafkaShareStaging.directory(1, "group", "messages"),
            KafkaShareStaging.directory(2, "group", "messages"));
    }

    @Test
    public void shouldDeriveDistinctDirectoryPerGroupId()
    {
        assertNotEquals(KafkaShareStaging.directory(1, "group.0", "messages"),
            KafkaShareStaging.directory(1, "group.1", "messages"));
    }

    @Test
    public void shouldDeriveDistinctDirectoryPerTopic()
    {
        assertNotEquals(KafkaShareStaging.directory(1, "group", "messages-a"),
            KafkaShareStaging.directory(1, "group", "messages-b"));
    }

    @Test
    public void shouldDeriveDistinctDirectoryWhenGroupIdAndTopicShareBoundary()
    {
        assertNotEquals(KafkaShareStaging.directory(1, "a-b", "c"),
            KafkaShareStaging.directory(1, "a", "b-c"));
    }

    @Test
    public void shouldDeriveDirectoryWithinFileNameLimitForLongGroupId()
    {
        final String groupId = "zilla.namespace-binding.share.name.".concat("x".repeat(600)).concat(".0");
        final String topic = "t".repeat(249);

        final String directory = KafkaShareStaging.directory(7, groupId, topic);

        assertTrue(directory, directory.getBytes(UTF_8).length <= FILE_NAME_BYTES_MAX);
    }

    @Test
    public void shouldDeriveDirectoryWithFileNameSafeCharacters()
    {
        final String directory = KafkaShareStaging.directory(0, "zilla:share/é %:0", "topic.中");

        assertTrue(directory, directory.matches("[a-z0-9-]+"));
    }
}
