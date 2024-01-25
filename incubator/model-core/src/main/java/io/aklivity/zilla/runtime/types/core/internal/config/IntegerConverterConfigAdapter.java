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
package io.aklivity.zilla.runtime.types.core.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ConverterConfig;
import io.aklivity.zilla.runtime.engine.config.ConverterConfigAdapterSpi;
import io.aklivity.zilla.runtime.types.core.config.IntegerConverterConfig;

public class IntegerConverterConfigAdapter implements ConverterConfigAdapterSpi, JsonbAdapter<ConverterConfig, JsonValue>
{
    @Override
    public String type()
    {
        return "integer";
    }

    @Override
    public JsonValue adaptToJson(
        ConverterConfig options)
    {
        return Json.createValue(type());
    }

    @Override
    public ConverterConfig adaptFromJson(
        JsonValue object)
    {
        return new IntegerConverterConfig();
    }
}
