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
package io.aklivity.zilla.config.model.avro.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.ConfigExtAdapter;
import io.aklivity.zilla.config.engine.ConfigExtBuilder;
import io.aklivity.zilla.config.engine.ValidateConfig;
import io.aklivity.zilla.config.engine.ValidateMode;
import io.aklivity.zilla.config.model.avro.AvroModelConfig;

public class AvroModelConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new AvroModelConfigAdapter(List.of()));
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadAvroconverter()
    {
        // GIVEN
        String json = """
            {
                "view": "json",
                "model": "avro",
                "catalog":
                {
                    "test0":
                    [
                        {
                            "strategy": "topic",
                            "version": "latest"
                        },
                        {
                            "subject": "cat",
                            "version": "latest"
                        },
                        {
                            "id": 42
                        }
                    ]
                }
            }""";

        // WHEN
        AvroModelConfig model = jsonb.fromJson(json, AvroModelConfig.class);

        // THEN
        assertThat(model, not(nullValue()));
        assertThat(model.view, equalTo("json"));
        assertThat(model.model, equalTo("avro"));
        assertThat(model.cataloged.size(), equalTo(1));
        assertThat(model.cataloged.get(0).name, equalTo("test0"));
        assertThat(model.cataloged.get(0).schemas.get(0).strategy, equalTo("topic"));
        assertThat(model.cataloged.get(0).schemas.get(0).version, equalTo("latest"));
        assertThat(model.cataloged.get(0).schemas.get(0).id, equalTo(0));
        assertThat(model.cataloged.get(0).schemas.get(1).subject, equalTo("cat"));
        assertThat(model.cataloged.get(0).schemas.get(1).strategy, nullValue());
        assertThat(model.cataloged.get(0).schemas.get(1).version, equalTo("latest"));
        assertThat(model.cataloged.get(0).schemas.get(1).id, equalTo(0));
        assertThat(model.cataloged.get(0).schemas.get(2).strategy, nullValue());
        assertThat(model.cataloged.get(0).schemas.get(2).version, nullValue());
        assertThat(model.cataloged.get(0).schemas.get(2).id, equalTo(42));
    }

    @Test
    public void shouldWriteAvroconverter()
    {
        // GIVEN
        String expectedJson =
            "{" +
                "\"view\":\"json\"," +
                "\"model\":\"avro\"," +
                "\"catalog\":" +
                "{" +
                    "\"test0\":" +
                    "[" +
                        "{" +
                            "\"strategy\":\"topic\"," +
                            "\"version\":\"latest\"" +
                        "}," +
                        "{" +
                            "\"subject\":\"cat\"," +
                            "\"version\":\"latest\"" +
                        "}," +
                        "{" +
                            "\"id\":42" +
                        "}" +
                    "]" +
                "}" +
            "}";
        AvroModelConfig model = AvroModelConfig.builder()
            .view("json")
            .catalog()
                .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .build()
                    .schema()
                        .subject("cat")
                        .version("latest")
                        .build()
                    .schema()
                        .id(42)
                        .build()
                    .build()
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, not(nullValue()));
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldDefaultValidateStrictWhenAbsent()
    {
        // GIVEN
        String json = """
            {
                "model": "avro",
                "catalog":
                {
                    "test0":
                    [
                        {
                            "subject": "cat",
                            "version": "latest"
                        }
                    ]
                }
            }""";

        // WHEN
        AvroModelConfig model = jsonb.fromJson(json, AvroModelConfig.class);

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
                "model": "avro",
                "validate": "lenient",
                "catalog":
                {
                    "test0":
                    [
                        {
                            "subject": "cat",
                            "version": "latest"
                        }
                    ]
                }
            }""";

        // WHEN
        AvroModelConfig model = jsonb.fromJson(json, AvroModelConfig.class);

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
                "model": "avro",
                "validate":
                {
                    "decode": "lenient",
                    "encode": "strict"
                },
                "catalog":
                {
                    "test0":
                    [
                        {
                            "subject": "cat",
                            "version": "latest"
                        }
                    ]
                }
            }""";

        // WHEN
        AvroModelConfig model = jsonb.fromJson(json, AvroModelConfig.class);

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
                "\"model\":\"avro\"," +
                "\"catalog\":" +
                "{" +
                    "\"test0\":" +
                    "[" +
                        "{" +
                            "\"subject\":\"cat\"," +
                            "\"version\":\"latest\"" +
                        "}" +
                    "]" +
                "}," +
                "\"validate\":\"lenient\"" +
            "}";
        AvroModelConfig model = AvroModelConfig.builder()
            .validate(ValidateConfig.builder().decode(ValidateMode.LENIENT).encode(ValidateMode.LENIENT).build())
            .catalog()
                .name("test0")
                    .schema()
                        .subject("cat")
                        .version("latest")
                        .build()
                    .build()
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
                "\"model\":\"avro\"," +
                "\"catalog\":" +
                "{" +
                    "\"test0\":" +
                    "[" +
                        "{" +
                            "\"subject\":\"cat\"," +
                            "\"version\":\"latest\"" +
                        "}" +
                    "]" +
                "}," +
                "\"validate\":" +
                "{" +
                    "\"decode\":\"lenient\"," +
                    "\"encode\":\"strict\"" +
                "}" +
            "}";
        AvroModelConfig model = AvroModelConfig.builder()
            .validate(ValidateConfig.builder().decode(ValidateMode.LENIENT).encode(ValidateMode.STRICT).build())
            .catalog()
                .name("test0")
                    .schema()
                        .subject("cat")
                        .version("latest")
                        .build()
                    .build()
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, equalTo(expectedJson));
    }

    @Test
    public void shouldOmitValidateWhenStrict()
    {
        // GIVEN
        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .subject("cat")
                        .version("latest")
                        .build()
                    .build()
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, not(containsString("validate")));
    }

    @Test
    public void shouldReadVault()
    {
        // GIVEN
        String json = """
            {
                "model": "avro",
                "catalog":
                {
                    "test0":
                    [
                        {
                            "subject": "cat",
                            "version": "latest"
                        }
                    ]
                },
                "vault":
                {
                    "vault0": {}
                }
            }""";

        // WHEN
        AvroModelConfig model = jsonb.fromJson(json, AvroModelConfig.class);

        // THEN
        assertThat(model.vaulted.size(), equalTo(1));
        assertThat(model.vaulted.get(0).name, equalTo("vault0"));
    }

    @Test
    public void shouldOmitVaultWhenAbsent()
    {
        // GIVEN
        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .subject("cat")
                        .version("latest")
                        .build()
                    .build()
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, not(containsString("vault")));
        assertThat(model.vaulted, equalTo(List.of()));
    }

    @Test
    public void shouldWriteVault()
    {
        // GIVEN
        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .subject("cat")
                        .version("latest")
                        .build()
                    .build()
            .vault()
                .name("vault0")
                .build()
            .build();

        // WHEN
        String json = jsonb.toJson(model);

        // THEN
        assertThat(json, containsString("\"vault\":{\"vault0\":{}}"));
    }

    @Test
    public void shouldRouteExtensionIntoVaultRatherThanModel()
    {
        // GIVEN
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new AvroModelConfigAdapter(
                List.of(new ConfigExtAdapter<>(Map.of(TestVaultExtConfig.NAME, new TestVaultExtConfigAdapter())))));
        Jsonb extJsonb = JsonbBuilder.create(config);

        String json = """
            {
                "model": "avro",
                "catalog":
                {
                    "test0":
                    [
                        {
                            "subject": "cat",
                            "version": "latest"
                        }
                    ]
                },
                "vault":
                {
                    "vault0":
                    {
                        "test-vault-ext":
                        {
                            "value": "value0"
                        }
                    }
                }
            }""";

        // WHEN
        AvroModelConfig model = extJsonb.fromJson(json, AvroModelConfig.class);

        // THEN
        assertThat(model.ext(TestVaultExtConfig.NAME, TestVaultExtConfig.class), nullValue());
        assertThat(model.vaulted.get(0).ext(TestVaultExtConfig.NAME, TestVaultExtConfig.class).value, equalTo("value0"));
    }

    @Test
    public void shouldWriteExtensionFromVaultRatherThanModel()
    {
        // GIVEN
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new AvroModelConfigAdapter(
                List.of(new ConfigExtAdapter<>(Map.of(TestVaultExtConfig.NAME, new TestVaultExtConfigAdapter())))));
        Jsonb extJsonb = JsonbBuilder.create(config);

        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .subject("cat")
                        .version("latest")
                        .build()
                    .build()
            .vault()
                .name("vault0")
                .ext(TestVaultExtConfigBuilder::new)
                    .value("value0")
                    .build()
                .build()
            .build();

        // WHEN
        String json = extJsonb.toJson(model);

        // THEN
        assertThat(json, containsString("\"vault\":{\"vault0\":{\"test-vault-ext\":{\"value\":\"value0\"}}}"));
    }

    // a minimal Config.Extensible-attachable fixture, local to this test, verifying that an extension
    // adapter discovered the same way disclosure's own is (ModelExtInfo, keyed by name) can still land on
    // a nested VaultedConfig rather than the model itself, since ConfigExtAdapter is builder-generic
    private static final class TestVaultExtConfig extends Config
    {
        private static final String NAME = "test-vault-ext";

        private final String value;

        private TestVaultExtConfig(
            String value)
        {
            this.value = value;
        }
    }

    private static final class TestVaultExtConfigAdapter extends ConfigAdapter<TestVaultExtConfig, JsonObject>
    {
        @Override
        public JsonObject adaptToJson(
            TestVaultExtConfig config)
        {
            return Json.createObjectBuilder().add("value", config.value).build();
        }

        @Override
        public TestVaultExtConfig adaptFromJson(
            JsonObject object)
        {
            return new TestVaultExtConfig(object.getString("value"));
        }
    }

    private static final class TestVaultExtConfigBuilder<B> extends ConfigExtBuilder<B>
    {
        private String value;

        TestVaultExtConfigBuilder(
            BiFunction<String, Config, B> mapper)
        {
            super(mapper);
        }

        private TestVaultExtConfigBuilder<B> value(
            String value)
        {
            this.value = value;
            return this;
        }

        @Override
        public B build()
        {
            return mapper.apply(TestVaultExtConfig.NAME, new TestVaultExtConfig(value));
        }
    }
}
