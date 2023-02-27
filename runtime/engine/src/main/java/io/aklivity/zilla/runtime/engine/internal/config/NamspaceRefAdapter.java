/*
 * Copyright 2021-2022 Aklivity Inc.
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

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;

import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;

public class NamspaceRefAdapter implements JsonbAdapter<NamespaceRef, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String LINKS_NAME = "links";

    private static final Map<String, String> LINKS_DEFAULT = emptyMap();
    private final ConfigAdapterContext context;

    public NamspaceRefAdapter(
        ConfigAdapterContext context)
    {
        this.context = context;
    }

    private final ConfigAdapterContext context;

    public NamspaceRefAdapter(
        ConfigAdapterContext context)
    {
        this.context = context;
    }

    private final ConfigAdapterContext context;

    public NamspaceRefAdapter(
        ConfigAdapterContext context)
    {
        this.context = context;
    }

    @Override
    public JsonObject adaptToJson(
        NamespaceRef ref)
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
    public NamespaceRef adaptFromJson(
        JsonObject object)
    {
        String name = object.getString(NAME_NAME);
        Map<String, String> links = object.containsKey(LINKS_NAME)
                ? object.getJsonObject(LINKS_NAME)
                    .entrySet()
                    .stream()
                    .collect(toMap(Map.Entry::getKey, e -> asJsonString(e.getValue())))
                : LINKS_DEFAULT;

        return new NamespaceRef(name, links);
    }

    private static String asJsonString(
        JsonValue value)
    {
        return ((JsonString) value).getString();
    }
}
