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
package io.aklivity.zilla.runtime.binding.openapi.internal.view;

import java.util.Map;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiLink;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver.OpenapiResolver;

public final class OpenapiLinkView
{
    public final String name;

    public final String operationRef;
    public final String operationId;
    public final Map<String, String> parameters;
    public final String requestBody;
    public final OpenapiServerView server;

    OpenapiLinkView(
        OpenapiResolver resolver,
        String name,
        OpenapiLink model)
    {
        this.name = name;

        OpenapiLink resolved = resolver.links.resolve(model);

        this.operationRef = resolved.operationRef;
        this.operationId = resolved.operationId;
        this.parameters = resolved.parameters;
        this.requestBody = resolved.requestBody;
        this.server = new OpenapiServerView(resolver, model.server);
    }
}
