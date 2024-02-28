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
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpDnsFailedExFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpEventExFW;

public class StdoutTcpHandler extends EventHandler
{
    private static final String DNS_FAILED_FORMAT = "%s - [%s] DNS_FAILED %s%n";

    private final EventFW eventRO = new EventFW();
    private final TcpEventExFW tcpEventExRO = new TcpEventExFW();

    public StdoutTcpHandler(
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
        final TcpEventExFW extension = tcpEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        switch (extension.kind())
        {
        case DNS_FAILED:
            TcpDnsFailedExFW ex = extension.dnsFailed();
            String qname = context.supplyQName(event.namespacedId());
            out.printf(DNS_FAILED_FORMAT, qname, asDateTime(event.timestamp()), asString(ex.address()));
            break;
        }
    }
}
