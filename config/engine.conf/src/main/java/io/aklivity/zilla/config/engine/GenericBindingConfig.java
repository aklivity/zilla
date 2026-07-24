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

import java.util.List;
import java.util.function.Function;

public final class GenericBindingConfig extends BindingConfig
{
    public static GenericBindingConfigBuilder<GenericBindingConfig> builder()
    {
        return new GenericBindingConfigBuilder<>(GenericBindingConfig.class::cast);
    }

    public static <T> GenericBindingConfigBuilder<T> builder(
        Function<BindingConfig, T> mapper)
    {
        return new GenericBindingConfigBuilder<>(mapper);
    }

    public static GenericBindingConfigBuilder<GenericBindingConfig> builder(
        BindingConfig binding)
    {
        return builder()
            .vault(binding.vault)
            .namespace(binding.namespace)
            .name(binding.name)
            .type(binding.type)
            .kind(binding.kind)
            .entry(binding.entry)
            .options(binding.options)
            .catalogs(binding.catalogs)
            .routes(binding.routes)
            .telemetry(binding.telemetryRef);
    }

    GenericBindingConfig(
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
        super(namespace, name, type, kind, entry, vault, options, catalogs, routes, telemetryRef);
    }
}
