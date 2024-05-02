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

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.tls.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventExFW;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public final class TlsEventFormatter implements EventFormatterSpi
{
    private static final String TLS_FAILED_FORMAT = "TLS_FAILED";
    private static final String PROTOCOL_REJECTED_FORMAT = "PROTOCOL_REJECTED";
    private static final String KEY_REJECTED_FORMAT = "KEY_REJECTED";
    private static final String PEER_NOT_VERIFIED_FORMAT = "PEER_NOT_VERIFIED";
    private static final String HANDSHAKE_FAILED_FORMAT = "HANDSHAKE_FAILED";

    private final EventFW eventRO = new EventFW();
    private final TlsEventExFW tlsEventExRO = new TlsEventExFW();

    TlsEventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final TlsEventExFW extension = tlsEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        String result = null;
        switch (extension.kind())
        {
        case TLS_FAILED:
        {
            result = TLS_FAILED_FORMAT;
            break;
        }
        case TLS_PROTOCOL_REJECTED:
        {
            result = PROTOCOL_REJECTED_FORMAT;
            break;
        }
        case TLS_KEY_REJECTED:
        {
            result = KEY_REJECTED_FORMAT;
            break;
        }
        case TLS_PEER_NOT_VERIFIED:
        {
            result = PEER_NOT_VERIFIED_FORMAT;
            break;
        }
        case TLS_HANDSHAKE_FAILED:
        {
            result = HANDSHAKE_FAILED_FORMAT;
            break;
        }
        }
        return result;
    }
}
