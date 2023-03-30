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

/*import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.LongSupplier;

import org.agrona.BitUtil;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;*/

public class CountersLayoutTest
{
    /*private static final int FIELD_SIZE = BitUtil.SIZE_OF_LONG;
    private static final int RECORD_SIZE = 3 * FIELD_SIZE;
    private static final int BINDING_ID_OFFSET = 0;
    private static final int METRIC_ID_OFFSET = 1 * FIELD_SIZE;
    private static final int VALUE_OFFSET = 2 * FIELD_SIZE;*/

    // TODO: Ati
    /*@Test
    public void shouldReadLayoutFile() throws IOException
    {
        // GIVEN
        Path path = Paths.get("target/zilla-itests/counters0");
        createEmptyFile(path.toFile(), 128);
        final MappedByteBuffer mappedBuffer = mapExistingFile(path.toFile(), "counters");
        final AtomicBuffer atomicBuffer = new UnsafeBuffer(mappedBuffer);
        atomicBuffer.putLong(0 * RECORD_SIZE + BINDING_ID_OFFSET, 9L);
        atomicBuffer.putLong(0 * RECORD_SIZE + METRIC_ID_OFFSET, 10L);
        atomicBuffer.putLong(0 * RECORD_SIZE + VALUE_OFFSET, 42L);
        atomicBuffer.putLong(1 * RECORD_SIZE + BINDING_ID_OFFSET, 9L);
        atomicBuffer.putLong(1 * RECORD_SIZE + METRIC_ID_OFFSET, 11L);
        atomicBuffer.putLong(1 * RECORD_SIZE + VALUE_OFFSET, 77L);
        atomicBuffer.putLong(2 * RECORD_SIZE + BINDING_ID_OFFSET, 9L);
        atomicBuffer.putLong(2 * RECORD_SIZE + METRIC_ID_OFFSET, 12L);
        atomicBuffer.putLong(2 * RECORD_SIZE + VALUE_OFFSET, 88L);
        CountersLayout layout = new CountersLayout.Builder().path(path).build();
        CountersReader reader = new CountersReader(layout);

        // WHEN
        LongSupplier[][] recordReaders = reader.recordReaders();

        // THEN
        assertThat(recordReaders[0][0].getAsLong(), equalTo(9L));
        assertThat(recordReaders[0][1].getAsLong(), equalTo(10L));
        assertThat(recordReaders[0][2].getAsLong(), equalTo(42L));
        assertThat(recordReaders[1][0].getAsLong(), equalTo(9L));
        assertThat(recordReaders[1][1].getAsLong(), equalTo(11L));
        assertThat(recordReaders[1][2].getAsLong(), equalTo(77L));
        assertThat(recordReaders[2][0].getAsLong(), equalTo(9L));
        assertThat(recordReaders[2][1].getAsLong(), equalTo(12L));
        assertThat(recordReaders[2][2].getAsLong(), equalTo(88L));
        assertTrue(Files.exists(path));
        reader.close();
        Files.delete(path);
    }*/
}
