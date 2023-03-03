/*
 * Copyright 2021-2022 Aklivity Inc.
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

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.test.internal.binding.config.TestBindingOptionsConfig;

public class OptionsConfigAdapterTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private ConfigAdapterContext context;

    private OptionsAdapter adapter;
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        adapter = new OptionsAdapter(OptionsConfigAdapterSpi.Kind.BINDING, context);
        adapter.adaptType("test");
        JsonbConfig config = new JsonbConfig()
                .withAdapters(adapter);
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"mode\": \"test\"" +
                "}";

        TestBindingOptionsConfig options = (TestBindingOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.mode, equalTo("test"));
    }

    @Test
    public void shouldWriteOptions()
    {
        OptionsConfig options = new TestBindingOptionsConfig("test");

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"mode\":\"test\"}"));
    }

    @Test
    public void shouldReadNullWhenNotAdapting()
    {
        String text =
                "{" +
                    "\"mode\": \"test\"" +
                "}";

        adapter.adaptType(null);
        TestBindingOptionsConfig options = (TestBindingOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options, nullValue());
    }

    @Test
    public void shouldWriteNullWhenNotAdapting()
    {
        OptionsConfig options = new TestBindingOptionsConfig("test");

        adapter.adaptType(null);
        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("null"));
    }
}
