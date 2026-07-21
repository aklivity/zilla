/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.openapi.view;

import java.util.Map;
import java.util.Optional;

import io.aklivity.zilla.runtime.common.openapi.model.OpenapiOAuthFlows;

public final class OpenapiOAuthFlowsView
{
    public final OpenapiOAuthFlowView implicit;
    public final OpenapiOAuthFlowView password;
    public final OpenapiOAuthFlowView clientCredentials;
    public final OpenapiOAuthFlowView authorizationCode;

    private final Map<String, Object> extensions;

    OpenapiOAuthFlowsView(
        OpenapiOAuthFlows model)
    {
        this.implicit = model.implicit != null ? new OpenapiOAuthFlowView(model.implicit) : null;
        this.password = model.password != null ? new OpenapiOAuthFlowView(model.password) : null;
        this.clientCredentials = model.clientCredentials != null
                ? new OpenapiOAuthFlowView(model.clientCredentials) : null;
        this.authorizationCode = model.authorizationCode != null
                ? new OpenapiOAuthFlowView(model.authorizationCode) : null;
        this.extensions = model.extensions;
    }

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
}
