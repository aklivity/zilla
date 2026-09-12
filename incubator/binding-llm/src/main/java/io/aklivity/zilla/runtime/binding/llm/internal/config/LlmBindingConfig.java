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
package io.aklivity.zilla.runtime.binding.llm.internal.config;

import java.util.List;
import java.util.stream.Collectors;

import io.aklivity.zilla.config.binding.llm.LlmOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.dialect.LlmDialectResolver;

public final class LlmBindingConfig
{
    public final long id;
    public final LlmOptionsConfig options;
    public final LlmDialectResolver dialects;

    private final List<LlmRouteConfig> routes;

    public LlmBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.options = (LlmOptionsConfig) binding.options;
        this.dialects = new LlmDialectResolver(options != null ? options.dialect : null);
        this.routes = binding.routes.stream()
            .map(LlmRouteConfig::new)
            .collect(Collectors.toList());
    }

    public LlmRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }
}
