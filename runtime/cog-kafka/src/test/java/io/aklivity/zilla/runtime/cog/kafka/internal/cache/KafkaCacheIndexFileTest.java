/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.kafka.internal.cache;

import static io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCacheCursorRecord.NEXT_SEGMENT_VALUE;
import static io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCacheCursorRecord.RETRY_SEGMENT_VALUE;
import static io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCacheCursorRecord.cursor;
import static io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCacheCursorRecord.cursorIndex;
import static io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCacheCursorRecord.cursorValue;
import static io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCacheIndexRecord.SIZEOF_INDEX_RECORD;
import static io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCacheIndexRecord.indexEntry;
import static io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCacheIndexRecord.indexKey;
import static java.nio.ByteBuffer.allocate;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class KafkaCacheIndexFileTest
{
    public static class SortedByKeyTest
    {
        @Rule
        public TemporaryFolder tempFolder = new TemporaryFolder();

        private KafkaCacheIndexFile indexFile;
        private int key;

        @Before
        public void initEntries() throws Exception
        {
            Random random = ThreadLocalRandom.current();
            File tempFile = tempFolder.newFile();

            int entries = 1024;
            ByteBuffer indexEntryHolder = allocate(SIZEOF_INDEX_RECORD).order(nativeOrder());
            try (FileChannel channel = FileChannel.open(tempFile.toPath(), CREATE, APPEND))
            {
                for (int indexKey = 0; indexKey < entries; indexKey++)
                {
                    long indexEntry = indexEntry(indexKey >> 1, indexKey & 0x01);
                    indexEntryHolder.clear();
                    indexEntryHolder.putLong(indexEntry);
                    indexEntryHolder.flip();
                    channel.write(indexEntryHolder);
                }
            }

            key = random.nextInt(entries >> 1);
            indexFile = new KafkaCacheIndexFile.SortedByKey(tempFile.toPath());
        }

        @Test
        public void shouldSeekFirstKey()
        {
            long first = indexFile.first(key);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(first));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(first));
            assertEquals(key << 1, cursorIndex(first));
            assertEquals(0, cursorValue(first));
        }

        @Test
        public void shouldSeekLastKey()
        {
            long last = indexFile.last(key);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(last));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(last));
            assertEquals((key << 1) + 1, cursorIndex(last));
            assertEquals(1, cursorValue(last));
        }

        @Test
        public void shouldSeekFloorKey()
        {
            long floor = indexFile.floor(key);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(floor));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(floor));
            assertEquals(key << 1, cursorIndex(floor));
            assertEquals(0, cursorValue(floor));
        }

        @Test
        public void shouldResolve()
        {
            long cursor = cursor(key << 1, 0);
            long resolved = indexFile.resolve(cursor);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(resolved));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(resolved));
            assertEquals(key << 1, cursorIndex(resolved));
            assertEquals(0, cursorValue(resolved));
        }

        @Test
        public void shouldSeekHigher()
        {
            long cursor = cursor(key << 1, 0);
            long higher = indexFile.higher(key, cursor);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(higher));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(higher));
            assertEquals((key << 1) + 1, cursorIndex(higher));
        }

        @Test
        public void shouldSeekCeiling()
        {
            long cursor = cursor(key << 1, 0);
            long ceiling = indexFile.ceiling(key, cursor);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(ceiling));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(ceiling));
            assertEquals(key << 1, cursorIndex(ceiling));
        }

        @Test
        public void shouldSeekFloor()
        {
            long cursor = cursor((key << 1) + 1, 1);
            long floor = indexFile.floor(key, cursor);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(floor));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(floor));
            assertEquals((key << 1) + 1, cursorIndex(floor));
        }

        @Test
        public void shouldSeekLower()
        {
            long cursor = cursor((key << 1) + 1, 1);
            long lower = indexFile.lower(key, cursor);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(lower));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(lower));
            assertEquals(key << 1, cursorIndex(lower));
        }
    }

    public static class SortedByValueTest
    {
        @Rule
        public TemporaryFolder tempFolder = new TemporaryFolder();

        private KafkaCacheIndexFile.SortedByValue indexFile;
        private int key;
        private int entries;

        @Before
        public void initEntries() throws Exception
        {
            Random random = ThreadLocalRandom.current();
            File tempFile = tempFolder.newFile();

            entries = 1024;
            ByteBuffer indexEntryHolder = allocate(SIZEOF_INDEX_RECORD).order(nativeOrder());
            try (FileChannel channel = FileChannel.open(tempFile.toPath(), CREATE, APPEND))
            {
                for (int indexKey = 0; indexKey < entries; indexKey++)
                {
                    long indexEntry = indexEntry((entries - indexKey - 1) >> 1, (indexKey >> 1) + (indexKey & 0x01));
                    indexEntryHolder.clear();
                    indexEntryHolder.putLong(indexEntry);
                    indexEntryHolder.flip();
                    channel.write(indexEntryHolder);
                }
            }

            key = random.nextInt(entries >> 1);
            indexFile = new KafkaCacheIndexFile.SortedByValue(tempFile.toPath(), long[]::new);
        }

        @Test
        public void shouldSeekFirstKey()
        {
            long first = indexFile.first(key);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(first));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(first));
            assertEquals(entries - 1 - ((key << 1) + 1), cursorIndex(first));
            assertEquals((entries - 1 - ((key << 1) + 1)) >> 1, cursorValue(first));
        }

        @Test
        public void shouldSeekLastKey()
        {
            long last = indexFile.last(key);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(last));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(last));
            assertEquals(entries - 1 - (key << 1), cursorIndex(last));
            assertEquals(((entries - 1 - ((key << 1) + 1)) >> 1) + 1, cursorValue(last));
        }

        @Test
        public void shouldSeekFloorKey()
        {
            long floor = indexFile.floor(key);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(floor));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(floor));
            assertEquals(entries - 1 - ((key << 1) + 1), cursorIndex(floor));
            assertEquals((entries - 1 - ((key << 1) + 1)) >> 1, cursorValue(floor));
        }

        @Test
        public void shouldResolve()
        {
            long cursor = cursor(key << 1, 0);
            long resolved = indexFile.resolve(cursor);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(resolved));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(resolved));
            assertEquals(cursorIndex(cursor), cursorIndex(resolved));
            assertEquals(key, cursorValue(resolved));
        }

        @Test
        public void shouldSeekHigher()
        {
            long cursor = cursor(entries - 1 - ((key << 1) + 1), (entries - 1 - ((key << 1) + 1)) >> 1);
            long higher = indexFile.higher(key, cursor);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(higher));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(higher));
            assertEquals(cursorIndex(cursor) + 1, cursorIndex(higher));
            assertEquals(cursorValue(cursor) + 1, cursorValue(higher));
        }

        @Test
        public void shouldSeekCeiling()
        {
            long cursor = cursor(entries - 1 - ((key << 1) + 1), (entries - 1 - ((key << 1) + 1)) >> 1);
            long ceiling = indexFile.ceiling(key, cursor);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(ceiling));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(ceiling));
            assertEquals(cursorIndex(cursor), cursorIndex(ceiling));
            assertEquals(cursorValue(cursor), cursorValue(ceiling));
        }

        @Test
        public void shouldSeekFloor()
        {
            long cursor = cursor(entries - 1 - (key << 1), ((entries - 1 - ((key << 1) + 1)) >> 1) + 1);
            long floor = indexFile.floor(key, cursor);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(floor));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(floor));
            assertEquals(cursorIndex(cursor), cursorIndex(floor));
            assertEquals(cursorValue(cursor), cursorValue(floor));
        }

        @Test
        public void shouldSeekLower()
        {
            long cursor = cursor(entries - 1 - (key << 1), ((entries - 1 - ((key << 1) + 1)) >> 1) + 1);
            long lower = indexFile.lower(key, cursor);

            assertNotEquals(NEXT_SEGMENT_VALUE, cursorValue(lower));
            assertNotEquals(RETRY_SEGMENT_VALUE, cursorValue(lower));
            assertEquals(cursorIndex(cursor) - 1, cursorIndex(lower));
            assertEquals(cursorValue(cursor) - 1, cursorValue(lower));
        }

        @Test
        public void shouldSortByKey() throws Exception
        {
            Path workingFile = new File(tempFolder.getRoot(), "working").toPath();
            Path sortedFile = new File(tempFolder.getRoot(), "sorted").toPath();

            indexFile.sortByKey(workingFile, sortedFile);

            ByteBuffer indexEntryHolder = allocate(SIZEOF_INDEX_RECORD).order(nativeOrder());
            try (FileChannel channel = FileChannel.open(sortedFile, READ))
            {
                assert channel.size() == SIZEOF_INDEX_RECORD * entries;

                int previousIndexKey = 0;
                while (channel.position() < channel.size())
                {
                    indexEntryHolder.clear();
                    int read = channel.read(indexEntryHolder);
                    assert read == SIZEOF_INDEX_RECORD;
                    indexEntryHolder.flip();

                    long indexEntry = indexEntryHolder.getLong();
                    int indexKey = indexKey(indexEntry);

                    assert indexKey >= previousIndexKey;
                    previousIndexKey = indexKey;
                }
            }
        }
    }

    public static class SortedByValueWithDuplicatesTest
    {
        @Rule
        public TemporaryFolder tempFolder = new TemporaryFolder();

        private KafkaCacheIndexFile.SortedByValue indexFile;
        private int entries;

        @Before
        public void initEntries() throws Exception
        {
            File tempFile = tempFolder.newFile();

            entries = 1024;
            ByteBuffer indexEntryHolder = allocate(SIZEOF_INDEX_RECORD).order(nativeOrder());
            try (FileChannel channel = FileChannel.open(tempFile.toPath(), CREATE, APPEND))
            {
                for (int indexKey = 0; indexKey < entries; indexKey++)
                {
                    long indexEntry = indexEntry((entries - indexKey - 1) >> 1, indexKey >> 1);
                    indexEntryHolder.clear();
                    indexEntryHolder.putLong(indexEntry);
                    indexEntryHolder.flip();
                    channel.write(indexEntryHolder);
                }
            }

            indexFile = new KafkaCacheIndexFile.SortedByValue(tempFile.toPath(), long[]::new);
        }

        @Test
        public void shouldSortByKeyUnique() throws Exception
        {
            Path workingFile = new File(tempFolder.getRoot(), "working").toPath();
            Path sortedFile = new File(tempFolder.getRoot(), "sorted").toPath();

            indexFile.sortByKeyUnique(workingFile, sortedFile);

            ByteBuffer indexEntryHolder = allocate(SIZEOF_INDEX_RECORD).order(nativeOrder());
            try (FileChannel channel = FileChannel.open(sortedFile, READ))
            {
                assert channel.size() <= SIZEOF_INDEX_RECORD * entries;

                int previousIndexKey = -1;
                while (channel.position() < channel.size())
                {
                    indexEntryHolder.clear();
                    int read = channel.read(indexEntryHolder);
                    assert read == SIZEOF_INDEX_RECORD;
                    indexEntryHolder.flip();

                    long indexEntry = indexEntryHolder.getLong();
                    int indexKey = indexKey(indexEntry);

                    assert indexKey > previousIndexKey;
                    previousIndexKey = indexKey;
                }
            }
        }
    }
}
