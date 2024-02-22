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

import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpDnsFailedFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpEventFW;

public class StdoutTcpHandler extends EventHandler
{
    private static final String DNS_FAILED_FORMAT = "DNS_FAILED %s - [%s] %s%n";

    private final TcpEventFW tcpEventRO = new TcpEventFW();

    public StdoutTcpHandler(
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
        final TcpEventFW event = tcpEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case DNS_FAILED:
            TcpDnsFailedFW e = event.dnsFailed();
            String qname = supplyQName.apply(e.namespacedId());
            out.printf(DNS_FAILED_FORMAT, qname, asDateTime(e.timestamp()), asString(e.address()));
            break;
        }
    }
}
