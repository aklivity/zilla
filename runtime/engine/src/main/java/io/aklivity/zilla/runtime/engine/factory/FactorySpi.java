/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.factory;

/**
 * Base service provider interface for all engine plugin factories.
 * <p>
 * Every plugin type — binding, guard, vault, catalog, exporter, model — has a corresponding
 * {@code FactorySpi} sub-interface. The {@link #type()} method returns the plugin type name
 * that the engine uses to match the {@code type} field in {@code zilla.yaml} to the correct
 * factory implementation discovered via {@link java.util.ServiceLoader}.
 * </p>
 *
 * @see BindingFactorySpi
 * @see GuardFactorySpi
 * @see VaultFactorySpi
 * @see CatalogFactorySpi
 * @see ExporterFactorySpi
 * @see ModelFactorySpi
 */
public interface FactorySpi
{
    /**
     * Returns the plugin type name, e.g. {@code "http"}, {@code "jwt"}, {@code "filesystem"}.
     * <p>
     * This value must match the {@code type} field used in {@code zilla.yaml} configuration
     * for the engine to associate the configuration entry with this factory.
     * </p>
     *
     * @return the plugin type name
     */
    String type();
}
