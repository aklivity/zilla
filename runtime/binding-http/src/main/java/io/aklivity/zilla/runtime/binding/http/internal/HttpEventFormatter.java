/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal;

import io.aklivity.zilla.runtime.binding.http.internal.types.StringFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpCircuitExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpEventExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpRequestAcceptedExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public final class HttpEventFormatter implements EventFormatterSpi
{
    private final EventFW eventRO = new EventFW();
    private final HttpEventExFW httpEventExRO = new HttpEventExFW();

    HttpEventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBufferEx buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final HttpEventExFW extension = httpEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        String result = null;
        switch (extension.kind())
        {
        case REQUEST_ACCEPTED:
        {
            HttpRequestAcceptedExFW ex = extension.requestAccepted();
            result = String.format("%s %s %s://%s%s", identity(ex.identity()), asString(ex.method()), asString(ex.scheme()),
                asString(ex.authority()), asString(ex.path()));
            break;
        }
        case CIRCUIT_OPENED:
        {
            HttpCircuitExFW ex = extension.circuitOpened();
            result = switch (ex.cause().get())
            {
            case RETRY_AFTER -> String.format("Circuit %d opened for %d ms by Retry-After.", ex.circuitId(), ex.remaining());
            case PROBE_FAILED -> String.format("Circuit %d reopened for %d ms after the probe failed.",
                ex.circuitId(), ex.remaining());
            default -> String.format("Circuit %d opened for %d ms after consecutive failures reached the threshold.",
                ex.circuitId(), ex.remaining());
            };
            break;
        }
        case CIRCUIT_HALF_OPENED:
            result = String.format("Circuit %d half-opened to admit a probe after the cooldown.",
                extension.circuitHalfOpened().circuitId());
            break;
        case CIRCUIT_CLOSED:
        {
            HttpCircuitExFW ex = extension.circuitClosed();
            result = switch (ex.cause().get())
            {
            case RETRY_AFTER_ELAPSED -> String.format("Circuit %d closed after Retry-After elapsed.", ex.circuitId());
            case EVICTED -> String.format("Circuit %d forgotten after eviction from the circuit table.", ex.circuitId());
            default -> String.format("Circuit %d closed after the probe succeeded.", ex.circuitId());
            };
            break;
        }
        case CIRCUIT_REJECTED:
        {
            HttpCircuitExFW ex = extension.circuitRejected();
            result = switch (ex.cause().get())
            {
            case RETRY_AFTER -> String.format("Circuit %d rejected a stream until Retry-After elapses.", ex.circuitId());
            case PROBE_IN_FLIGHT -> String.format("Circuit %d rejected a stream while the probe is in flight.", ex.circuitId());
            default -> String.format("Circuit %d rejected a stream while open.", ex.circuitId());
            };
            break;
        }
        }
        return result;
    }

    private static String asString(
        StringFW stringFW)
    {
        String s = stringFW.asString();
        return s == null ? "" : s;
    }

    private static String identity(
        StringFW identity)
    {
        int length = identity.length();
        return length <= 0 ? "-" : identity.asString();
    }
}
