/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.model.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class ModelConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        ModelConfigAdapter adapter = new ModelConfigAdapter();
        adapter.adaptType("test");
        JsonbConfig config = new JsonbConfig()
            .withAdapters(adapter);
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadConverter()
    {
        // GIVEN
        String json =
            "{" +
                "\"model\": \"test\"" +
            "}";

        // WHEN
        ModelConfig model = jsonb.fromJson(json, ModelConfig.class);

        // THEN
        assertThat(model, not(nullValue()));
        assertThat(model.model, equalTo("test"));
    }

    @Test
    public void shouldWriteConverter()
    {
        // GIVEN
        String expectedJson = "\"test\"";
        ModelConfig model = TestModelConfig.builder().build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }
}
