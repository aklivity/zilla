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
package io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;

public final class OpenapiResolver
{
    public final OpenapiSchemaResolver schemas;
    public final OpenapiParameterResolver parameters;
    public final OpenapiHeaderResolver headers;
    public final OpenapiRequestBodyResolver requestBodies;
    public final OpenapiResponseResolver responses;
    public final OpenapiSecuritySchemeResolver securitySchemes;
    public final OpenapiLinkResolver links;

    private final Set<String> unresolved;

    public OpenapiResolver(
        Openapi model)
    {
        this.unresolved = new LinkedHashSet<>();
        this.schemas = new OpenapiSchemaResolver(model, unresolved);
        this.parameters = new OpenapiParameterResolver(model, unresolved);
        this.headers = new OpenapiHeaderResolver(model, unresolved);
        this.requestBodies = new OpenapiRequestBodyResolver(model, unresolved);
        this.responses = new OpenapiResponseResolver(model, unresolved);
        this.securitySchemes = new OpenapiSecuritySchemeResolver(model, unresolved);
        this.links = new OpenapiLinkResolver(model, unresolved);
    }

    public Collection<String> unresolvedRefs()
    {
        return unresolved;
    }
}
