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

import java.nio.ByteBuffer;
import java.time.Clock;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLKeyException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLProtocolException;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsError;
import io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class TlsEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final TlsEventFW.Builder tlsEventRW = new TlsEventFW.Builder();
    private final MutableDirectBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final int tlsTypeId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public TlsEventContext(
        EngineContext context)
    {
        this.tlsTypeId = context.supplyTypeId(TlsBinding.NAME);
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void tlsFailed(
        TlsError error,
        long traceId)
    {
        TlsEventFW event = tlsEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .tlsFailed(e -> e
                .timestamp(clock.millis())
                .traceId(traceId)
                .error(r -> r.set(error))
            )
            .build();
        eventWriter.accept(tlsTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void tlsFailed(
        Exception ex,
        long traceId)
    {
        TlsError error;
        if (ex instanceof SSLProtocolException)
        {
            error = TlsError.PROTOCOL_ERROR;
        }
        else if (ex instanceof SSLKeyException)
        {
            error = TlsError.KEY_ERROR;
        }
        else if (ex instanceof SSLHandshakeException)
        {
            error = TlsError.HANDSHAKE_ERROR;
        }
        else if (ex instanceof SSLPeerUnverifiedException)
        {
            error = TlsError.PEER_UNVERIFIED_ERROR;
        }
        else
        {
            error = TlsError.UNSPECIFIED_ERROR;
        }
        tlsFailed(error, traceId);
    }
}
