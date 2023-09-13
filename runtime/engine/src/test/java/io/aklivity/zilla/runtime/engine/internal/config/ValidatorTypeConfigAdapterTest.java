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
package io.aklivity.zilla.runtime.engine.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.LinkedHashMap;
import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.config.ValidatorTypeConfig;

public class ValidatorTypeConfigAdapterTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new ValidatorTypeAdapter(), new SchemaAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadShortSyntax()
    {
        // GIVEN
        String text = "string";

        // WHEN
        ValidatorTypeConfig validatorType = jsonb.fromJson(text, ValidatorTypeConfig.class);

        // THEN
        assertThat(validatorType, not(nullValue()));
        assertThat(validatorType.type, equalTo(ValidatorTypeConfig.Type.STRING));
    }

    @Test
    public void shouldReadFullSyntax()
    {
        // GIVEN
        String text =
            "{" +
                "\"type\": \"json\", " +
                "\"catalog\": " +
                "{" +
                    "\"catalog0\": " +
                    "[" +
                        "{" +
                            "\"schema\": \"cat\"" +
                        "}," +
                        "{" +
                            "\"schema\": \"tiger\", " +
                            "\"version\": \"jungle\"" +
                        "}," +
                        "{" +
                            "\"strategy\": \"random\", " +
                            "\"version\": \"latest\"" +
                        "}," +
                        "{" +
                            "\"id\": 42" +
                        "}" +
                    "]," +
                    "\"catalog1\": " +
                    "[" +
                        "{" +
                            "\"schema\": \"dog\"," +
                            "\"version\": \"labrador\"" +
                        "}" +
                    "]" +
                "}" +
            "}";

        // WHEN
        ValidatorTypeConfig validatorType = jsonb.fromJson(text, ValidatorTypeConfig.class);

        // THEN
        assertThat(validatorType, not(nullValue()));
        assertThat(validatorType.type, equalTo(ValidatorTypeConfig.Type.JSON));
        assertThat(validatorType.catalog.keySet().size(), equalTo(2));
        assertThat(validatorType.catalog.get("catalog0").get(0).schema, equalTo("cat"));
        assertThat(validatorType.catalog.get("catalog0").get(1).schema, equalTo("tiger"));
        assertThat(validatorType.catalog.get("catalog0").get(1).version, equalTo("jungle"));
        assertThat(validatorType.catalog.get("catalog0").get(2).strategy, equalTo("random"));
        assertThat(validatorType.catalog.get("catalog0").get(2).version, equalTo("latest"));
        assertThat(validatorType.catalog.get("catalog0").get(3).id, equalTo(42));
        assertThat(validatorType.catalog.get("catalog1").get(0).schema, equalTo("dog"));
        assertThat(validatorType.catalog.get("catalog1").get(0).version, equalTo("labrador"));
    }

    @Test
    public void shouldWriteShortSyntax()
    {
        // GIVEN
        String expectedJson = "\"string\"";
        ValidatorTypeConfig validatorType = new ValidatorTypeConfig(ValidatorTypeConfig.Type.STRING, null);

        // WHEN
        String json = jsonb.toJson(validatorType);

        // THEN
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldWriteFullSyntax()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"type\":\"json\"," +
                "\"catalog\":" +
                "{" +
                    "\"catalog0\":" +
                    "[" +
                        "{" +
                            "\"schema\":\"cat\"" +
                        "}," +
                        "{" +
                            "\"schema\":\"tiger\"," +
                            "\"version\":\"jungle\"" +
                        "}," +
                        "{" +
                            "\"strategy\":\"random\"," +
                            "\"version\":\"latest\"" +
                        "}," +
                        "{" +
                            "\"id\":42" +
                        "}" +
                    "]," +
                    "\"catalog1\":" +
                    "[" +
                        "{" +
                            "\"schema\":\"dog\"," +
                            "\"version\":\"labrador\"" +
                        "}" +
                    "]" +
                "}" +
            "}";
        LinkedHashMap<String, List<SchemaConfig>> catalog = new LinkedHashMap<>();
        catalog.put("catalog0", List.of(
            new SchemaConfig("cat", null, null, 0),
            new SchemaConfig("tiger", null, "jungle", 0),
            new SchemaConfig(null, "random", "latest", 0),
            new SchemaConfig(null, null, null, 42)
        ));
        catalog.put("catalog1", List.of(
            new SchemaConfig("dog", null, "labrador", 0)
        ));
        ValidatorTypeConfig validatorType = new ValidatorTypeConfig(ValidatorTypeConfig.Type.JSON, catalog);

        // WHEN
        String json = jsonb.toJson(validatorType);

        // THEN
        assertThat(json, equalTo(expectedJson));
    }
}
