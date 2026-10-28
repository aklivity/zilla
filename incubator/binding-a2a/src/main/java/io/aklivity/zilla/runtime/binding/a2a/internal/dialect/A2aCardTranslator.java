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
package io.aklivity.zilla.runtime.binding.a2a.internal.dialect;

import java.util.List;
import java.util.function.Function;

import jakarta.json.stream.JsonParser.Event;

import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aAgentCard;
import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aAgentInterface;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

public final class A2aCardTranslator extends A2aJsonRewriter
{
    private static final String SUPPORTED_INTERFACES = "supportedInterfaces";
    private static final String SECURITY_REQUIREMENTS = "securityRequirements";
    private static final String SECURITY_SCHEME = "oauth2";

    private static final Function<String, List<String>> NO_ROLES = skill -> List.of();

    public int translate(
        A2aAgentCard card,
        DirectBufferEx input,
        int inputOffset,
        int inputLimit,
        MutableDirectBufferEx output,
        int outputOffset)
    {
        return translate(card, NO_ROLES, input, inputOffset, inputLimit, output, outputOffset);
    }

    public int translate(
        A2aAgentCard card,
        Function<String, List<String>> rolesOf,
        DirectBufferEx input,
        int inputOffset,
        int inputLimit,
        MutableDirectBufferEx output,
        int outputOffset)
    {
        begin(input, inputOffset, inputLimit, output, outputOffset);

        parser.next();
        generator.writeStartObject();

        boolean interfaces = false;
        for (Event event = parser.next(); event != Event.END_OBJECT; event = parser.next())
        {
            final String key = parser.getString();
            final Event value = parser.next();
            switch (key)
            {
            case "url":
            case "preferredTransport":
            case "protocolVersion":
            case "additionalInterfaces":
                skipValue(value);
                if (card.legacy && !interfaces)
                {
                    interfaces = true;
                    writeInterfaces(card.interfaces);
                }
                break;
            case "skills":
                generator.writeKey(key);
                skills(rolesOf);
                break;
            default:
                generator.writeKey(key);
                copyValue(value);
                break;
            }
        }
        generator.writeEnd();

        return end();
    }

    private void writeInterfaces(
        List<A2aAgentInterface> interfaces)
    {
        generator.writeKey(SUPPORTED_INTERFACES);
        generator.writeStartArray();
        for (A2aAgentInterface iface : interfaces)
        {
            generator.writeStartObject();
            generator.write("url", iface.url);
            generator.write("protocolBinding", iface.binding);
            generator.write("protocolVersion", iface.version);
            generator.writeEnd();
        }
        generator.writeEnd();
    }

    private void skills(
        Function<String, List<String>> rolesOf)
    {
        generator.writeStartArray();
        for (Event event = parser.next(); event != Event.END_ARRAY; event = parser.next())
        {
            if (event == Event.START_OBJECT)
            {
                skill(rolesOf);
            }
            else
            {
                copyValue(event);
            }
        }
        generator.writeEnd();
    }

    private void skill(
        Function<String, List<String>> rolesOf)
    {
        generator.writeStartObject();

        String id = null;
        boolean secured = false;
        for (Event event = parser.next(); event != Event.END_OBJECT; event = parser.next())
        {
            final String key = parser.getString();
            final Event value = parser.next();
            if ("id".equals(key) && value == Event.VALUE_STRING)
            {
                id = parser.getString();
                generator.writeKey(key);
                generator.write(id);
            }
            else if ("security".equals(key) || SECURITY_REQUIREMENTS.equals(key))
            {
                secured = true;
                generator.writeKey(SECURITY_REQUIREMENTS);
                requirements("security".equals(key), id != null ? rolesOf.apply(id) : List.of());
            }
            else
            {
                generator.writeKey(key);
                copyValue(value);
            }
        }

        final List<String> roles = id != null ? rolesOf.apply(id) : List.of();
        if (!secured && !roles.isEmpty())
        {
            generator.writeKey(SECURITY_REQUIREMENTS);
            generator.writeStartArray();
            writeRequirement(roles);
            generator.writeEnd();
        }

        generator.writeEnd();
    }

    private void requirements(
        boolean declared,
        List<String> roles)
    {
        generator.writeStartArray();
        for (Event event = parser.next(); event != Event.END_ARRAY; event = parser.next())
        {
            if (declared && event == Event.START_OBJECT)
            {
                generator.writeStartObject();
                generator.writeKey("schemes");
                generator.writeStartObject();
                for (Event e = parser.next(); e != Event.END_OBJECT; e = parser.next())
                {
                    generator.writeKey(parser.getString());
                    generator.writeStartObject();
                    generator.writeKey("list");
                    copyValue(parser.next());
                    generator.writeEnd();
                }
                generator.writeEnd();
                generator.writeEnd();
            }
            else
            {
                copyValue(event);
            }
        }

        if (!roles.isEmpty())
        {
            writeRequirement(roles);
        }
        generator.writeEnd();
    }

    private void writeRequirement(
        List<String> roles)
    {
        generator.writeStartObject();
        generator.writeKey("schemes");
        generator.writeStartObject();
        generator.writeKey(SECURITY_SCHEME);
        generator.writeStartObject();
        generator.writeKey("list");
        generator.writeStartArray();
        roles.forEach(generator::write);
        generator.writeEnd();
        generator.writeEnd();
        generator.writeEnd();
        generator.writeEnd();
    }
}
