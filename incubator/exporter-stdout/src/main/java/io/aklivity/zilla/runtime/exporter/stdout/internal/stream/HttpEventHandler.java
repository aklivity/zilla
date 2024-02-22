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

import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpDefaultEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpEventFW;

public class HttpEventHandler extends EventHandler
{
    private static final String HTTP_AUTHORIZATION_FAILED_FORMAT = "AUTHORIZATION_FAILED %s.%s %s [%s]%n";
    private static final String HTTP_REQUEST_FORMAT = "REQUEST_ACCEPTED %s.%s %s [%s]%n";

    private final HttpEventFW httpEventRO = new HttpEventFW();

    public HttpEventHandler(
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
        final HttpEventFW event = httpEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case AUTHORIZATION_FAILED:
        {
            HttpDefaultEventFW e = event.authorizationFailed();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(HTTP_AUTHORIZATION_FAILED_FORMAT, namespace, binding, identity(e.identity()), asDateTime(e.timestamp()));
            break;
        }
        case REQUEST_ACCEPTED:
        {
            HttpDefaultEventFW e = event.requestAccepted();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.format(HTTP_REQUEST_FORMAT, namespace, binding, identity(e.identity()), asDateTime(e.timestamp()));
            break;
        }
        }
    }
}
