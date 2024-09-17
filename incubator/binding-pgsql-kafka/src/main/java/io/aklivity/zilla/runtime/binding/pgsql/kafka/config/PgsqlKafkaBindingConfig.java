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
package io.aklivity.zilla.runtime.binding.pgsql.kafka.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

import org.agrona.collections.ObjectHashSet;

import io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.PgsqlKafkaConfiguration;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class PgsqlKafkaBindingConfig
{
    public final long id;
    public final String name;
    public final KindConfig kind;
    public final List<PgsqlKafkaRouteConfig> routes;
    public final Set<CatalogHandler> catalogs;

    public PgsqlKafkaBindingConfig(
        PgsqlKafkaConfiguration config,
        BindingConfig binding,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.routes = binding.routes.stream().map(PgsqlKafkaRouteConfig::new).collect(toList());

        Set<CatalogHandler> catalogs = new ObjectHashSet<>();
        for (CatalogedConfig catalog : binding.catalogs)
        {
            CatalogHandler handler = supplyCatalog.apply(catalog.id);
            catalogs.add(handler);
        }
        this.catalogs = catalogs;
    }

    public PgsqlKafkaRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }
}
