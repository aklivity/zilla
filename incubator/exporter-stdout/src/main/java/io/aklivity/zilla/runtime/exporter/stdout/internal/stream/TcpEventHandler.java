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

import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpDnsResolutionFailedEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpEventFW;

public class TcpEventHandler extends EventHandler
{
    private static final String TCP_DNS_RESOLUTION_FAILED_FORMAT =
        "ERROR: TCP DNS Resolution Failed [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s] [address = %s]%n";

    private final TcpEventFW tcpEventRO = new TcpEventFW();

    public TcpEventHandler(
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
        final TcpEventFW event = tcpEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case DNS_RESOLUTION_FAILED:
            TcpDnsResolutionFailedEventFW e = event.dnsResolutionFailed();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(TCP_DNS_RESOLUTION_FAILED_FORMAT, e.timestamp(), e.traceId(), namespace, binding, asString(e.address()));
            break;
        }
    }
}
