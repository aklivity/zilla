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
package io.aklivity.zilla.runtime.engine.internal.validator.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfigAdapterSpi;

public class IntegerValidatorConfigAdapter implements ValidatorConfigAdapterSpi, JsonbAdapter<ValidatorConfig, JsonObject>
{
    private static final String TYPE_NAME = "type";

    @Override
    public String type()
    {
        return "integer";
    }

    @Override
    public JsonObject adaptToJson(
        ValidatorConfig options)
    {
        JsonObjectBuilder validator = Json.createObjectBuilder();
        validator.add(TYPE_NAME, type());
        return validator.build();
    }

    @Override
    public ValidatorConfig adaptFromJson(
        JsonObject object)
    {
        return new IntegerValidatorConfig();
    }
}
