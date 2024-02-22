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
    private static final String HTTP_AUTHORIZATION_FAILURE_FORMAT = "HTTP_AUTHORIZATION_FAILURE %s.%s %s %d%n";
    private static final String HTTP_REQUEST_FORMAT = "HTTP_REQUEST %s.%s %s %d%n";

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
        case AUTHORIZATION_FAILURE:
        {
            HttpDefaultEventFW e = event.authorizationFailure();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(HTTP_AUTHORIZATION_FAILURE_FORMAT, namespace, binding, identity(e.identity()), e.timestamp());
            break;
        }
        case REQUEST:
        {
            HttpDefaultEventFW e = event.request();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.format(HTTP_REQUEST_FORMAT, namespace, binding, identity(e.identity()), e.timestamp());
            break;
        }
        }
    }
}
