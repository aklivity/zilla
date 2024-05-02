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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

public class StringModelConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new StringModelConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadString()
    {
        // GIVEN
        String json =
            "{" +
                "\"model\": \"string\"," +
                "\"encoding\": \"utf_8\"," +
                "\"pattern\": \"^[a-zA-Z]\"," +
                "\"maxLength\": 5," +
                "\"minLength\": 2" +
            "}";

        // WHEN
        StringModelConfig model = jsonb.fromJson(json, StringModelConfig.class);

        // THEN
        assertThat(model, not(nullValue()));
        assertThat(model.model, equalTo("string"));
        assertThat(model.encoding, equalTo("utf_8"));
        assertThat(model.pattern, equalTo("^[a-zA-Z]"));
        assertThat(model.maxLength, equalTo(5));
        assertThat(model.minLength, equalTo(2));
    }

    @Test
    public void shouldWriteString()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"model\":\"string\"," +
                "\"encoding\":\"utf_16\"," +
                "\"pattern\":\"^[a-zA-Z]\"," +
                "\"maxLength\":5," +
                "\"minLength\":2" +
            "}";
        StringModelConfig model = StringModelConfig.builder()
            .encoding("utf_16")
            .pattern("^[a-zA-Z]")
            .maxLength(5)
            .minLength(2)
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldWriteDefaultConfig()
    {
        // GIVEN
        String expectedJson = "\"string\"";
        StringModelConfig model = StringModelConfig.builder()
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldReadDefaultConfig()
    {
        // GIVEN
        String json = "string";

        // WHEN
        StringModelConfig model = jsonb.fromJson(json, StringModelConfig.class);

        // THEN
        assertThat(model, not(nullValue()));
        assertThat(model.model, equalTo("string"));
        assertThat(model.encoding, equalTo("utf_8"));
        assertThat(model.maxLength, equalTo(0));
        assertThat(model.minLength, equalTo(0));
    }
}
