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
package io.aklivity.zilla.runtime.binding.risingwave.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveOptionsConfig;
import io.aklivity.zilla.runtime.binding.risingwave.internal.RisingwaveConfiguration;
import io.aklivity.zilla.runtime.binding.risingwave.internal.statement.RisingwaveCreateTableGenerator;
import io.aklivity.zilla.runtime.binding.risingwave.internal.statement.RisingwaveCreateTopicGenerator;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class RisingwaveBindingConfig
{
    public final long id;
    public final String name;
    public final RisingwaveOptionsConfig options;
    public final KindConfig kind;
    public final List<RisingwaveRouteConfig> routes;
    public final RisingwaveCreateTopicGenerator createTopic;
    public final RisingwaveCreateTableGenerator createTable;

    public RisingwaveBindingConfig(
        RisingwaveConfiguration config,
        BindingConfig binding,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.options = RisingwaveOptionsConfig.class.cast(binding.options);
        this.kind = binding.kind;
        this.routes = binding.routes.stream().map(RisingwaveRouteConfig::new).collect(toList());

        this.createTopic = new RisingwaveCreateTopicGenerator();
        final CatalogHandler catalogHandler = supplyCatalog.apply(options.kafka.format.cataloged.get(0).id);
        this.createTable = new RisingwaveCreateTableGenerator(options.kafka.properties.bootstrapServer,
            catalogHandler.location(), config.kafkaScanStartupTimestampMillis());
    }

    public RisingwaveRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }

    public RisingwaveRouteConfig resolve(
        long authorization,
        DirectBuffer statement,
        int offset,
        int length)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(statement, offset, length))
            .findFirst()
            .orElse(null);
    }
}
