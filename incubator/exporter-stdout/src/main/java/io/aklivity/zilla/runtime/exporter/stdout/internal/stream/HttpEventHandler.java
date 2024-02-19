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

import io.aklivity.zilla.runtime.exporter.stdout.internal.types.Array32FW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpAuthorizationEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpRequestEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpResponseEventFW;

public class HttpEventHandler extends EventHandler
{
    private static final String HTTP_AUTHORIZATION_FORMAT =
        "%s: HTTP Authorization %s [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s] [identity = %s]%n";
    private static final String HTTP_REQUEST_FORMAT =
        "INFO: HTTP Request [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s] %s%n";
    private static final String HTTP_RESPONSE_FORMAT =
        "INFO: HTTP Response [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s] %s%n";

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
        case AUTHORIZATION:
        {
            HttpAuthorizationEventFW e = event.authorization();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(HTTP_AUTHORIZATION_FORMAT, level(e.result()), result(e.result()), e.timestamp(), e.traceId(), namespace,
                binding, asString(e.identity()));
            break;
        }
        case REQUEST:
        {
            HttpRequestEventFW e = event.request();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.format(HTTP_REQUEST_FORMAT, e.timestamp(), e.traceId(), namespace, binding, headersAsString(e.headers()));
            break;
        }
        case RESPONSE:
        {
            HttpResponseEventFW e = event.response();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.format(HTTP_RESPONSE_FORMAT, e.timestamp(), e.traceId(), namespace, binding, headersAsString(e.headers()));
            break;
        }
        }
    }

    private static String headersAsString(
        Array32FW<HttpHeaderFW> headers)
    {
        StringBuilder sb = new StringBuilder();
        headers.forEach(h ->
        {
            sb.append("[");
            sb.append(h.name().asString());
            sb.append(" = ");
            sb.append(h.value().asString());
            sb.append("] ");
        });
        return sb.toString();
    }
}
