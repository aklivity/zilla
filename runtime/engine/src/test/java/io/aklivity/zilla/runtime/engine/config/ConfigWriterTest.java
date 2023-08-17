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
package io.aklivity.zilla.runtime.engine.config;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonPatch;
import jakarta.json.spi.JsonProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import io.aklivity.zilla.runtime.engine.internal.config.ConditionConfigAdapterTest.TestConditionConfig;
import io.aklivity.zilla.runtime.engine.test.internal.binding.config.TestBindingOptionsConfig;

public class ConfigWriterTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private ConfigAdapterContext context;

    private ConfigWriter yaml;

    @Before
    public void initYaml()
    {
        yaml = new ConfigWriter(context);
    }

    @Test
    public void shouldWriteNamespace()
    {
        // GIVEN
        NamespaceConfig config = NamespaceConfig.builder()
                .name("test")
                .binding()
                    .inject(identity())
                    .name("test0")
                    .type("test")
                    .kind(SERVER)
                    .options(TestBindingOptionsConfig::builder)
                        .inject(identity())
                        .mode("test")
                        .build()
                    .route()
                        .inject(identity())
                        .when(TestConditionConfig::builder)
                            .inject(identity())
                            .match("test")
                            .build()
                        .exit("exit0")
                        .build()
                    .build()
                .build();

        // WHEN
        String text = yaml.write(config);

        // THEN
        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(String.join("\n",
            new String[] {
                "name: test",
                "bindings:",
                "  test0:",
                "    type: test",
                "    kind: server",
                "    options:",
                "      mode: test",
                "    routes:",
                "    - exit: exit0",
                "      when:",
                "      - match: test",
                ""
            })));
    }

    @Test
    public void shouldPatchAndWriteNamespace()
    {
        // GIVEN
        NamespaceConfig config = NamespaceConfig.builder()
                .name("test")
                .binding()
                    .name("test0")
                    .type("test")
                    .kind(SERVER)
                    .options(TestBindingOptionsConfig::builder)
                        .mode("test")
                        .build()
                    .route()
                        .when(TestConditionConfig::builder)
                            .match("test")
                            .build()
                        .exit("exit0")
                        .build()
                    .build()
                .build();
        JsonArray ops = Json.createArrayBuilder()
            .add(Json.createObjectBuilder()
                .add("op", "replace")
                .add("path", "/bindings/test0/type")
                .add("value", "newType")
                .build())
            .build();
        JsonPatch patch = JsonProvider.provider().createPatch(ops);

        // WHEN
        String text = yaml.write(config, patch, List.of());

        // THEN
        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(String.join("\n",
            new String[] {
                "name: test",
                "bindings:",
                "  test0:",
                "    type: newType",
                "    kind: server",
                "    options:",
                "      mode: test",
                "    routes:",
                "    - exit: exit0",
                "      when:",
                "      - match: test",
                ""
            })));
    }

    @Test
    public void shouldPatchAndUnquoteAndWriteNamespace()
    {
        // GIVEN
        NamespaceConfig config = NamespaceConfig.builder()
                .name("test")
                .binding()
                    .name("test0")
                    .type("test")
                    .kind(SERVER)
                    .options(TestBindingOptionsConfig::builder)
                        .mode("test")
                        .build()
                    .route()
                        .when(TestConditionConfig::builder)
                            .match("test")
                            .build()
                        .exit("exit0")
                        .build()
                    .build()
                .build();
        JsonArray ops = Json.createArrayBuilder()
            .add(Json.createObjectBuilder()
                .add("op", "replace")
                .add("path", "/bindings/test0/type")
                .add("value", "${{env.INTEGER}}")
                .build())
            .build();
        JsonPatch patch = JsonProvider.provider().createPatch(ops);

        // WHEN
        String text = yaml.write(config, patch, List.of("INTEGER"));

        // THEN
        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(String.join("\n",
            new String[] {
                "name: test",
                "bindings:",
                "  test0:",
                "    type: ${{env.INTEGER}}",
                "    kind: server",
                "    options:",
                "      mode: test",
                "    routes:",
                "    - exit: exit0",
                "      when:",
                "      - match: test",
                ""
            })));
    }
}
