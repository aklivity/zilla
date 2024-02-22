/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.stdout.internal.stream;

import java.io.PrintStream;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TlsEventFW;

public class TlsEventHandler extends EventHandler
{
    private static final String TLS_FAILED_FORMAT = "TLS_FAILED %s - [%s]%n";
    private static final String PROTOCOL_REJECTED_FORMAT = "PROTOCOL_REJECTED %s - [%s]%n";
    private static final String KEY_REJECTED_FORMAT = "KEY_REJECTED %s - [%s]%n";
    private static final String PEER_NOT_VERIFIED_FORMAT = "PEER_NOT_VERIFIED %s - [%s]%n";
    private static final String HANDSHAKE_FAILED_FORMAT = "HANDSHAKE_FAILED %s - [%s]%n";

    private final TlsEventFW tlsEventRO = new TlsEventFW();

    public TlsEventHandler(
        LongFunction<String> supplyQName,
        PrintStream out)
    {
        super(supplyQName, out);
    }

    public void handleEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        TlsEventFW event = tlsEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case TLS_FAILED:
        {
            EventFW e = event.tlsFailed();
            String qname = supplyQName.apply(e.namespacedId());
            out.printf(TLS_FAILED_FORMAT, qname, asDateTime(e.timestamp()));
            break;
        }
        case TLS_PROTOCOL_REJECTED:
        {
            EventFW e = event.tlsProtocolRejected();
            String qname = supplyQName.apply(e.namespacedId());
            out.printf(PROTOCOL_REJECTED_FORMAT, qname, asDateTime(e.timestamp()));
            break;
        }
        case TLS_KEY_REJECTED:
        {
            EventFW e = event.tlsKeyRejected();
            String qname = supplyQName.apply(e.namespacedId());
            out.printf(KEY_REJECTED_FORMAT, qname, asDateTime(e.timestamp()));
            break;
        }
        case TLS_PEER_NOT_VERIFIED:
        {
            EventFW e = event.tlsPeerNotVerified();
            String qname = supplyQName.apply(e.namespacedId());
            out.printf(PEER_NOT_VERIFIED_FORMAT, qname, asDateTime(e.timestamp()));
            break;
        }
        case TLS_HANDSHAKE_FAILED:
        {
            EventFW e = event.tlsHandshakeFailed();
            String qname = supplyQName.apply(e.namespacedId());
            out.printf(HANDSHAKE_FAILED_FORMAT, qname, asDateTime(e.timestamp()));
            break;
        }
        }
    }
}
