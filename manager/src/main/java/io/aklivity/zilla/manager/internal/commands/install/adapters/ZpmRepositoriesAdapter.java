/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.manager.internal.commands.install.adapters;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.manager.internal.commands.install.ZpmRepository;

public final class ZpmRepositoriesAdapter implements JsonbAdapter<List<ZpmRepository>, JsonValue>
{
    @Override
    public JsonValue adaptToJson(
        List<ZpmRepository> repositories)
    {
        return !repositories.isEmpty() && repositories.stream().allMatch(r -> r.id != null)
            ? adaptToJsonObject(repositories)
            : adaptToJsonArray(repositories);
    }

    @Override
    public List<ZpmRepository> adaptFromJson(
        JsonValue value)
    {
        List<ZpmRepository> repositories = new ArrayList<>();
        if (value instanceof JsonObject object)
        {
            object.forEach((id, location) -> repositories.add(newRepository(id, location)));
        }
        else
        {
            value.asJsonArray().forEach(location -> repositories.add(newRepository(null, location)));
        }
        return repositories;
    }

    private static JsonValue adaptToJsonObject(
        List<ZpmRepository> repositories)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        repositories.forEach(r -> object.add(r.id, r.location));
        return object.build();
    }

    private static JsonValue adaptToJsonArray(
        List<ZpmRepository> repositories)
    {
        JsonArrayBuilder array = Json.createArrayBuilder();
        repositories.forEach(r -> array.add(r.location));
        return array.build();
    }

    private static ZpmRepository newRepository(
        String id,
        JsonValue location)
    {
        ZpmRepository repository = new ZpmRepository();
        repository.id = id;
        repository.location = ((JsonString) location).getString();
        return repository;
    }
}
