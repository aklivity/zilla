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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class OptionsConfigAdapterTest
{
    private OptionsAdapter adapter;
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        adapter = new OptionsAdapter(OptionsConfigAdapterSpi.Kind.BINDING);
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

        TestOptionsConfig options = (TestOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.mode, equalTo("test"));
    }

    @Test
    public void shouldWriteOptions()
    {
        OptionsConfig options = new TestOptionsConfig("test");

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
        TestOptionsConfig options = (TestOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options, nullValue());
    }

    @Test
    public void shouldWriteNullWhenNotAdapting()
    {
        OptionsConfig options = new TestOptionsConfig("test");

        adapter.adaptType(null);
        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("null"));
    }

    public static final class TestOptionsConfig extends OptionsConfig
    {
        public final String mode;

        public TestOptionsConfig(
            String mode)
        {
            this.mode = mode;
        }
    }

    public static final class TestOptionsConfigAdapter implements OptionsConfigAdapterSpi
    {
        private static final String MODE_NAME = "mode";

        @Override
        public Kind kind()
        {
            return Kind.BINDING;
        }

        @Override
        public String type()
        {
            return "test";
        }

        @Override
        public JsonObject adaptToJson(
            OptionsConfig options)
        {
            TestOptionsConfig testOptions = (TestOptionsConfig) options;

            JsonObjectBuilder object = Json.createObjectBuilder();

            object.add(MODE_NAME, testOptions.mode);

            return object.build();
        }

        @Override
        public OptionsConfig adaptFromJson(
            JsonObject object)
        {
            String mode = object.getString(MODE_NAME);

            return new TestOptionsConfig(mode);
        }
    }
}
