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

import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Channel;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Message;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Parameter;

public final class ChannelView extends Resolvable<Channel>
{
    private final Channel channel;

    private ChannelView(
        Map<String, Channel> channels,
        Channel channel)
    {
        super(channels, "#/channels/(\\w+)");
        this.channel = channel.ref == null ? channel : resolveRef(channel.ref);
    }

    public String address()
    {
        return channel.address;
    }

    public Map<String, Message> messages()
    {
        return channel.messages;
    }

    public Map<String, Parameter> parameters()
    {
        return channel.parameters;
    }

    public static ChannelView of(
        Map<String, Channel> channels,
        Channel channel)
    {
        return new ChannelView(channels, channel);
    }
}
