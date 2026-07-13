/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.asyncapi.view;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiReply;
import io.aklivity.zilla.runtime.common.asyncapi.model.resolver.AsyncapiResolver;

public class AsyncapiReplyView
{
    public String address;
    public AsyncapiChannelView channel;

    private final Map<String, Object> extensions;

    public boolean hasExtension(
        String name)
    {
        return extensions != null && extensions.containsKey(name);
    }

    public <T> Optional<T> extension(
        String name,
        Class<T> type)
    {
        return Optional.ofNullable(extensions != null ? type.cast(extensions.get(name)) : null);
    }

    AsyncapiReplyView(
        AsyncapiResolver resolver,
        List<AsyncapiServerView> specServers,
        AsyncapiReply model)
    {
        this.address = model.address != null
                ? model.address.location
                : null;
        this.channel = model.channel != null
            ? new AsyncapiChannelView(resolver, specServers, model.channel)
            : null;
        this.extensions = model.extensions;
    }
}
