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
package io.aklivity.zilla.runtime.validator.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class StringValidatorConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new StringValidatorConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadStringValidator()
    {
        // GIVEN
        String json =
            "{" +
                "\"type\": \"string\"," +
                "\"encoding\": \"utf_8\"" +
            "}";

        // WHEN
        StringValidatorConfig validator = jsonb.fromJson(json, StringValidatorConfig.class);

        // THEN
        assertThat(validator, not(nullValue()));
        assertThat(validator.type, equalTo("string"));
        assertThat(validator.encoding, equalTo("utf_8"));
    }

    @Test
    public void shouldWriteDefaultEncodingStringValidator()
    {
        // GIVEN
        String expectedJson = "\"string\"";
        StringValidatorConfig validator = StringValidatorConfig.builder().build();

        // WHEN
        String json = jsonb.toJson(validator);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldWriteStringValidator()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"type\":\"string\"," +
                "\"encoding\":\"utf_16\"" +
            "}";
        StringValidatorConfig validator = StringValidatorConfig.builder()
            .encoding("utf_16")
            .build();

        // WHEN
        String json = jsonb.toJson(validator);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }
}
