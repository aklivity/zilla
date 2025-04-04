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

public interface OptionsConfigAdapterSpi extends JsonbAdapter<OptionsConfig, JsonObject>, Aliasable
{
    enum Kind
    {
        BINDING,
        EXPORTER,
        VAULT,
        GUARD,
        CATALOG
    }

    Kind kind();

    String type();

    @Override
    JsonObject adaptToJson(
        OptionsConfig options);

    @Override
    OptionsConfig adaptFromJson(
        JsonObject object);

    default void adaptContext(
        ConfigAdapterContext context)
    {
    }
}
