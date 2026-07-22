/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.command.logs.internal.printer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.command.logs.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LogsReaderTest
{
    @Test
    public void shouldReadDecodedRecords() throws Exception
    {
        // GIVEN
        UnsafeBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocate(256));
        EventFW event = new EventFW.Builder()
            .wrap(buffer, 0, buffer.capacity())
            .id(42)
            .timestamp(1000L)
            .traceId(7L)
            .namespacedId(99L)
            .build();

        LogsReader reader = new LogsReader(
            (handler, limit) ->
            {
                handler.accept(1, buffer, event.offset(), event.sizeof());
                return 1;
            },
            (msgTypeId, buf, index, length) -> "test message",
            namespacedId -> "namespace:binding",
            id -> "test.event");

        // WHEN
        List<LogRecord> records = reader.read(10);

        // THEN
        assertThat(records, hasSize(1));
        LogRecord record = records.get(0);
        assertThat(record.timestamp(), equalTo(1000L));
        assertThat(record.traceId(), equalTo(7L));
        assertThat(record.qualifiedName(), equalTo("namespace:binding"));
        assertThat(record.eventName(), equalTo("test.event"));
        assertThat(record.message(), equalTo("test message"));
    }

    @Test
    public void shouldReadNoRecordsWhenEmpty() throws Exception
    {
        // GIVEN
        LogsReader reader = new LogsReader(
            (handler, limit) -> 0,
            (msgTypeId, buf, index, length) -> "unused",
            namespacedId -> "unused",
            id -> "unused");

        // WHEN
        List<LogRecord> records = reader.read(10);

        // THEN
        assertThat(records, hasSize(0));
    }
}
