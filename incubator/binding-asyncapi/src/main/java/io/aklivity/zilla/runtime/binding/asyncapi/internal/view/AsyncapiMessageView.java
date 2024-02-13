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

import java.util.Map;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchema;

public final class AsyncapiMessageView extends AsyncapiResolvable<AsyncapiMessage>
{
    private final AsyncapiMessage asyncapiMessage;

    public String refKey()
    {
        return key;
    }

    public AsyncapiSchema headers()
    {
        return asyncapiMessage.headers;
    }

    public String contentType()
    {
        return asyncapiMessage.contentType;
    }

    public static AsyncapiMessageView of(
        Map<String, AsyncapiMessage> messages,
        AsyncapiMessage asyncapiMessage)
    {
        return new AsyncapiMessageView(messages, asyncapiMessage);
    }

    private AsyncapiMessageView(
        Map<String, AsyncapiMessage> messages,
        AsyncapiMessage asyncapiMessage)
    {
        super(messages, "#/components/messages/(\\w+)");
        this.asyncapiMessage = asyncapiMessage.ref == null ? asyncapiMessage : resolveRef(asyncapiMessage.ref);
    }
}
