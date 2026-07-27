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
package io.aklivity.zilla.config.engine;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.function.ToLongFunction;

public abstract class BindingConfig
{
    public transient long id;
    public transient long entryId;
    public transient ToLongFunction<String> resolveId;

    public transient long vaultId;
    public transient String qvault;

    public transient long[] metricIds;

    public transient long typeId;
    public transient long kindId;

    public transient long originTypeId;
    public transient long routedTypeId;

    public final String namespace;
    public final String name;
    public final String qname;
    public final String type;
    public final KindConfig kind;
    public final String entry;
    public final String vault;
    public final OptionsConfig options;
    public final List<CatalogedConfig> catalogs;
    public final List<RouteConfig> routes;
    public final TelemetryRefConfig telemetryRef;

    protected BindingConfig(
        String namespace,
        String name,
        String type,
        KindConfig kind,
        String entry,
        String vault,
        OptionsConfig options,
        List<CatalogedConfig> catalogs,
        List<RouteConfig> routes,
        TelemetryRefConfig telemetryRef)
    {
        this.namespace = requireNonNull(namespace);
        this.name = requireNonNull(name);
        this.qname = String.format("%s:%s", namespace, name);
        this.type = requireNonNull(type);
        this.kind = requireNonNull(kind);
        this.entry = entry;
        this.vault = vault;
        this.options = options;
        this.routes = routes;
        this.catalogs = catalogs;
        this.telemetryRef = telemetryRef;
    }
}
