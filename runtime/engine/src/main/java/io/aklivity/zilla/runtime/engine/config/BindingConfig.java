/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.config;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.function.ToLongFunction;

public class BindingConfig
{
    public transient long id;
    public transient ToLongFunction<String> resolveId;

    public transient long vaultId;

    public final String vault;
    public final String entry;
    public final String type;
    public final KindConfig kind;
    public final OptionsConfig options;
    public final List<RouteConfig> routes;

    public BindingConfig(
        String vault,
        String entry,
        String type,
        KindConfig kind,
        OptionsConfig options,
        List<RouteConfig> routes)
    {
        this.vault = vault;
        this.entry = entry;
        this.type = requireNonNull(type);
        this.kind = requireNonNull(kind);
        this.options = options;
        this.routes = routes;
    }
}
