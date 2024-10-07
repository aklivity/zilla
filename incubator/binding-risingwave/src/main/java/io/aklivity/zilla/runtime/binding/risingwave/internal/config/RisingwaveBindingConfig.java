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
import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveUdfConfig;
import io.aklivity.zilla.runtime.binding.risingwave.internal.RisingwaveConfiguration;
import io.aklivity.zilla.runtime.binding.risingwave.internal.statement.RisingwaveCreateFunctionTemplate;
import io.aklivity.zilla.runtime.binding.risingwave.internal.statement.RisingwaveCreateMaterializedViewTemplate;
import io.aklivity.zilla.runtime.binding.risingwave.internal.statement.RisingwaveCreateSinkTemplate;
import io.aklivity.zilla.runtime.binding.risingwave.internal.statement.RisingwaveCreateSourceTemplate;
import io.aklivity.zilla.runtime.binding.risingwave.internal.statement.RisingwaveCreateTableTemplate;
import io.aklivity.zilla.runtime.binding.risingwave.internal.statement.RisingwaveCreateTopicTemplate;
import io.aklivity.zilla.runtime.binding.risingwave.internal.statement.RisingwaveDescribeTemplate;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class RisingwaveBindingConfig
{
    public final long id;
    public final String name;
    public final RisingwaveOptionsConfig options;
    public final KindConfig kind;
    public final List<RisingwaveRouteConfig> routes;
    public final RisingwaveCreateTopicTemplate createTopic;
    public final RisingwaveCreateMaterializedViewTemplate createView;
    public final RisingwaveDescribeTemplate describeView;
    public final RisingwaveCreateTableTemplate createTable;
    public final RisingwaveCreateSourceTemplate createSource;
    public final RisingwaveCreateSinkTemplate createSink;
    public final RisingwaveCreateFunctionTemplate createFunction;

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

        String bootstrapServer = null;
        String location = null;
        RisingwaveUdfConfig udf = null;

        if (options.kafka != null)
        {
            final CatalogedConfig cataloged = options.kafka.format.cataloged.get(0);
            cataloged.id = binding.resolveId.applyAsLong(cataloged.name);

            final CatalogHandler catalogHandler = supplyCatalog.apply(cataloged.id);
            bootstrapServer = options.kafka.properties.bootstrapServer;
            location = catalogHandler.location();
        }

        this.createTable = new RisingwaveCreateTableTemplate();
        this.createSource = new RisingwaveCreateSourceTemplate(bootstrapServer,
            location, config.kafkaScanStartupTimestampMillis());
        this.createSink = new RisingwaveCreateSinkTemplate(bootstrapServer, location);
        this.createTopic = new RisingwaveCreateTopicTemplate();
        this.createView = new RisingwaveCreateMaterializedViewTemplate();
        this.describeView = new RisingwaveDescribeTemplate();
        this.createFunction = new RisingwaveCreateFunctionTemplate(options.udfs);
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
