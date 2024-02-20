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

import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TlsEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TlsFailedFW;

public class TlsEventHandler extends EventHandler
{
    private static final String TLS_FAILED_FORMAT =
        "TLS Failed [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s] [error = %s]%n";

    private final TlsEventFW tlsEventRO = new TlsEventFW();

    public TlsEventHandler(
        LongFunction<String> supplyNamespace,
        LongFunction<String> supplyLocalName,
        PrintStream out)
    {
        super(supplyNamespace, supplyLocalName, out);
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
            TlsFailedFW e = event.tlsFailed();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(TLS_FAILED_FORMAT, e.timestamp(), e.traceId(), namespace, binding, e.error().get().name());
            break;
        }
    }
}
