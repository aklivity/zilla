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

        String text = yaml.write(config);

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
}
