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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.model.core.config.Int64ModelConfig;

public class Int64ModelConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new Int64ModelConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadInt64()
    {
        // GIVEN
        String json =
            "{" +
                "\"model\":\"int64\"," +
                "\"format\":\"text\"," +
                "\"range\":\"[-999,999)\"," +
                "\"multiple\":100" +
            "}";

        // WHEN
        Int64ModelConfig model = jsonb.fromJson(json, Int64ModelConfig.class);

        // THEN
        assertThat(model, not(nullValue()));
        assertThat(model.model, equalTo("int64"));
        assertThat(model.format, equalTo("text"));
        assertThat(model.max, equalTo(999L));
        assertThat(model.min, equalTo(-999L));
        assertTrue(model.exclusiveMax);
        assertFalse(model.exclusiveMin);
        assertThat(model.multiple, equalTo(100L));
    }

    @Test
    public void shouldWriteInt64Default()
    {
        // GIVEN
        String expectedJson = "\"int64\"";
        Int64ModelConfig model = Int64ModelConfig.builder().build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldWriteInt64()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"model\":\"int64\"," +
                "\"range\":\"(,1234]\"" +
            "}";
        Int64ModelConfig model = Int64ModelConfig.builder()
            .max(1234L)
            .exclusiveMin(true)
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }
}
