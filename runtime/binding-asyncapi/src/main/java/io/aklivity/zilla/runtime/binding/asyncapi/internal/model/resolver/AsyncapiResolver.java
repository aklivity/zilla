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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.model.resolver;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;

public final class AsyncapiResolver
{
    public final String defaultContentType;
    public final AsyncapiChannelResolver channels;
    public final AsyncapiOperationResolver operations;
    public final AsyncapiMessageResolver messages;
    public final AsyncapiSecuritySchemeResolver securitySchemes;
    public final AsyncapiSchemaResolver schemas;
    public final AsyncapiMessageTraitResolver messageTraits;
    public final AsyncapiServerVariableResolver serverVariables;
    public final AsyncapiCorrelationIdResolver correlationIds;

    private final Set<String> unresolved;

    public AsyncapiResolver(
        Asyncapi model)
    {
        this.defaultContentType = model.defaultContentType;
        this.channels = new AsyncapiChannelResolver(model);
        this.operations = new AsyncapiOperationResolver(model);
        this.messages = new AsyncapiMessageResolver(model);
        this.securitySchemes = new AsyncapiSecuritySchemeResolver(model);
        this.schemas = new AsyncapiSchemaResolver(model);
        this.messageTraits = new AsyncapiMessageTraitResolver(model);
        this.serverVariables = new AsyncapiServerVariableResolver(model);
        this.correlationIds = new AsyncapiCorrelationIdResolver(model);
        this.unresolved = new LinkedHashSet<>();
    }

    public Set<String> unresolved()
    {
        Stream.of(
            channels.unresolved(),
            operations.unresolved(),
            messages.unresolved(),
            securitySchemes.unresolved(),
            schemas.unresolved(),
            messageTraits.unresolved(),
            serverVariables.unresolved(),
            correlationIds.unresolved())
            .forEach(unresolved::addAll);

        return unresolved;
    }
}
