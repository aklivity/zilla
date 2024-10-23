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
package io.aklivity.zilla.runtime.engine.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.GuardConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class GuardAdapter
{
    private static final String TYPE_NAME = "type";
    private static final String OPTIONS_NAME = "options";

    private final OptionsConfigAdapter options;

    private String namespace;

    public GuardAdapter(
        ConfigAdapterContext context)
    {
        this.options = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.GUARD, context);
    }

    public void adaptNamespace(
        String namespace)
    {
        this.namespace = namespace;
    }

    public JsonObject adaptToJson(
        GuardConfig guard)
    {
        options.adaptType(guard.type);

        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(TYPE_NAME, guard.type);

        if (guard.options != null)
        {
            object.add(OPTIONS_NAME, options.adaptToJson(guard.options));
        }

        return object.build();
    }

    public GuardConfig adaptFromJson(
        String name,
        JsonObject object)
    {
        String type = object.getString(TYPE_NAME);

        options.adaptType(type);

        GuardConfigBuilder<GuardConfig> guard = GuardConfig.builder()
            .namespace(namespace)
            .name(name)
            .type(type);

        if (object.containsKey(OPTIONS_NAME))
        {
            guard.options(options.adaptFromJson(object.getJsonObject(OPTIONS_NAME)));
        }

        return guard.build();
    }
}
