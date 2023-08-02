/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.http.filesystem.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.filesystem.config.HttpFileSystemConditionConfig;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.HttpFileSystemBinding;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public final class HttpFileSystemConditionConfigAdapter
    implements ConditionConfigAdapterSpi, JsonbAdapter<ConditionConfig, JsonObject>
{
    private static final String PATH_NAME = "path";

    @Override
    public String type()
    {
        return HttpFileSystemBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        HttpFileSystemConditionConfig httpCondition = (HttpFileSystemConditionConfig) condition;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (httpCondition.path != null)
        {
            object.add(PATH_NAME, httpCondition.path);
        }

        return object.build();
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        String path = object.containsKey(PATH_NAME)
                ? object.getString(PATH_NAME)
                : null;

        return new HttpFileSystemConditionConfig(path);
    }
}
