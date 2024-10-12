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
package io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver;

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

    public OpenapiResolver(
        Openapi model)
    {
        this.schemas = new OpenapiSchemaResolver(model);
        this.parameters = new OpenapiParameterResolver(model);
        this.headers = new OpenapiHeaderResolver(model);
        this.requestBodies = new OpenapiRequestBodyResolver(model);
        this.responses = new OpenapiResponseResolver(model);
        this.securitySchemes = new OpenapiSecuritySchemeResolver(model);
        this.links = new OpenapiLinkResolver(model);
    }
}
