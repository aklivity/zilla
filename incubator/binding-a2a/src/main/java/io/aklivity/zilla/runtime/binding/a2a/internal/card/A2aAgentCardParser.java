/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.a2a.internal.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.json.stream.JsonParser.Event;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

public final class A2aAgentCardParser
{
    private static final String DEFAULT_LEGACY_VERSION = "0.3";
    private static final String DEFAULT_VERSION = "1.0";

    private final JsonParserEx parser = JsonEx.createParser(Map.of());

    public A2aAgentCard parse(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        parser.wrap(buffer, offset, limit);

        final List<A2aAgentInterface> supported = new ArrayList<>();
        final List<A2aAgentInterface> additional = new ArrayList<>();
        final List<String> skills = new ArrayList<>();

        String url = null;
        String transport = null;
        String version = null;
        String topKey = null;
        String itemKey = null;
        String itemUrl = null;
        String itemBinding = null;
        String itemVersion = null;
        String itemTenant = null;
        boolean legacy = true;
        int depth = 0;

        while (parser.hasNext())
        {
            final Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                if (depth == 3)
                {
                    itemKey = null;
                    itemUrl = null;
                    itemBinding = null;
                    itemVersion = null;
                    itemTenant = null;
                }
                break;
            case END_OBJECT:
            case END_ARRAY:
                if (depth == 3 && event == Event.END_OBJECT)
                {
                    if ("supportedInterfaces".equals(topKey))
                    {
                        supported.add(new A2aAgentInterface(itemUrl, itemBinding, itemVersion, itemTenant));
                    }
                    else if ("additionalInterfaces".equals(topKey))
                    {
                        additional.add(new A2aAgentInterface(itemUrl, itemBinding, itemVersion, itemTenant));
                    }
                }
                depth--;
                break;
            case KEY_NAME:
                if (depth == 1)
                {
                    topKey = parser.getString();
                    legacy &= !"supportedInterfaces".equals(topKey);
                }
                else if (depth == 3)
                {
                    itemKey = parser.getString();
                }
                break;
            case VALUE_STRING:
                final String value = parser.getString();
                if (depth == 1)
                {
                    switch (topKey)
                    {
                    case "url":
                        url = value;
                        break;
                    case "preferredTransport":
                        transport = value;
                        break;
                    case "protocolVersion":
                        version = value;
                        break;
                    default:
                        break;
                    }
                }
                else if (depth == 3 && itemKey != null)
                {
                    switch (itemKey)
                    {
                    case "id":
                        if ("skills".equals(topKey))
                        {
                            skills.add(value);
                        }
                        break;
                    case "url":
                        itemUrl = value;
                        break;
                    case "protocolBinding":
                    case "transport":
                        itemBinding = value;
                        break;
                    case "protocolVersion":
                        itemVersion = value;
                        break;
                    case "tenant":
                        itemTenant = value;
                        break;
                    default:
                        break;
                    }
                }
                break;
            default:
                break;
            }
        }
        parser.reset();

        final List<A2aAgentInterface> interfaces = new ArrayList<>();
        if (legacy)
        {
            final String cardVersion = version != null ? version : DEFAULT_LEGACY_VERSION;
            if (url != null)
            {
                interfaces.add(new A2aAgentInterface(url, transport != null ? transport : A2aAgentInterface.BINDING_JSONRPC,
                    cardVersion, null));
            }
            additional.forEach(a -> interfaces.add(new A2aAgentInterface(a.url, a.binding, cardVersion, null)));
        }
        else
        {
            supported.forEach(s -> interfaces.add(new A2aAgentInterface(s.url, s.binding,
                s.version != null ? s.version : DEFAULT_VERSION, s.tenant)));
        }

        return new A2aAgentCard(legacy, interfaces, skills);
    }
}
