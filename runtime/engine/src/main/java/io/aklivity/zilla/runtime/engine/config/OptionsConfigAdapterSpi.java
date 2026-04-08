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
package io.aklivity.zilla.runtime.engine.config;

import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.factory.Aliasable;

/**
 * Service provider interface for serializing and deserializing the {@code options} block
 * of a binding, exporter, vault, guard, or catalog configuration entry in {@code zilla.yaml}.
 * <p>
 * Each plugin type that accepts an {@code options} block provides an implementation of this
 * interface, registered via {@link java.util.ServiceLoader} in
 * {@code META-INF/services/io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi}.
 * The engine selects the correct adapter by matching {@link #type()} against the enclosing
 * entry's {@code type} field and {@link #kind()} against the entry's plugin category.
 * </p>
 * <p>
 * Implementations extend {@link jakarta.json.bind.adapter.JsonbAdapter} to perform the
 * conversion between the plugin's concrete {@link OptionsConfig} subclass and a
 * {@link jakarta.json.JsonObject} representing the raw YAML/JSON {@code options} object.
 * </p>
 * <p>
 * A plugin may also declare alternative type names via {@link Aliasable#aliases()} to allow
 * the same adapter to handle multiple configuration type names.
 * </p>
 *
 * @see ConditionConfigAdapterSpi
 * @see ModelConfigAdapterSpi
 * @see WithConfigAdapterSpi
 */
public interface OptionsConfigAdapterSpi extends JsonbAdapter<OptionsConfig, JsonObject>, Aliasable
{
    /**
     * The plugin category to which an options adapter belongs, used by the engine to
     * select the correct adapter when multiple plugins share the same {@link #type()} name
     * across different categories.
     */
    enum Kind
    {
        /** Options adapter for a protocol binding (e.g., {@code binding-http}). */
        BINDING,
        /** Options adapter for a metrics exporter (e.g., {@code exporter-prometheus}). */
        EXPORTER,
        /** Options adapter for a cryptographic vault (e.g., {@code vault-filesystem}). */
        VAULT,
        /** Options adapter for an authorization guard (e.g., {@code guard-jwt}). */
        GUARD,
        /** Options adapter for a schema catalog (e.g., {@code catalog-schema-registry}). */
        CATALOG,
        /** Options adapter for a mutable runtime state store (e.g., {@code store-memory}). */
        STORE
    }

    /**
     * Returns the plugin category this adapter serves.
     * <p>
     * Combined with {@link #type()}, uniquely identifies this adapter within the engine's
     * adapter registry.
     * </p>
     *
     * @return the plugin kind
     */
    Kind kind();

    /**
     * Returns the plugin type name this adapter handles, e.g. {@code "http"}, {@code "jwt"}.
     * <p>
     * Must match the {@code type} field of the configuration entry whose {@code options}
     * block this adapter serializes and deserializes.
     * </p>
     *
     * @return the plugin type name
     */
    String type();

    /**
     * Serializes a plugin-specific {@link OptionsConfig} instance to a
     * {@link jakarta.json.JsonObject} for writing back to YAML/JSON.
     *
     * @param options  the options configuration to serialize
     * @return a {@link jakarta.json.JsonObject} representation of the options
     */
    @Override
    JsonObject adaptToJson(
        OptionsConfig options);

    /**
     * Deserializes a {@link jakarta.json.JsonObject} from the {@code options} block in
     * {@code zilla.yaml} into a plugin-specific {@link OptionsConfig} instance.
     *
     * @param object  the raw JSON object from the {@code options} block
     * @return the deserialized {@link OptionsConfig}
     */
    @Override
    OptionsConfig adaptFromJson(
        JsonObject object);

    /**
     * Supplies a {@link ConfigAdapterContext} to this adapter after construction, allowing
     * it to read referenced resource files during deserialization.
     *
     * @param context  the adapter context
     * @deprecated {@link ConfigAdapterContext} is deprecated; resource resolution is now
     *             handled via the engine's path resolution mechanism
     */
    @Deprecated
    default void adaptContext(
        ConfigAdapterContext context)
    {
    }
}
