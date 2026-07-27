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

import java.util.function.Function;

import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

public class OptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
{
    private final Function<String, ? extends OptionsInfo> infoLookup;

    private JsonbAdapter<OptionsConfig, JsonObject> delegate;

    public OptionsConfigAdapter(
        Kind kind)
    {
        this(kind, null);
    }

    public OptionsConfigAdapter(
        Kind kind,
        Function<String, ? extends OptionsInfo> infoLookup)
    {
        this.infoLookup = infoLookup;
    }

    public void adaptType(
        String type)
    {
        OptionsInfo info = infoLookup != null && type != null ? infoLookup.apply(type) : null;
        delegate = info != null ? info.options() : null;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options) throws Exception
    {
        return delegate != null ? delegate.adaptToJson(options) : null;
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object) throws Exception
    {
        return delegate != null ? delegate.adaptFromJson(object) : null;
    }

    /**
     * The plugin category an options adapter belongs to, used to select the correct
     * adapter when multiple plugins share the same type name across different categories.
     */
    public enum Kind
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
}
