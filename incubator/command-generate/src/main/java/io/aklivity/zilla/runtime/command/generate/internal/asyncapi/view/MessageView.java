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
package io.aklivity.zilla.runtime.command.generate.internal.asyncapi.view;

import java.util.Map;

import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Message;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Schema;

public final class MessageView extends Resolvable<Message>
{
    private final Message message;
    private final String refKey;

    private MessageView(
        Map<String, Message> messages,
        Message message)
    {
        super(messages, "#/components/messages/(\\w+)");
        this.message = message.ref == null ? message : resolveRef(message.ref);
        this.refKey = this.key;
    }

    public String refKey()
    {
        return refKey;
    }

    public Schema headers()
    {
        return message.headers;
    }

    public String contentType()
    {
        return message.contentType;
    }

    public static MessageView of(
        Map<String, Message> messages,
        Message message)
    {
        return new MessageView(messages, message);
    }
}
