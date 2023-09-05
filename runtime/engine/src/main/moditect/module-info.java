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
module io.aklivity.zilla.runtime.engine
{
    exports io.aklivity.zilla.runtime.engine;
    exports io.aklivity.zilla.runtime.engine.config;

    exports io.aklivity.zilla.runtime.engine.binding;
    exports io.aklivity.zilla.runtime.engine.binding.function;
    exports io.aklivity.zilla.runtime.engine.catalog;
    exports io.aklivity.zilla.runtime.engine.exporter;
    exports io.aklivity.zilla.runtime.engine.guard;
    exports io.aklivity.zilla.runtime.engine.metrics;
    exports io.aklivity.zilla.runtime.engine.metrics.reader;
    exports io.aklivity.zilla.runtime.engine.util.function;
    exports io.aklivity.zilla.runtime.engine.vault;

    exports io.aklivity.zilla.runtime.engine.ext;

    exports io.aklivity.zilla.runtime.engine.budget;
    exports io.aklivity.zilla.runtime.engine.buffer;
    exports io.aklivity.zilla.runtime.engine.concurrent;
    exports io.aklivity.zilla.runtime.engine.poller;

    requires transitive jakarta.json;
    requires transitive jakarta.json.bind;
    requires transitive org.agrona.core;
    requires org.leadpony.justify;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires com.fasterxml.jackson.databind;
    requires jdk.unsupported;
    requires java.net.http;

    uses io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;
    uses io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
    uses io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

    uses io.aklivity.zilla.runtime.engine.binding.BindingFactorySpi;
    uses io.aklivity.zilla.runtime.engine.catalog.CatalogFactorySpi;
    uses io.aklivity.zilla.runtime.engine.exporter.ExporterFactorySpi;
    uses io.aklivity.zilla.runtime.engine.guard.GuardFactorySpi;
    uses io.aklivity.zilla.runtime.engine.metrics.MetricGroupFactorySpi;
    uses io.aklivity.zilla.runtime.engine.vault.VaultFactorySpi;
    uses io.aklivity.zilla.runtime.engine.ext.EngineExtSpi;
    uses io.aklivity.zilla.runtime.engine.expression.ExpressionResolverSpi;

    provides io.aklivity.zilla.runtime.engine.expression.ExpressionResolverSpi
            with io.aklivity.zilla.runtime.engine.internal.expression.EnvironmentResolverSpi;
}
