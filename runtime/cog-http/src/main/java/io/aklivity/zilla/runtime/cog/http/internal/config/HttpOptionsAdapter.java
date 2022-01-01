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
package io.aklivity.zilla.runtime.cog.http.internal.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.cog.http.internal.HttpCog;
import io.aklivity.zilla.runtime.cog.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.cog.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.Options;
import io.aklivity.zilla.runtime.engine.config.OptionsAdapterSpi;

public final class HttpOptionsAdapter implements OptionsAdapterSpi, JsonbAdapter<Options, JsonObject>
{
    private static final String VERSIONS_NAME = "versions";
    private static final String OVERRIDES_NAME = "overrides";

    @Override
    public String type()
    {
        return HttpCog.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        Options options)
    {
        HttpOptions httpOptions = (HttpOptions) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (httpOptions.versions != null &&
            !httpOptions.versions.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            httpOptions.versions.forEach(v -> entries.add(v.asString()));

            object.add(VERSIONS_NAME, entries);
        }

        if (httpOptions.overrides != null &&
            !httpOptions.overrides.isEmpty())
        {
            JsonObjectBuilder entries = Json.createObjectBuilder();
            httpOptions.overrides.forEach((k, v) -> entries.add(k.asString(), v.asString()));

            object.add(OVERRIDES_NAME, entries);
        }

        return object.build();
    }

    @Override
    public Options adaptFromJson(
        JsonObject object)
    {
        JsonArray versions = object.containsKey(VERSIONS_NAME)
                ? object.getJsonArray(VERSIONS_NAME)
                : null;

        SortedSet<HttpVersion> newVersions = null;

        if (versions != null)
        {
            SortedSet<HttpVersion> newVersions0 = new TreeSet<HttpVersion>();
            versions.forEach(v ->
                newVersions0.add(HttpVersion.of(JsonString.class.cast(v).getString())));
            newVersions = newVersions0;
        }

        JsonObject overrides = object.containsKey(OVERRIDES_NAME)
                ? object.getJsonObject(OVERRIDES_NAME)
                : null;

        Map<String8FW, String16FW> newOverrides = null;

        if (overrides != null)
        {
            Map<String8FW, String16FW> newOverrides0 = new LinkedHashMap<>();
            overrides.forEach((k, v) ->
                newOverrides0.put(new String8FW(k), new String16FW(JsonString.class.cast(v).getString())));
            newOverrides = newOverrides0;
        }

        return new HttpOptions(newVersions, newOverrides);
    }
}
