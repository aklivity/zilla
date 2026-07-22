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

import io.aklivity.zilla.config.engine.GuardConfig;
import io.aklivity.zilla.config.engine.GuardConfigBuilder;
import io.aklivity.zilla.config.engine.GuardInfoRegistry;
import io.aklivity.zilla.config.engine.OptionsConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfigAdapterSpi;

public class GuardAdapter
{
    private static final String TYPE_NAME = "type";
    private static final String KIND_NAME = "kind";
    private static final String STORE_NAME = "store";
    private static final String OPTIONS_NAME = "options";

    private final OptionsConfigAdapter options;

    private String namespace;

    public GuardAdapter()
    {
        this(null);
    }

    public GuardAdapter(
        GuardInfoRegistry guardInfos)
    {
        this.options = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.GUARD,
            guardInfos != null ? guardInfos::lookup : null);
    }

    public void adaptNamespace(
        String namespace)
    {
        this.namespace = namespace;
    }

    public JsonObject adaptToJson(
        GuardConfig guard) throws Exception
    {
        options.adaptType(guard.type);

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(TYPE_NAME, guard.type);

        if (guard.kind != null)
        {
            object.add(KIND_NAME, guard.kind);
        }

        if (guard.store != null)
        {
            object.add(STORE_NAME, guard.store);
        }

        if (guard.options != null)
        {
            object.add(OPTIONS_NAME, options.adaptToJson(guard.options));
        }

        return object.build();
    }

    public GuardConfig adaptFromJson(
        String name,
        JsonObject object) throws Exception
    {
        String type = object.getString(TYPE_NAME);

        options.adaptType(type);

        GuardConfigBuilder<GuardConfig> guard = GuardConfig.builder()
            .namespace(namespace)
            .name(name)
            .type(type);

        if (object.containsKey(KIND_NAME))
        {
            guard.kind(object.getString(KIND_NAME));
        }

        if (object.containsKey(STORE_NAME))
        {
            guard.store(object.getString(STORE_NAME));
        }

        if (object.containsKey(OPTIONS_NAME))
        {
            guard.options(options.adaptFromJson(object.getJsonObject(OPTIONS_NAME)));
        }

        return guard.build();
    }
}
