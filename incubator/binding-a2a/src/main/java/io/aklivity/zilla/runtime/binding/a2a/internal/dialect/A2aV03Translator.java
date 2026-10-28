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

import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_EXTENDED_AGENT_CARD;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_SEND;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_STREAM;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_CREATE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_DELETE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_CANCEL;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_SUBSCRIBE;

import jakarta.json.stream.JsonParser.Event;

import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aAgentCard;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

final class A2aV03Translator extends A2aJsonRewriter
{
    private final A2aCardTranslator cards = new A2aCardTranslator();

    int request(
        int kind,
        DirectBufferEx input,
        int inputOffset,
        int inputLimit,
        MutableDirectBufferEx output,
        int outputOffset)
    {
        begin(input, inputOffset, inputLimit, output, outputOffset);

        if (kind == KIND_PUSH_CONFIG_CREATE)
        {
            final String configId = stringOfTopLevel("configId");
            rewind();
            pushConfigCreate(configId);
        }
        else
        {
            message();
        }

        return end();
    }

    int result(
        int kind,
        A2aAgentCard card,
        DirectBufferEx input,
        int inputOffset,
        int inputLimit,
        MutableDirectBufferEx output,
        int outputOffset)
    {
        int limit;

        if (kind == KIND_EXTENDED_AGENT_CARD)
        {
            limit = cards.translate(card, input, inputOffset, inputLimit, output, outputOffset);
        }
        else
        {
            begin(input, inputOffset, inputLimit, output, outputOffset);

            final String resultKind = kind == KIND_MESSAGE_SEND || kind == KIND_MESSAGE_STREAM || kind == KIND_TASKS_SUBSCRIBE
                ? stringOfTopLevel("kind")
                : null;
            rewind();

            switch (kind)
            {
            case KIND_MESSAGE_SEND:
            case KIND_MESSAGE_STREAM:
            case KIND_TASKS_SUBSCRIBE:
                wrapped(resultKind);
                break;
            case KIND_TASKS_GET:
            case KIND_TASKS_CANCEL:
                canonical(parser.next(), true);
                break;
            case KIND_PUSH_CONFIG_CREATE:
            case KIND_PUSH_CONFIG_GET:
                pushConfig(parser.next());
                break;
            case KIND_PUSH_CONFIG_LIST:
                pushConfigs();
                break;
            case KIND_PUSH_CONFIG_DELETE:
            default:
                copyValue(parser.next());
                break;
            }

            limit = end();
        }

        return limit;
    }

    private void message()
    {
        parser.next();
        generator.writeStartObject();
        for (Event event = parser.next(); event != Event.END_OBJECT; event = parser.next())
        {
            final String key = parser.getString();
            generator.writeKey(key);
            final Event value = parser.next();
            if ("message".equals(key))
            {
                requestMessage();
            }
            else
            {
                copyValue(value);
            }
        }
        generator.writeEnd();
    }

    private void requestMessage()
    {
        generator.writeStartObject();
        generator.write("kind", "message");
        for (Event event = parser.next(); event != Event.END_OBJECT; event = parser.next())
        {
            final String key = parser.getString();
            generator.writeKey(key);
            final Event value = parser.next();
            switch (key)
            {
            case "role":
                generator.write(roleToV03(parser.getString()));
                break;
            case "parts":
                requestParts();
                break;
            default:
                copyValue(value);
                break;
            }
        }
        generator.writeEnd();
    }

    private void requestParts()
    {
        generator.writeStartArray();
        for (Event event = parser.next(); event != Event.END_ARRAY; event = parser.next())
        {
            if (event == Event.START_OBJECT)
            {
                requestPart();
            }
            else
            {
                copyValue(event);
            }
        }
        generator.writeEnd();
    }

    private void requestPart()
    {
        generator.writeStartObject();

        String uri = null;
        String bytes = null;
        String mimeType = null;
        String name = null;

        Event event = parser.next();
        if (event != Event.END_OBJECT)
        {
            final String first = parser.getString();
            generator.write("kind", "url".equals(first) || "raw".equals(first) ? "file" : "data".equals(first) ? "data" : "text");
        }

        for (; event != Event.END_OBJECT; event = parser.next())
        {
            final String key = parser.getString();
            final Event value = parser.next();
            switch (key)
            {
            case "url":
                uri = parser.getString();
                break;
            case "raw":
                bytes = parser.getString();
                break;
            case "mediaType":
                mimeType = parser.getString();
                break;
            case "filename":
                name = parser.getString();
                break;
            default:
                generator.writeKey(key);
                copyValue(value);
                break;
            }
        }

        if (uri != null || bytes != null)
        {
            generator.writeKey("file");
            generator.writeStartObject();
            if (uri != null)
            {
                generator.write("uri", uri);
            }
            if (bytes != null)
            {
                generator.write("bytes", bytes);
            }
            if (mimeType != null)
            {
                generator.write("mimeType", mimeType);
            }
            if (name != null)
            {
                generator.write("name", name);
            }
            generator.writeEnd();
        }
        generator.writeEnd();
    }

    private void pushConfigCreate(
        String configId)
    {
        parser.next();
        generator.writeStartObject();
        for (Event event = parser.next(); event != Event.END_OBJECT; event = parser.next())
        {
            final String key = parser.getString();
            final Event value = parser.next();
            switch (key)
            {
            case "configId":
                break;
            case "config":
                generator.writeKey("pushNotificationConfig");
                generator.writeStartObject();
                if (configId != null)
                {
                    generator.write("id", configId);
                }
                for (Event e = parser.next(); e != Event.END_OBJECT; e = parser.next())
                {
                    final String member = parser.getString();
                    final Event memberValue = parser.next();
                    generator.writeKey(member);
                    if ("authentication".equals(member))
                    {
                        requestAuthentication();
                    }
                    else
                    {
                        copyValue(memberValue);
                    }
                }
                generator.writeEnd();
                break;
            default:
                generator.writeKey(key);
                copyValue(value);
                break;
            }
        }
        generator.writeEnd();
    }

    private void requestAuthentication()
    {
        generator.writeStartObject();
        for (Event event = parser.next(); event != Event.END_OBJECT; event = parser.next())
        {
            final String key = parser.getString();
            final Event value = parser.next();
            if ("scheme".equals(key))
            {
                generator.writeKey("schemes");
                generator.writeStartArray();
                generator.write(parser.getString());
                generator.writeEnd();
            }
            else
            {
                generator.writeKey(key);
                copyValue(value);
            }
        }
        generator.writeEnd();
    }

    private void wrapped(
        String kind)
    {
        final String wrapper = kind == null ? "message" : switch (kind)
        {
        case "status-update" -> "statusUpdate";
        case "artifact-update" -> "artifactUpdate";
        default -> kind;
        };

        generator.writeStartObject();
        generator.writeKey(wrapper);
        canonical(parser.next(), "status-update".equals(kind));
        generator.writeEnd();
    }

    private void canonical(
        Event event,
        boolean dropFinal)
    {
        if (event == Event.START_OBJECT)
        {
            generator.writeStartObject();
            for (Event e = parser.next(); e != Event.END_OBJECT; e = parser.next())
            {
                final String key = parser.getString();
                final Event value = parser.next();
                switch (key)
                {
                case "kind":
                    break;
                case "final":
                    if (!dropFinal)
                    {
                        generator.writeKey(key);
                        copyValue(value);
                    }
                    break;
                case "data":
                case "metadata":
                    generator.writeKey(key);
                    copyValue(value);
                    break;
                case "role":
                    generator.writeKey(key);
                    generator.write(roleFromV03(parser.getString()));
                    break;
                case "state":
                    generator.writeKey(key);
                    generator.write(stateFromV03(parser.getString()));
                    break;
                default:
                    generator.writeKey(key);
                    canonical(value, false);
                    break;
                }
            }
            generator.writeEnd();
        }
        else if (event == Event.START_ARRAY)
        {
            generator.writeStartArray();
            for (Event e = parser.next(); e != Event.END_ARRAY; e = parser.next())
            {
                canonical(e, false);
            }
            generator.writeEnd();
        }
        else
        {
            copyValue(event);
        }
    }

    private void pushConfigs()
    {
        parser.next();
        generator.writeStartObject();
        generator.writeKey("configs");
        generator.writeStartArray();
        for (Event event = parser.next(); event != Event.END_ARRAY; event = parser.next())
        {
            pushConfig(event);
        }
        generator.writeEnd();
        generator.writeEnd();
    }

    private void pushConfig(
        Event first)
    {
        String taskId = null;
        boolean configured = false;

        generator.writeStartObject();
        for (Event event = parser.next(); event != Event.END_OBJECT; event = parser.next())
        {
            final String key = parser.getString();
            final Event value = parser.next();
            switch (key)
            {
            case "taskId":
                if (configured)
                {
                    generator.write(key, parser.getString());
                }
                else
                {
                    taskId = parser.getString();
                }
                break;
            case "pushNotificationConfig":
                configured = true;
                for (Event e = parser.next(); e != Event.END_OBJECT; e = parser.next())
                {
                    final String member = parser.getString();
                    final Event memberValue = parser.next();
                    if ("id".equals(member))
                    {
                        generator.write("id", parser.getString());
                    }
                    else
                    {
                        if (taskId != null)
                        {
                            generator.write("taskId", taskId);
                            taskId = null;
                        }

                        generator.writeKey(member);
                        if ("authentication".equals(member))
                        {
                            responseAuthentication();
                        }
                        else
                        {
                            copyValue(memberValue);
                        }
                    }
                }
                break;
            default:
                generator.writeKey(key);
                copyValue(value);
                break;
            }
        }

        if (taskId != null)
        {
            generator.write("taskId", taskId);
        }
        generator.writeEnd();
    }

    private void responseAuthentication()
    {
        generator.writeStartObject();
        for (Event event = parser.next(); event != Event.END_OBJECT; event = parser.next())
        {
            final String key = parser.getString();
            final Event value = parser.next();
            if ("schemes".equals(key))
            {
                generator.writeKey("scheme");
                Event scheme = parser.next();
                generator.write(parser.getString());
                for (scheme = parser.next(); scheme != Event.END_ARRAY; scheme = parser.next())
                {
                    skipValue(scheme);
                }
            }
            else
            {
                generator.writeKey(key);
                copyValue(value);
            }
        }
        generator.writeEnd();
    }

    private static String roleToV03(
        String role)
    {
        return "ROLE_USER".equals(role) ? "user" : "ROLE_AGENT".equals(role) ? "agent" : role;
    }

    private static String roleFromV03(
        String role)
    {
        return "user".equals(role) ? "ROLE_USER" : "agent".equals(role) ? "ROLE_AGENT" : role;
    }

    private static String stateFromV03(
        String state)
    {
        return "TASK_STATE_" + state.replace('-', '_').toUpperCase();
    }
}
