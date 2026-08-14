/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.model.core.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.ValidateConfig;
import io.aklivity.zilla.config.engine.ValidateMode;
import io.aklivity.zilla.config.model.core.BytesModelConfig;

public class BytesModelConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new BytesModelConfigAdapter(List.of()));
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadDefaultConfig()
    {
        // GIVEN
        String json = "\"bytes\"";

        // WHEN
        BytesModelConfig model = jsonb.fromJson(json, BytesModelConfig.class);

        // THEN
        assertThat(model, not(nullValue()));
        assertThat(model.model, equalTo("bytes"));
    }

    @Test
    public void shouldWriteDefaultConfig()
    {
        // GIVEN
        String expectedJson = "\"bytes\"";
        BytesModelConfig model = BytesModelConfig.builder().build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldDefaultValidateStrictWhenAbsent()
    {
        // GIVEN
        String json = "\"bytes\"";

        // WHEN
        BytesModelConfig model = jsonb.fromJson(json, BytesModelConfig.class);

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
                "model": "bytes",
                "validate": "lenient"
            }""";

        // WHEN
        BytesModelConfig model = jsonb.fromJson(json, BytesModelConfig.class);

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
                "model": "bytes",
                "validate":
                {
                    "decode": "lenient",
                    "encode": "strict"
                }
            }""";

        // WHEN
        BytesModelConfig model = jsonb.fromJson(json, BytesModelConfig.class);

        // THEN
        assertThat(model.validate.decode, equalTo(ValidateMode.LENIENT));
        assertThat(model.validate.encode, equalTo(ValidateMode.STRICT));
    }

    @Test
    public void shouldWriteScalarValidate()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"model\":\"bytes\"," +
                "\"validate\":\"lenient\"" +
            "}";
        BytesModelConfig model = BytesModelConfig.builder()
            .validate(ValidateConfig.builder().decode(ValidateMode.LENIENT).encode(ValidateMode.LENIENT).build())
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldWriteObjectValidate()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"model\":\"bytes\"," +
                "\"validate\":" +
                "{" +
                    "\"decode\":\"lenient\"," +
                    "\"encode\":\"strict\"" +
                "}" +
            "}";
        BytesModelConfig model = BytesModelConfig.builder()
            .validate(ValidateConfig.builder().decode(ValidateMode.LENIENT).encode(ValidateMode.STRICT).build())
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldWriteBareStringWhenValidateStrict()
    {
        // GIVEN
        String expectedJson = "\"bytes\"";
        BytesModelConfig model = BytesModelConfig.builder()
            .validate(ValidateConfig.builder().decode(ValidateMode.STRICT).encode(ValidateMode.STRICT).build())
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, equalTo(expectedJson));
        assertThat(json, not(containsString("validate")));
    }
}
