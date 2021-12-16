/*
 * Copyright 2021-2021 Aklivity Inc.
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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.Options;
import io.aklivity.zilla.runtime.engine.config.OptionsAdapterSpi;

public class OptionsAdapterTest
{
    private OptionsAdapter adapter;
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        adapter = new OptionsAdapter(OptionsAdapterSpi.Kind.BINDING);
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

        TestOptions options = (TestOptions) jsonb.fromJson(text, Options.class);

        assertThat(options, not(nullValue()));
        assertThat(options.mode, equalTo("test"));
    }

    @Test
    public void shouldWriteOptions()
    {
        Options options = new TestOptions("test");

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
        TestOptions options = (TestOptions) jsonb.fromJson(text, Options.class);

        assertThat(options, nullValue());
    }

    @Test
    public void shouldWriteNullWhenNotAdapting()
    {
        Options options = new TestOptions("test");

        adapter.adaptType(null);
        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("null"));
    }

    public static final class TestOptions extends Options
    {
        public final String mode;

        public TestOptions(
            String mode)
        {
            this.mode = mode;
        }
    }

    public static final class TestOptionsAdapter implements OptionsAdapterSpi
    {
        private static final String MODE_NAME = "mode";

        @Override
        public String type()
        {
            return "test";
        }

        @Override
        public JsonObject adaptToJson(
            Options options)
        {
            TestOptions testOptions = (TestOptions) options;

            JsonObjectBuilder object = Json.createObjectBuilder();

            object.add(MODE_NAME, testOptions.mode);

            return object.build();
        }

        @Override
        public Options adaptFromJson(
            JsonObject object)
        {
            String mode = object.getString(MODE_NAME);

            return new TestOptions(mode);
        }
    }
}
