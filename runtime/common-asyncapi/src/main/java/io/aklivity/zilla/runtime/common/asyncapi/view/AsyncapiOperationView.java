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

import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiOperation;
import io.aklivity.zilla.runtime.common.asyncapi.model.resolver.AsyncapiResolver;

public final class AsyncapiOperationView
{
    public final AsyncapiView specification;
    public final long compositeId;
    public final String name;

    public final AsyncapiChannelView channel;
    public final String action;
    public final AsyncapiReplyView reply;
    public final List<AsyncapiMessageView> messages;
    public final List<AsyncapiSecuritySchemeView> security;

    private final Map<String, Object> bindings;

    public boolean hasBinding(
        String name)
    {
        return bindings != null && bindings.containsKey(name);
    }

    public <T> Optional<T> binding(
        String name,
        Class<T> type)
    {
        return Optional.ofNullable(bindings != null ? type.cast(bindings.get(name)) : null);
    }

    public boolean hasMessagesOrParameters()
    {
        return channel != null &&
            (channel.hasMessages() || channel.hasParameters());
    }

    AsyncapiOperationView(
        AsyncapiView specification,
        long compositeId,
        AsyncapiResolver resolver,
        String name,
        AsyncapiOperation model)
    {
        this.specification = specification;
        this.compositeId = compositeId;
        this.name = name;

        final AsyncapiOperation resolved = resolver.operations.resolve(model);
        this.bindings = resolved.bindings;
        this.channel = resolved.channel != null
                ? new AsyncapiChannelView(resolver, resolved.channel)
                : null;
        this.action = resolved.action;
        this.reply = resolved.reply != null
                ? new AsyncapiReplyView(resolver, resolved.reply)
                : null;
        this.messages = resolved.messages != null
            ? resolved.messages.stream()
                .map(m -> new AsyncapiMessageView(this, resolver, m))
                .toList()
            : channel.messages;
        this.security = resolved.security != null
                ? resolved.security.stream()
                    .map(scheme -> new AsyncapiSecuritySchemeView(resolver, scheme))
                    .toList()
                : null;
    }
}
