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
package io.aklivity.zilla.config.model.vector.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.model.vector.VectorModelConfig;

public class VectorModelConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new VectorModelConfigAdapter(List.of()));
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadVectorModel()
    {
        // GIVEN
        String json = """
            {
                "model": "vector",
                "embedding": "moderator0",
                "reject":
                [
                    "reject phrase one",
                    "reject phrase two"
                ],
                "threshold": 0.85
            }""";

        // WHEN
        VectorModelConfig config = jsonb.fromJson(json, VectorModelConfig.class);

        // THEN
        assertThat(config, not(nullValue()));
        assertThat(config.model, equalTo("vector"));
        assertThat(config.embedding.name, equalTo("moderator0"));
        assertThat(config.reject, contains("reject phrase one", "reject phrase two"));
        assertThat(config.threshold, equalTo(0.85));
    }

    @Test
    public void shouldWriteVectorModel()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"model\":\"vector\"," +
                "\"embedding\":\"moderator0\"," +
                "\"reject\":" +
                "[" +
                    "\"reject phrase one\"," +
                    "\"reject phrase two\"" +
                "]," +
                "\"threshold\":0.85" +
            "}";
        VectorModelConfig config = VectorModelConfig.builder()
            .embedding("moderator0")
            .reject("reject phrase one")
            .reject("reject phrase two")
            .threshold(0.85)
            .build();

        // WHEN
        String json = jsonb.toJson(config);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }
}
