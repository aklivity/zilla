/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tcp.internal;

import static io.aklivity.zilla.runtime.binding.tcp.internal.types.event.TcpEventType.DNS_FAILED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.ByteBuffer;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.tcp.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.event.TcpEventExFW;

public class TcpEventFormatterTest
{
    private static final int EVENT_BUFFER_CAPACITY = 2048;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final TcpEventExFW.Builder tcpEventExRW = new TcpEventExFW.Builder();

    @Test
    public void shouldFormatDnsFailed()
    {
        // GIVEN
        TcpEventExFW extension = tcpEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .dnsFailed(e -> e
                .typeId(DNS_FAILED.value())
                .address("address")
            )
            .build();
        eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .timestamp(0L)
            .traceId(0L)
            .namespacedId(0L)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        TcpEventFormatter formatter = new TcpEventFormatter();

        // WHEN
        String result = formatter.formatEventEx(0, eventBuffer, 0, eventBuffer.capacity());

        // THEN
        assertThat(result, equalTo("DNS_FAILED address"));
    }
}
