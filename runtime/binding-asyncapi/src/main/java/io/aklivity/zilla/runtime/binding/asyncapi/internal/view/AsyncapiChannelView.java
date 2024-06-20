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

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiChannel;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiParameter;

public final class AsyncapiChannelView extends AsyncapiResolvable<AsyncapiChannel>
{
    private final AsyncapiChannel channel;

    public String address()
    {
        return channel.address;
    }

    public Map<String, AsyncapiMessage> messages()
    {
        return channel.messages;
    }

    public Map<String, AsyncapiParameter> parameters()
    {
        return channel.parameters;
    }

    public static AsyncapiChannelView of(
        Map<String, AsyncapiChannel> channels,
        AsyncapiChannel asyncapiChannel)
    {
        return new AsyncapiChannelView(channels, asyncapiChannel);
    }

    private AsyncapiChannelView(
        Map<String, AsyncapiChannel> channels,
        AsyncapiChannel channel)
    {
        super(channels, "#/channels/(.+)");
        this.channel = channel.ref == null ? channel : resolveRef(channel.ref);
    }
}
