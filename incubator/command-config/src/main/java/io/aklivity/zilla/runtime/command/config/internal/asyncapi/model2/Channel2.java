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
package io.aklivity.zilla.runtime.command.config.internal.asyncapi.model2;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.command.config.internal.asyncapi.model.Channel;

public final class Channel2
{
    private final Map<String, Channel> channels;
    private final Channel channel;

    private Channel2(
        Map<String, Channel> channels,
        Channel channel)
    {
        this.channels = channels;
        this.channel = channel;
    }

    public String address()
    {
        if (channel.address == null)
        {
            Pattern regexPattern = Pattern.compile("#/channels/(\\w+)");
            Matcher matcher = regexPattern.matcher(channel.ref);
            if (matcher.matches())
            {
                String name = matcher.group(1);
                Channel refChannel = channels.get(name);
                if (refChannel != null)
                {
                    channel.address = refChannel.address;
                }
            }
        }
        return channel.address;
    }

    public static Channel2 of(
        Map<String, Channel> channels,
        Channel channel)
    {
        return new Channel2(channels, channel);
    }
}
