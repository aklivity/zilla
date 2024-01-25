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
package io.aklivity.zilla.runtime.model.core.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfigAdapterSpi;
import io.aklivity.zilla.runtime.model.core.config.StringValidatorConfig;

public final class StringValidatorConfigAdapter implements ValidatorConfigAdapterSpi, JsonbAdapter<ValidatorConfig, JsonValue>
{
    private static final String TYPE_NAME = "type";
    private static final String ENCODING_NAME = "encoding";

    @Override
    public String type()
    {
        return "string";
    }

    @Override
    public JsonValue adaptToJson(
        ValidatorConfig config)
    {
        JsonValue result;
        String encoding = ((StringValidatorConfig) config).encoding;
        if (encoding != null && !encoding.isEmpty() && !encoding.equals(StringValidatorConfig.DEFAULT_ENCODING))
        {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add(TYPE_NAME, type());
            builder.add(ENCODING_NAME, encoding);
            result = builder.build();
        }
        else
        {
            result = Json.createValue("string");
        }
        return result;
    }

    @Override
    public StringValidatorConfig adaptFromJson(
        JsonValue value)
    {
        StringValidatorConfig config = null;
        switch (value.getValueType())
        {
        case STRING:
            config = StringValidatorConfig.builder().build();
            break;
        case OBJECT:
            JsonObject object = (JsonObject) value;
            String encoding = object.containsKey(ENCODING_NAME)
                    ? object.getString(ENCODING_NAME)
                    : null;
            config = StringValidatorConfig.builder()
                    .encoding(encoding)
                    .build();
            break;
        }
        return config;
    }
}
