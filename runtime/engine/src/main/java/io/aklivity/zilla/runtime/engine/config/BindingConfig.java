/*
 * Copyright 2021-2023 Aklivity Inc.
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
import static java.util.function.Function.identity;

import java.util.List;
import java.util.function.ToLongFunction;

public class BindingConfig
{
    public transient long id;
    public transient long entryId;
    public transient long typeId;
    public transient long kindId;
    public transient long originTypeId;
    public transient long routedTypeId;
    public transient ToLongFunction<String> resolveId;

    public transient long vaultId;

    public transient long[] metricIds;

    public final String vault;
    public final String name;
    public final String type;
    public final KindConfig kind;
    public final String entry;
    public final OptionsConfig options;
    public final List<RouteConfig> routes;
    public final TelemetryRefConfig telemetryRef;

    public static BindingConfigBuilder<BindingConfig> builder()
    {
        return new BindingConfigBuilder<>(identity());
    }

    BindingConfig(
        String vault,
        String name,
        String type,
        KindConfig kind,
        String entry,
        OptionsConfig options,
        List<RouteConfig> routes,
        TelemetryRefConfig telemetryRef)
    {
        this.vault = vault;
        this.name = name;
        this.type = requireNonNull(type);
        this.kind = requireNonNull(kind);
        this.entry = entry;
        this.options = options;
        this.routes = routes;
        this.telemetryRef = telemetryRef;
    }
}
