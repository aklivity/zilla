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

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.exporter.stdout.internal.StdoutExporterContext;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TlsEventExFW;

public class StdoutTlsHandler extends EventHandler
{
    private static final String TLS_FAILED_FORMAT = "%s - [%s] TLS_FAILED%n";
    private static final String PROTOCOL_REJECTED_FORMAT = "%s - [%s] PROTOCOL_REJECTED%n";
    private static final String KEY_REJECTED_FORMAT = "%s - [%s] KEY_REJECTED%n";
    private static final String PEER_NOT_VERIFIED_FORMAT = "%s - [%s] PEER_NOT_VERIFIED%n";
    private static final String HANDSHAKE_FAILED_FORMAT = "%s - [%s] HANDSHAKE_FAILED%n";

    private final EventFW eventRO = new EventFW();
    private final TlsEventExFW tlsEventExRO = new TlsEventExFW();

    public StdoutTlsHandler(
        StdoutExporterContext context,
        PrintStream out)
    {
        super(context, out);
    }

    public void handleEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final TlsEventExFW extension = tlsEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        switch (extension.kind())
        {
        case TLS_FAILED:
        {
            String qname = context.supplyQName(event.namespacedId());
            out.printf(TLS_FAILED_FORMAT, qname, asDateTime(event.timestamp()));
            break;
        }
        case TLS_PROTOCOL_REJECTED:
        {
            String qname = context.supplyQName(event.namespacedId());
            out.printf(PROTOCOL_REJECTED_FORMAT, qname, asDateTime(event.timestamp()));
            break;
        }
        case TLS_KEY_REJECTED:
        {
            String qname = context.supplyQName(event.namespacedId());
            out.printf(KEY_REJECTED_FORMAT, qname, asDateTime(event.timestamp()));
            break;
        }
        case TLS_PEER_NOT_VERIFIED:
        {
            String qname = context.supplyQName(event.namespacedId());
            out.printf(PEER_NOT_VERIFIED_FORMAT, qname, asDateTime(event.timestamp()));
            break;
        }
        case TLS_HANDSHAKE_FAILED:
        {
            String qname = context.supplyQName(event.namespacedId());
            out.printf(HANDSHAKE_FAILED_FORMAT, qname, asDateTime(event.timestamp()));
            break;
        }
        }
    }
}
