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
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpEventExFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpRequestAcceptedExFW;

public class StdoutHttpHandler extends EventHandler
{
    private static final String REQUEST_ACCEPTED_FORMAT = "%s %s [%s] REQUEST_ACCEPTED %s %s %s %s%n";

    private final EventFW eventRO = new EventFW();
    private final HttpEventExFW httpEventExRO = new HttpEventExFW();

    public StdoutHttpHandler(
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
        final HttpEventExFW extension = httpEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        switch (extension.kind())
        {
        case REQUEST_ACCEPTED:
        {
            HttpRequestAcceptedExFW ex = extension.requestAccepted();
            String qname = context.supplyQName(event.namespacedId());
            out.format(REQUEST_ACCEPTED_FORMAT, qname, identity(ex.identity()), asDateTime(event.timestamp()),
                asString(ex.scheme()), asString(ex.method()), asString(ex.authority()), asString(ex.path()));
            break;
        }
        }
    }
}
