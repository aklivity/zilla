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

import static io.aklivity.zilla.runtime.engine.config.Role.SERVER;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.NamespacedRef;
import io.aklivity.zilla.runtime.engine.config.Route;
import io.aklivity.zilla.runtime.engine.internal.config.OptionsAdapterTest.TestOptions;

public class BindingAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new BindingAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadBinding()
    {
        String text =
                "{" +
                    "\"type\": \"test\"," +
                    "\"kind\": \"server\"," +
                    "\"routes\":" +
                    "[" +
                    "]" +
                "}";

        Binding binding = jsonb.fromJson(text, Binding.class);

        assertThat(binding, not(nullValue()));
        assertThat(binding.kind, equalTo(SERVER));
        assertThat(binding.routes, emptyCollectionOf(Route.class));
    }

    @Test
    public void shouldWriteBinding()
    {
        Binding binding = new Binding(null, null, "test", SERVER, null, emptyList(), null);

        String text = jsonb.toJson(binding);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"type\":\"test\",\"kind\":\"server\"}"));
    }

    @Test
    public void shouldReadBindingWithVault()
    {
        String text =
                "{" +
                    "\"vault\": \"test\"," +
                    "\"type\": \"test\"," +
                    "\"kind\": \"server\"," +
                    "\"routes\":" +
                    "[" +
                    "]" +
                "}";

        Binding binding = jsonb.fromJson(text, Binding.class);

        assertThat(binding, not(nullValue()));
        assertThat(binding.vault, not(nullValue()));
        assertThat(binding.vault.name, equalTo("test"));
        assertThat(binding.kind, equalTo(SERVER));
        assertThat(binding.routes, emptyCollectionOf(Route.class));
    }

    @Test
    public void shouldWriteBindingWithVault()
    {
        NamespacedRef vault = new NamespacedRef("default", "test");
        Binding binding = new Binding(vault, null, "test", SERVER, null, emptyList(), null);

        String text = jsonb.toJson(binding);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"vault\":\"test\",\"type\":\"test\",\"kind\":\"server\"}"));
    }

    @Test
    public void shouldReadBindingWithEntry()
    {
        String text =
                "{" +
                    "\"entry\": \"test\"," +
                    "\"type\": \"test\"," +
                    "\"kind\": \"server\"," +
                    "\"routes\":" +
                    "[" +
                    "]" +
                "}";

        Binding binding = jsonb.fromJson(text, Binding.class);

        assertThat(binding, not(nullValue()));
        assertThat(binding.entry, equalTo("test"));
        assertThat(binding.kind, equalTo(SERVER));
        assertThat(binding.routes, emptyCollectionOf(Route.class));
    }

    @Test
    public void shouldWriteBindingWithEntry()
    {
        Binding binding = new Binding(null, "test", "test", SERVER, null, emptyList(), null);

        String text = jsonb.toJson(binding);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"entry\":\"test\",\"type\":\"test\",\"kind\":\"server\"}"));
    }

    @Test
    public void shouldReadBindingWithExit()
    {
        String text =
                "{" +
                    "\"entry\": \"test\"," +
                    "\"type\": \"test\"," +
                    "\"kind\": \"server\"," +
                    "\"routes\":" +
                    "[" +
                    "]," +
                    "\"exit\": \"test\"" +
                "}";

        Binding binding = jsonb.fromJson(text, Binding.class);

        assertThat(binding, not(nullValue()));
        assertThat(binding.entry, equalTo("test"));
        assertThat(binding.kind, equalTo(SERVER));
        assertThat(binding.routes, emptyCollectionOf(Route.class));
        assertThat(binding.exit, not(nullValue()));
        assertThat(binding.exit.exit, equalTo("test"));
    }

    @Test
    public void shouldWriteBindingWithExit()
    {
        Binding binding = new Binding(null, "test", "test", SERVER, null, emptyList(), new Route("test"));

        String text = jsonb.toJson(binding);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"entry\":\"test\",\"type\":\"test\",\"kind\":\"server\",\"exit\":\"test\"}"));
    }

    @Test
    public void shouldReadBindingWithOptions()
    {
        String text =
                "{" +
                    "\"entry\": \"test\"," +
                    "\"type\": \"test\"," +
                    "\"kind\": \"server\"," +
                    "\"options\":" +
                    "{" +
                        "\"mode\": \"test\"" +
                    "}" +
                "}";

        Binding binding = jsonb.fromJson(text, Binding.class);

        assertThat(binding, not(nullValue()));
        assertThat(binding.entry, equalTo("test"));
        assertThat(binding.kind, equalTo(SERVER));
        assertThat(binding.options, instanceOf(TestOptions.class));
        assertThat(((TestOptions) binding.options).mode, equalTo("test"));
    }

    @Test
    public void shouldWriteBindingWithOptions()
    {
        Binding binding = new Binding(null, null, "test", SERVER, new TestOptions("test"), emptyList(), null);

        String text = jsonb.toJson(binding);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"type\":\"test\",\"kind\":\"server\",\"options\":{\"mode\":\"test\"}}"));
    }

    @Test
    public void shouldReadBindingWithRoute()
    {
        String text =
                "{" +
                    "\"entry\": \"test\"," +
                    "\"type\": \"test\"," +
                    "\"kind\": \"server\"," +
                    "\"routes\":" +
                    "[" +
                        "{" +
                            "\"exit\": \"test\"" +
                        "}" +
                    "]" +
                "}";

        Binding binding = jsonb.fromJson(text, Binding.class);

        assertThat(binding, not(nullValue()));
        assertThat(binding.entry, equalTo("test"));
        assertThat(binding.kind, equalTo(SERVER));
        assertThat(binding.routes, hasSize(1));
        assertThat(binding.routes.get(0).exit, equalTo("test"));
        assertThat(binding.routes.get(0).when, empty());
    }

    @Test
    public void shouldWriteBindingWithRoute()
    {
        Binding binding = new Binding(null, null, "test", SERVER, null, singletonList(new Route("test")), null);

        String text = jsonb.toJson(binding);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"type\":\"test\",\"kind\":\"server\",\"routes\":[{\"exit\":\"test\"}]}"));
    }

}
