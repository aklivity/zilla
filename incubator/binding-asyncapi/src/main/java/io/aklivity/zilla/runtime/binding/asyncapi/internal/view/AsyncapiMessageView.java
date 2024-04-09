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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.view;

import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchema;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiTrait;

public final class AsyncapiMessageView extends AsyncapiResolvable<AsyncapiMessage>
{
    private final AsyncapiMessage message;

    public String refKey()
    {
        return key;
    }

    public AsyncapiSchema headers()
    {
        return message.headers;
    }

    public String contentType()
    {
        return message.contentType;
    }

    public AsyncapiSchema payload()
    {
        return message.payload;
    }
    public List<AsyncapiTrait> traits()
    {
        return message.traits;
    }

    public static AsyncapiMessageView of(
        Map<String, AsyncapiMessage> messages,
        AsyncapiMessage asyncapiMessage)
    {
        return new AsyncapiMessageView(messages, asyncapiMessage);
    }

    private AsyncapiMessageView(
        Map<String, AsyncapiMessage> messages,
        AsyncapiMessage message)
    {
        super(messages, "#/components/messages/(\\w+)");
        this.message = message.ref == null ? message : resolveRef(message.ref);
    }
}
