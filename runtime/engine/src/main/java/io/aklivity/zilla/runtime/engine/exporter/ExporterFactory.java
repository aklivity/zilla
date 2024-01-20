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
package io.aklivity.zilla.runtime.engine.exporter;

import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.Map;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.factory.Factory;

public final class ExporterFactory extends Factory
{
    private final Map<String, ExporterFactorySpi> factorySpis;

    public static ExporterFactory instantiate(
        Configuration config)
    {
        return instantiate(config, load(ExporterFactorySpi.class), ExporterFactory::new);
    }

    public Iterable<String> names()
    {
        return factorySpis.keySet();
    }

    public Exporter create(
        String type,
        Configuration config)
    {
        requireNonNull(type, "type");

        ExporterFactorySpi factorySpi = requireNonNull(factorySpis.get(type), () -> "Unrecognized exporter type: " + type);

        return factorySpi.create(config);
    }

    private ExporterFactory(
        Map<String, ExporterFactorySpi> factorySpis)
    {
        this.factorySpis = factorySpis;
    }
}
