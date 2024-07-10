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
import io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsKeyVerificationFailedExFW;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public final class TlsEventFormatter implements EventFormatterSpi
{
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
            result = "There was a generic error detected by an SSL subsystem.";
            break;
        }
        case TLS_PROTOCOL_REJECTED:
        {
            result = "There was an error in the operation of the SSL protocol.";
            break;
        }
        case TLS_KEY_REJECTED:
        {
            result = "Bad SSL key due to misconfiguration of the server or client SSL certificate and private key.";
            break;
        }
        case TLS_PEER_NOT_VERIFIED:
        {
            result = "The peer's identity could not be verified.";
            break;
        }
        case TLS_HANDSHAKE_FAILED:
        {
            result = "The client and server could not negotiate the desired level of security.";
            break;
        }
        case TLS_KEY_VERIFICATION_FAILED:
        {
            TlsKeyVerificationFailedExFW ex = extension.tlsKeyVerificationFailed();
            result = switch (ex.failureType().get())
            {
            case TLS_KEY_MISSING -> String.format("Key (%s) is missing.", ex.keyName());
            case TLS_KEY_INVALID -> String.format("Key (%s) is invalid.", ex.keyName());
            };
            break;
        }
        }
        return result;
    }
}
