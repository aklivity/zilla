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

import java.util.Locale;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

public final class ValidateConfigAdapter
{
    private static final String VALIDATE_NAME = "validate";
    private static final String DECODE_NAME = "decode";
    private static final String ENCODE_NAME = "encode";

    public JsonValue adaptToJson(
        ValidateConfig validate)
    {
        JsonValue result = null;

        if (validate != null &&
            !(validate.decode == ValidateMode.STRICT && validate.encode == ValidateMode.STRICT))
        {
            if (validate.decode == validate.encode)
            {
                result = Json.createValue(asString(validate.decode));
            }
            else
            {
                result = Json.createObjectBuilder()
                    .add(DECODE_NAME, asString(validate.decode))
                    .add(ENCODE_NAME, asString(validate.encode))
                    .build();
            }
        }

        return result;
    }

    public ValidateConfig adaptFromJson(
        JsonValue value)
    {
        ValidateConfig validate;

        if (value instanceof JsonString)
        {
            ValidateMode mode = asMode(((JsonString) value).getString());
            validate = new ValidateConfig(mode, mode);
        }
        else if (value instanceof JsonObject)
        {
            JsonObject object = (JsonObject) value;
            ValidateMode decode = object.containsKey(DECODE_NAME)
                ? asMode(object.getString(DECODE_NAME))
                : ValidateMode.STRICT;
            ValidateMode encode = object.containsKey(ENCODE_NAME)
                ? asMode(object.getString(ENCODE_NAME))
                : ValidateMode.STRICT;
            validate = new ValidateConfig(decode, encode);
        }
        else
        {
            validate = ValidateConfig.STRICT;
        }

        return validate;
    }

    public ValidateConfig adaptFromJsonObject(
        JsonObject object)
    {
        return object.containsKey(VALIDATE_NAME)
            ? adaptFromJson(object.get(VALIDATE_NAME))
            : ValidateConfig.STRICT;
    }

    private static String asString(
        ValidateMode mode)
    {
        return mode.name().toLowerCase(Locale.US);
    }

    private static ValidateMode asMode(
        String value)
    {
        return ValidateMode.valueOf(value.toUpperCase(Locale.US));
    }
}
