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
package io.aklivity.zilla.runtime.binding.tls.internal;

import static io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventType.TLS_FAILED;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventType.TLS_HANDSHAKE_FAILED;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventType.TLS_KEY_REJECTED;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventType.TLS_PEER_NOT_VERIFIED;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventType.TLS_PROTOCOL_REJECTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.ByteBuffer;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.tls.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventExFW;

public class TlsEventFormatterTest
{
    private static final int EVENT_BUFFER_CAPACITY = 2048;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final TlsEventExFW.Builder tlsEventExRW = new TlsEventExFW.Builder();

    @Test
    public void shouldFormatTlsFailed()
    {
        // GIVEN
        TlsEventExFW extension = tlsEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .tlsFailed(e -> e
                .typeId(TLS_FAILED.value())
            )
            .build();
        eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .timestamp(0L)
            .traceId(0L)
            .namespacedId(0L)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        TlsEventFormatter formatter = new TlsEventFormatter();

        // WHEN
        String result = formatter.format(eventBuffer, 0, eventBuffer.capacity());

        // THEN
        assertThat(result, equalTo("TLS_FAILED"));
    }

    @Test
    public void shouldFormatProtocolRejected()
    {
        // GIVEN
        TlsEventExFW extension = tlsEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .tlsProtocolRejected(e -> e
                .typeId(TLS_PROTOCOL_REJECTED.value())
            )
            .build();
        eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .timestamp(0L)
            .traceId(0L)
            .namespacedId(0L)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        TlsEventFormatter formatter = new TlsEventFormatter();

        // WHEN
        String result = formatter.format(eventBuffer, 0, eventBuffer.capacity());

        // THEN
        assertThat(result, equalTo("PROTOCOL_REJECTED"));
    }

    @Test
    public void shouldFormatKeyRejected()
    {
        // GIVEN
        TlsEventExFW extension = tlsEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .tlsKeyRejected(e -> e
                .typeId(TLS_KEY_REJECTED.value())
            )
            .build();
        eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .timestamp(0L)
            .traceId(0L)
            .namespacedId(0L)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        TlsEventFormatter formatter = new TlsEventFormatter();

        // WHEN
        String result = formatter.format(eventBuffer, 0, eventBuffer.capacity());

        // THEN
        assertThat(result, equalTo("KEY_REJECTED"));
    }

    @Test
    public void shouldFormatPeerNotVerified()
    {
        // GIVEN
        TlsEventExFW extension = tlsEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .tlsPeerNotVerified(e -> e
                .typeId(TLS_PEER_NOT_VERIFIED.value())
            )
            .build();
        eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .timestamp(0L)
            .traceId(0L)
            .namespacedId(0L)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        TlsEventFormatter formatter = new TlsEventFormatter();

        // WHEN
        String result = formatter.format(eventBuffer, 0, eventBuffer.capacity());

        // THEN
        assertThat(result, equalTo("PEER_NOT_VERIFIED"));
    }

    @Test
    public void shouldFormatHandshakeFailed()
    {
        // GIVEN
        TlsEventExFW extension = tlsEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .tlsHandshakeFailed(e -> e
                .typeId(TLS_HANDSHAKE_FAILED.value())
            )
            .build();
        eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .timestamp(0L)
            .traceId(0L)
            .namespacedId(0L)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        TlsEventFormatter formatter = new TlsEventFormatter();

        // WHEN
        String result = formatter.format(eventBuffer, 0, eventBuffer.capacity());

        // THEN
        assertThat(result, equalTo("HANDSHAKE_FAILED"));
    }
}
