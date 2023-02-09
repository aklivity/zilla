/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.specs.guard.jwt.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.junit.Rule;
import org.junit.Test;

import io.aklivity.zilla.specs.engine.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/guard/jwt/schema/jwt.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/guard/jwt/config");

    @Test
    public void shouldValidateGuard()
    {
        JsonObject config = schema.validate("guard.json");

        assertThat(config, not(nullValue()));
        JsonValue value = config.getValue("/guards/jwt0/options/keys");
        assertThat(value, is(instanceOf(JsonArray.class)));
    }

    @Test
    public void shouldValidateGuardWithDynamicKeys()
    {
        JsonObject config = schema.validate("guard-keys-dynamic.json");

        assertThat(config, not(nullValue()));
        JsonValue value = config.getValue("/guards/jwt0/options/keys");
        assertThat(value, is(instanceOf(JsonString.class)));

    }

    @Test
    public void shouldValidateGuardWithImplicitKeys()
    {
        JsonObject config = schema.validate("guard-keys-implicit.json");

        assertThat(config, not(nullValue()));
        JsonObject optionsConfig = (JsonObject) config.getValue("/guards/jwt0/options");
        assertThat(optionsConfig.containsKey("keys"), is(false));
    }
}
