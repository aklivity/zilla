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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.view;

import java.util.List;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.resolver.AsyncapiResolver;

public final class AsyncapiMessageView
{
    public final AsyncapiChannelView channel;
    public final String name;
    public final AsyncapiSchemaView headers;
    public final String contentType;
    public final AsyncapiSchemaItemView payload;
    public final List<AsyncapiTraitView> traits;
    public final AsyncapiCorrelationIdView correlationId;
    public final AsyncapiMessageBindingsView bindings;

    public boolean hasTraits()
    {
        return traits != null && !traits.isEmpty();
    }

    AsyncapiMessageView(
        AsyncapiResolver resolver,
        String name,
        AsyncapiMessage model)
    {
        this((AsyncapiChannelView) null, resolver, name, model);
    }

    AsyncapiMessageView(
        AsyncapiOperationView operation,
        AsyncapiResolver resolver,
        AsyncapiMessage model)
    {
        this.channel = operation.channel;

        final AsyncapiMessage resolved = resolver.operations.resolve(model);

        this.name = resolver.operations.resolveName(model.ref);
        this.headers = resolved.headers != null
            ? new AsyncapiSchemaView(resolver, resolved.headers)
            : null;
        this.contentType = resolved.contentType != null
            ? resolved.contentType
            : resolver.defaultContentType;
        this.payload = resolved.payload != null
                ? AsyncapiSchemaItemView.of(resolver, resolved.payload)
                : null;
        this.traits = resolved.traits != null
                ? resolved.traits.stream()
                    .map(m -> new AsyncapiTraitView(resolver, m))
                    .toList()
                : null;
        this.correlationId = resolved.correlationId != null
                ? new AsyncapiCorrelationIdView(resolver, resolved.correlationId)
                : null;
        this.bindings = resolved.bindings != null
                ? new AsyncapiMessageBindingsView(resolver, resolved.bindings)
                : null;
    }

    AsyncapiMessageView(
        AsyncapiChannelView channel,
        AsyncapiResolver resolver,
        String name,
        AsyncapiMessage model)
    {
        this.channel = channel;

        final AsyncapiMessage resolved = resolver.messages.resolve(model);

        this.name = name;
        this.headers = resolved.headers != null
            ? new AsyncapiSchemaView(resolver, resolved.headers)
            : null;
        this.contentType = resolved.contentType != null
            ? resolved.contentType
            : resolver.defaultContentType;
        this.payload = resolved.payload != null
                ? AsyncapiSchemaItemView.of(resolver, resolved.payload)
                : null;
        this.traits = resolved.traits != null
                ? resolved.traits.stream()
                    .map(m -> new AsyncapiTraitView(resolver, m))
                    .toList()
                : null;
        this.correlationId = resolved.correlationId != null
                ? new AsyncapiCorrelationIdView(resolver, resolved.correlationId)
                : null;
        this.bindings = resolved.bindings != null
                ? new AsyncapiMessageBindingsView(resolver, resolved.bindings)
                : null;
    }
}
