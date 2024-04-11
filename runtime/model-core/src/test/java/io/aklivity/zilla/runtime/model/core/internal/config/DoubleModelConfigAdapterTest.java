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

import io.aklivity.zilla.runtime.model.core.config.DoubleModelConfig;

public class DoubleModelConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new DoubleModelConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldRead()
    {
        // GIVEN
        String json =
            "{" +
                "\"model\":\"double\"," +
                "\"format\":\"text\"," +
                "\"range\":\"[-999.98,999.99)\"," +
                "\"multiple\":100" +
            "}";

        // WHEN
        DoubleModelConfig model = jsonb.fromJson(json, DoubleModelConfig.class);

        // THEN
        assertThat(model, not(nullValue()));
        assertThat(model.model, equalTo("double"));
        assertThat(model.format, equalTo("text"));
        assertThat(model.max, equalTo(999.99));
        assertThat(model.min, equalTo(-999.98));
        assertTrue(model.exclusiveMax);
        assertFalse(model.exclusiveMin);
        assertThat(model.multiple, equalTo(100.0));
    }

    @Test
    public void shouldWriteDefault()
    {
        // GIVEN
        String expectedJson = "\"double\"";

        DoubleModelConfig model = DoubleModelConfig.builder().build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }
}
