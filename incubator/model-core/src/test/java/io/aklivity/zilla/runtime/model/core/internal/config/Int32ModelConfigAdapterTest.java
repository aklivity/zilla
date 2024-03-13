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

import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;

public class Int32ModelConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new Int32ModelConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadIntegerconverter()
    {
        // GIVEN
        String json =
            "{" +
                "\"model\":\"int32\"," +
                "\"format\":\"text\"," +
                "\"max\":999," +
                "\"min\":-999," +
                "\"exclusiveMax\":true," +
                "\"exclusiveMin\":false," +
                "\"multiple\":100" +
            "}";

        // WHEN
        Int32ModelConfig model = jsonb.fromJson(json, Int32ModelConfig.class);

        // THEN
        assertThat(model, not(nullValue()));
        assertThat(model.model, equalTo("int32"));
        assertThat(model.format, equalTo("text"));
        assertThat(model.max, equalTo(999));
        assertThat(model.min, equalTo(-999));
        assertTrue(model.exclusiveMax);
        assertFalse(model.exclusiveMin);
        assertThat(model.multiple, equalTo(100));
    }

    @Test
    public void shouldWriteIntegerconverter()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"model\":\"int32\"" +
            "}";
        Int32ModelConfig model = Int32ModelConfig.builder().build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }
}
