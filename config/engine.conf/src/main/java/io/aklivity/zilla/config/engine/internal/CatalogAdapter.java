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
package io.aklivity.zilla.config.engine.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.config.engine.CatalogConfig;
import io.aklivity.zilla.config.engine.CatalogConfigBuilder;
import io.aklivity.zilla.config.engine.CatalogInfoRegistry;
import io.aklivity.zilla.config.engine.OptionsConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfigAdapterSpi;

public class CatalogAdapter
{
    private static final String TYPE_NAME = "type";
    private static final String VAULT_NAME = "vault";
    private static final String OPTIONS_NAME = "options";

    private final OptionsConfigAdapter options;

    private String namespace;

    public CatalogAdapter()
    {
        this(null);
    }

    public CatalogAdapter(
        CatalogInfoRegistry catalogInfos)
    {
        this.options = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.CATALOG,
            catalogInfos != null ? catalogInfos::lookup : null);
    }

    public void adaptNamespace(
        String namespace)
    {
        this.namespace = namespace;
    }

    public JsonObject adaptToJson(
        CatalogConfig catalog) throws Exception
    {
        options.adaptType(catalog.type);

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(TYPE_NAME, catalog.type);

        if (catalog.options != null)
        {
            object.add(OPTIONS_NAME, options.adaptToJson(catalog.options));
        }

        return object.build();
    }

    public CatalogConfig adaptFromJson(
        String name,
        JsonObject object) throws Exception
    {
        CatalogConfigBuilder<CatalogConfig> builder = CatalogConfig.builder()
            .namespace(namespace)
            .name(name);

        String type = object.getString(TYPE_NAME);
        builder.type(type);

        if (object.containsKey(VAULT_NAME))
        {
            builder.vault(object.getString(VAULT_NAME));
        }

        options.adaptType(type);

        if (object.containsKey(OPTIONS_NAME))
        {
            builder.options(options.adaptFromJson(object.getJsonObject(OPTIONS_NAME)));
        }

        return builder.build();
    }
}
