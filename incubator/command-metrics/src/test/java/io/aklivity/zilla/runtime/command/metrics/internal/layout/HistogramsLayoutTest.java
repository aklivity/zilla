/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.command.metrics.internal.layout;

import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.agrona.BitUtil;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class HistogramsLayoutTest
{
    private static final int FIELD_SIZE = BitUtil.SIZE_OF_LONG;
    private static final int BINDING_ID_OFFSET = 0;
    private static final int METRIC_ID_OFFSET = 1 * FIELD_SIZE;
    private static final int VALUES_OFFSET = 2 * FIELD_SIZE;
    private static final int BUCKETS = 10;
    private static final int ARRAY_SIZE = BUCKETS * FIELD_SIZE;
    private static final int RECORD_SIZE = 2 * FIELD_SIZE + ARRAY_SIZE;

    @Test
    public void shouldReadLayoutFile() throws IOException
    {
        // GIVEN
        Path path = Paths.get("target/zilla-itests/histograms0");
        createEmptyFile(path.toFile(), 256);
        final MappedByteBuffer mappedBuffer = mapExistingFile(path.toFile(), "histograms");
        final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
        atomicBuffer.putLong(0 * RECORD_SIZE + BINDING_ID_OFFSET, 9L);
        atomicBuffer.putLong(0 * RECORD_SIZE + METRIC_ID_OFFSET, 42L);
        atomicBuffer.putLong(0 * RECORD_SIZE + VALUES_OFFSET + 0 * FIELD_SIZE, 1L);
        atomicBuffer.putLong(0 * RECORD_SIZE + VALUES_OFFSET + 1 * FIELD_SIZE, 2L);
        atomicBuffer.putLong(0 * RECORD_SIZE + VALUES_OFFSET + 2 * FIELD_SIZE, 33L);
        atomicBuffer.putLong(0 * RECORD_SIZE + VALUES_OFFSET + 3 * FIELD_SIZE, 0L);
        atomicBuffer.putLong(0 * RECORD_SIZE + VALUES_OFFSET + 4 * FIELD_SIZE, 42L);
        atomicBuffer.putLong(0 * RECORD_SIZE + VALUES_OFFSET + 5 * FIELD_SIZE, 0L);
        atomicBuffer.putLong(0 * RECORD_SIZE + VALUES_OFFSET + 6 * FIELD_SIZE, 0L);
        atomicBuffer.putLong(0 * RECORD_SIZE + VALUES_OFFSET + 7 * FIELD_SIZE, 77L);
        atomicBuffer.putLong(0 * RECORD_SIZE + VALUES_OFFSET + 8 * FIELD_SIZE, 88L);
        atomicBuffer.putLong(0 * RECORD_SIZE + VALUES_OFFSET + 9 * FIELD_SIZE, 99L);

        atomicBuffer.putLong(1 * RECORD_SIZE + BINDING_ID_OFFSET, 9L);
        atomicBuffer.putLong(1 * RECORD_SIZE + METRIC_ID_OFFSET, 11L);
        atomicBuffer.putLong(1 * RECORD_SIZE + VALUES_OFFSET + 0 * FIELD_SIZE, 7L);
        atomicBuffer.putLong(1 * RECORD_SIZE + VALUES_OFFSET + 1 * FIELD_SIZE, 8L);
        atomicBuffer.putLong(1 * RECORD_SIZE + VALUES_OFFSET + 2 * FIELD_SIZE, 0L);
        atomicBuffer.putLong(1 * RECORD_SIZE + VALUES_OFFSET + 3 * FIELD_SIZE, 0L);
        atomicBuffer.putLong(1 * RECORD_SIZE + VALUES_OFFSET + 4 * FIELD_SIZE, 44L);
        atomicBuffer.putLong(1 * RECORD_SIZE + VALUES_OFFSET + 5 * FIELD_SIZE, 0L);
        atomicBuffer.putLong(1 * RECORD_SIZE + VALUES_OFFSET + 6 * FIELD_SIZE, 0L);
        atomicBuffer.putLong(1 * RECORD_SIZE + VALUES_OFFSET + 7 * FIELD_SIZE, 7L);
        atomicBuffer.putLong(1 * RECORD_SIZE + VALUES_OFFSET + 8 * FIELD_SIZE, 8L);
        atomicBuffer.putLong(1 * RECORD_SIZE + VALUES_OFFSET + 9 * FIELD_SIZE, 9L);
        HistogramsLayout layout = new HistogramsLayout.Builder().path(path).build();
        HistogramsReader reader = new HistogramsReader(layout);

        // WHEN
        long[][] records = reader.records();

        // THEN
        assertThat(records[0][0], equalTo(9L));
        assertThat(records[0][1], equalTo(42L));
        assertThat(records[0][2], equalTo(1L));
        assertThat(records[0][3], equalTo(2L));
        assertThat(records[0][4], equalTo(33L));
        assertThat(records[0][5], equalTo(0L));
        assertThat(records[0][6], equalTo(42L));
        assertThat(records[0][7], equalTo(0L));
        assertThat(records[0][8], equalTo(0L));
        assertThat(records[0][9], equalTo(77L));
        assertThat(records[0][10], equalTo(88L));
        assertThat(records[0][11], equalTo(99L));

        assertThat(records[1][0], equalTo(9L));
        assertThat(records[1][1], equalTo(11L));
        assertThat(records[1][2], equalTo(7L));
        assertThat(records[1][3], equalTo(8L));
        assertThat(records[1][4], equalTo(0L));
        assertThat(records[1][5], equalTo(0L));
        assertThat(records[1][6], equalTo(44L));
        assertThat(records[1][7], equalTo(0L));
        assertThat(records[1][8], equalTo(0L));
        assertThat(records[1][9], equalTo(7L));
        assertThat(records[1][10], equalTo(8L));
        assertThat(records[1][11], equalTo(9L));

        assertTrue(Files.exists(path));
        reader.close();
        Files.delete(path);
    }
}
