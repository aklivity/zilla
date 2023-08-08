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
package io.aklivity.zilla.runtime.engine.internal.config;

import static io.aklivity.zilla.runtime.engine.config.NamespaceRefConfigBuilder.LINKS_DEFAULT;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.NamespaceRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceRefConfigBuilder;

public class NamspaceRefAdapter implements JsonbAdapter<NamespaceRefConfig, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String LINKS_NAME = "links";

    public NamspaceRefAdapter(
        ConfigAdapterContext context)
    {
    }

    @Override
    public JsonObject adaptToJson(
        NamespaceRefConfig ref)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(NAME_NAME, ref.name);

        if (!LINKS_DEFAULT.equals(ref.links))
        {
            JsonObjectBuilder links = Json.createObjectBuilder();
            ref.links.forEach(links::add);
            object.add(LINKS_NAME, links);
        }

        return object.build();
    }

    @Override
    public NamespaceRefConfig adaptFromJson(
        JsonObject object)
    {
        NamespaceRefConfigBuilder<NamespaceRefConfig> namespace = NamespaceRefConfig.builder();

        namespace.name(object.getString(NAME_NAME));

        if (object.containsKey(LINKS_NAME))
        {
            object.getJsonObject(LINKS_NAME)
                .entrySet()
                .stream()
                .forEach(e -> namespace.link(e.getKey(), JsonString.class.cast(e.getValue()).getString()));
        }

        return namespace.build();
    }
}
