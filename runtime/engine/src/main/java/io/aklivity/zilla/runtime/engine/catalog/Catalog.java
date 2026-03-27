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
package io.aklivity.zilla.runtime.engine.catalog;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.factory.Aliasable;

/**
 * Entry point for a schema catalog plugin.
 * <p>
 * A {@code Catalog} provides access to a schema registry — a store of named schemas (Avro,
 * Protobuf, JSON Schema, etc.) indexed by subject and version. Bindings that handle structured
 * data (e.g., {@code binding-kafka}) use a catalog to resolve schemas for encoding, decoding,
 * and validation via a {@link CatalogHandler}.
 * </p>
 * <p>
 * Built-in implementations include {@code catalog-schema-registry} (Confluent-compatible),
 * {@code catalog-apicurio}, {@code catalog-karapace}, {@code catalog-inline}, and
 * {@code catalog-filesystem}.
 * </p>
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} through {@link CatalogFactorySpi}.
 * A catalog may declare aliases via {@link Aliasable#aliases()} to support multiple configuration names.
 * </p>
 *
 * @see CatalogContext
 * @see CatalogHandler
 * @see CatalogFactorySpi
 */
public interface Catalog extends Aliasable
{
    /**
     * Returns the unique name identifying this catalog type, e.g. {@code "schema-registry"}.
     *
     * @return the catalog type name
     */
    String name();

    /**
     * Creates a per-thread context for this catalog.
     *
     * @param context  the engine context for the calling I/O thread
     * @return a new {@link CatalogContext} confined to that thread
     */
    CatalogContext supply(
        EngineContext context);

    /**
     * Returns a URL pointing to the JSON schema for this catalog's configuration options.
     *
     * @return the configuration schema URL
     */
    URL type();
}
