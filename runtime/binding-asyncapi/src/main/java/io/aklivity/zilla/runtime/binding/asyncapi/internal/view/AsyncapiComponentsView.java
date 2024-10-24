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
import java.util.Map;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiComponents;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.resolver.AsyncapiResolver;

public final class AsyncapiComponentsView
{
    public final List<AsyncapiSecuritySchemeView> securitySchemes;
    public final List<AsyncapiMessageView> messages;
    public final Map<String, AsyncapiSchemaItemView> schemas;
    public final List<AsyncapiCorrelationIdView> correlationIds;
    public final List<AsyncapiTraitView> messageTraits;
    public final List<AsyncapiServerVariableView> serverVariables;

    AsyncapiComponentsView(
        AsyncapiResolver resolver,
        AsyncapiComponents model)
    {
        this.securitySchemes = model.securitySchemes != null
                ? model.securitySchemes.entrySet().stream()
                    .map(e -> new AsyncapiSecuritySchemeView(resolver, e.getKey(), e.getValue()))
                    .toList()
                : null;

        this.messages = model.messages != null
                ? model.messages.entrySet().stream()
                    .map(e -> new AsyncapiMessageView(resolver, e.getKey(), e.getValue()))
                    .toList()
                : null;

        this.schemas = model.schemas != null
                ? model.schemas.entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey(), e -> AsyncapiSchemaItemView.of(resolver, e.getValue())))
                : null;

        this.correlationIds = model.correlationIds != null
                ? model.correlationIds.entrySet().stream()
                    .map(e -> new AsyncapiCorrelationIdView(resolver, e.getValue()))
                    .toList()
                : null;

        this.messageTraits = model.messageTraits != null
                ? model.messageTraits.entrySet().stream()
                    .map(e -> new AsyncapiTraitView(resolver, e.getValue()))
                    .toList()
                : null;

        this.serverVariables = model.serverVariables != null
                ? model.serverVariables.entrySet().stream()
                    .map(e -> new AsyncapiServerVariableView(resolver, e.getKey(), e.getValue()))
                    .toList()
                : null;

    }
}
