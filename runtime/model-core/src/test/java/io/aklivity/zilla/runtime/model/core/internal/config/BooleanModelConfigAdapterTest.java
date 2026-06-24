/*
 * Copyright 2021-2024 Aklivity Inc
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.ValidateConfig;
import io.aklivity.zilla.runtime.engine.config.ValidateMode;
import io.aklivity.zilla.runtime.model.core.config.BooleanModelConfig;

public class BooleanModelConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new BooleanModelConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldRead()
    {
        // GIVEN
        String json = """
            {
                "model": "boolean"
            }""";

        // WHEN
        BooleanModelConfig model = jsonb.fromJson(json, BooleanModelConfig.class);

        // THEN
        assertThat(model, not(nullValue()));
        assertThat(model.model, equalTo("boolean"));
    }

    @Test
    public void shouldWriteDefault()
    {
        // GIVEN
        String expected = "\"boolean\"";

        BooleanModelConfig model = BooleanModelConfig.builder().build();

        // WHEN
        String actual = jsonb.toJson(model);

        // THEN
        assertThat(actual, not(nullValue()));
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void shouldDefaultValidateStrictWhenAbsent()
    {
        // GIVEN
        String json = """
            {
                "model": "boolean"
            }""";

        // WHEN
        BooleanModelConfig model = jsonb.fromJson(json, BooleanModelConfig.class);

        // THEN
        assertThat(model.validate, not(nullValue()));
        assertThat(model.validate.decode, equalTo(ValidateMode.STRICT));
        assertThat(model.validate.encode, equalTo(ValidateMode.STRICT));
    }

    @Test
    public void shouldReadScalarValidate()
    {
        // GIVEN
        String json = """
            {
                "model": "boolean",
                "validate": "lenient"
            }""";

        // WHEN
        BooleanModelConfig model = jsonb.fromJson(json, BooleanModelConfig.class);

        // THEN
        assertThat(model.validate.decode, equalTo(ValidateMode.LENIENT));
        assertThat(model.validate.encode, equalTo(ValidateMode.LENIENT));
    }

    @Test
    public void shouldReadObjectValidate()
    {
        // GIVEN
        String json = """
            {
                "model": "boolean",
                "validate":
                {
                    "decode": "lenient",
                    "encode": "strict"
                }
            }""";

        // WHEN
        BooleanModelConfig model = jsonb.fromJson(json, BooleanModelConfig.class);

        // THEN
        assertThat(model.validate.decode, equalTo(ValidateMode.LENIENT));
        assertThat(model.validate.encode, equalTo(ValidateMode.STRICT));
    }

    @Test
    public void shouldWriteScalarValidate()
    {
        // GIVEN
        String expected =
            "{" +
                "\"model\":\"boolean\"," +
                "\"validate\":\"lenient\"" +
            "}";
        BooleanModelConfig model = BooleanModelConfig.builder()
            .validate(new ValidateConfig(ValidateMode.LENIENT, ValidateMode.LENIENT))
            .build();

        // WHEN
        String actual = jsonb.toJson(model);

        // THEN
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void shouldWriteBareStringWhenValidateStrict()
    {
        // GIVEN
        String expected = "\"boolean\"";
        BooleanModelConfig model = BooleanModelConfig.builder()
            .validate(new ValidateConfig(ValidateMode.STRICT, ValidateMode.STRICT))
            .build();

        // WHEN
        String actual = jsonb.toJson(model);

        // THEN
        assertThat(actual, equalTo(expected));
        assertThat(actual, not(containsString("validate")));
    }
}
