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

import static java.util.Objects.requireNonNull;

import java.util.List;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiChannel;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.resolver.AsyncapiResolver;

public final class AsyncapiChannelView
{
    public final String name;
    public final String address;
    public final List<AsyncapiMessageView> messages;
    public final List<AsyncapiParameterView> parameters;

    public boolean hasMessages()
    {
        return messages != null && !messages.isEmpty();
    }

    public boolean hasParameters()
    {
        return parameters != null && !parameters.isEmpty();
    }

    AsyncapiChannelView(
        AsyncapiResolver resolver,
        AsyncapiChannel model)
    {
        this(resolver, model.ref, model);
    }

    AsyncapiChannelView(
        AsyncapiResolver resolver,
        String name,
        AsyncapiChannel model)
    {
        final AsyncapiChannel resolved = resolver.channels.resolve(model);

        this.name = requireNonNull(name);
        this.address = resolved.address;
        this.messages = resolved.messages != null
            ? resolved.messages.entrySet().stream()
                .map(e -> new AsyncapiMessageView(resolver, e.getKey(), e.getValue()))
                .toList()
            : null;
        this.parameters = resolved.parameters != null
            ? resolved.parameters.entrySet().stream()
                .map(e -> new AsyncapiParameterView(resolver, e.getKey(), e.getValue()))
                .toList()
            : null;
    }
}
