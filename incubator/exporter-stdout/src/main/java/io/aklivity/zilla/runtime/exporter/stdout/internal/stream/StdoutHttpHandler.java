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
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpAuthorizationFailedEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpRequestAcceptedEventFW;

public class StdoutHttpHandler extends EventHandler
{
    private static final String AUTHORIZATION_FAILED_FORMAT = "AUTHORIZATION_FAILED %s %s [%s]%n";
    private static final String REQUEST_ACCEPTED_FORMAT = "REQUEST_ACCEPTED %s %s [%s] %s %s %s%n";

    private final HttpEventFW httpEventRO = new HttpEventFW();

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
        final HttpEventFW event = httpEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case AUTHORIZATION_FAILED:
        {
            HttpAuthorizationFailedEventFW e = event.authorizationFailed();
            String qname = context.supplyQName(e.namespacedId());
            out.printf(AUTHORIZATION_FAILED_FORMAT, qname, identity(e.identity()), asDateTime(e.timestamp()));
            break;
        }
        case REQUEST_ACCEPTED:
        {
            HttpRequestAcceptedEventFW e = event.requestAccepted();
            String qname = context.supplyQName(e.namespacedId());
            out.format(REQUEST_ACCEPTED_FORMAT, qname, identity(e.identity()), asDateTime(e.timestamp()), asString(e.scheme()),
                asString(e.method()), asString(e.path()));
            break;
        }
        }
    }
}
