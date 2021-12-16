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
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.Vault;

public class ConfigurationAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new ConfigurationAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadEmptyConfiguration()
    {
        String text =
                "{" +
                "}";

        Configuration config = jsonb.fromJson(text, Configuration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.name, equalTo("default"));
        assertThat(config.bindings, emptyCollectionOf(Binding.class));
        assertThat(config.namespaces, emptyCollectionOf(NamespaceRef.class));
    }

    @Test
    public void shouldWriteEmptyConfiguration()
    {
        Configuration config = new Configuration("default", emptyList(), emptyList(), emptyList());

        String text = jsonb.toJson(config);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{}"));
    }

    @Test
    public void shouldReadConfiguration()
    {
        String text =
                "{" +
                    "\"name\": \"test\"," +
                    "\"bindings\":" +
                    "[" +
                    "]," +
                    "\"vaults\":" +
                    "[" +
                    "]," +
                    "\"namespaces\":" +
                    "[" +
                    "]" +
                "}";

        Configuration config = jsonb.fromJson(text, Configuration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.name, equalTo("test"));
        assertThat(config.bindings, emptyCollectionOf(Binding.class));
        assertThat(config.namespaces, emptyCollectionOf(NamespaceRef.class));
    }

    @Test
    public void shouldWriteConfiguration()
    {
        Configuration config = new Configuration("test", emptyList(), emptyList(), emptyList());

        String text = jsonb.toJson(config);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\"}"));
    }

    @Test
    public void shouldReadConfigurationWithBinding()
    {
        String text =
                "{" +
                    "\"name\": \"test\"," +
                    "\"bindings\":" +
                    "[" +
                        "{" +
                            "\"type\": \"test\"," +
                            "\"kind\": \"server\"" +
                        "}" +
                    "]," +
                    "\"vaults\":" +
                    "[" +
                    "]," +
                    "\"namespaces\":" +
                    "[" +
                    "]" +
                "}";

        Configuration config = jsonb.fromJson(text, Configuration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.name, equalTo("test"));
        assertThat(config.bindings, hasSize(1));
        assertThat(config.bindings.get(0).type, equalTo("test"));
        assertThat(config.bindings.get(0).kind, equalTo(SERVER));
        assertThat(config.namespaces, emptyCollectionOf(NamespaceRef.class));
    }

    @Test
    public void shouldWriteConfigurationWithBinding()
    {
        Binding binding = new Binding(null, null, "test", SERVER, null, emptyList(), null);
        Configuration config = new Configuration("test", emptyList(), emptyList(), singletonList(binding));

        String text = jsonb.toJson(config);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\",\"bindings\":[{\"type\":\"test\",\"kind\":\"server\"}]}"));
    }

    @Test
    public void shouldReadConfigurationWithVault()
    {
        String text =
                "{" +
                    "\"name\": \"test\"," +
                    "\"bindings\":" +
                    "[" +
                    "]," +
                    "\"vaults\":" +
                    "[" +
                        "{" +
                            "\"name\": \"default\"," +
                            "\"type\": \"test\"" +
                        "}" +
                    "]," +
                    "\"namespaces\":" +
                    "[" +
                    "]" +
                "}";

        Configuration config = jsonb.fromJson(text, Configuration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.name, equalTo("test"));
        assertThat(config.vaults, hasSize(1));
        assertThat(config.vaults.get(0).name, equalTo("default"));
        assertThat(config.vaults.get(0).type, equalTo("test"));
        assertThat(config.namespaces, emptyCollectionOf(NamespaceRef.class));
    }

    @Test
    public void shouldWriteConfigurationWithVault()
    {
        Vault vault = new Vault("default", "test", null);
        Configuration config = new Configuration("test", emptyList(), singletonList(vault), emptyList());

        String text = jsonb.toJson(config);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\",\"vaults\":[{\"name\":\"default\",\"type\":\"test\"}]}"));
    }

    @Test
    public void shouldReadConfigurationWithNamespace()
    {
        String text =
                "{" +
                    "\"name\": \"test\"," +
                    "\"bindings\":" +
                    "[" +
                    "]," +
                    "\"namespaces\":" +
                    "[" +
                        "{" +
                            "\"name\": \"test\"" +
                        "}" +
                    "]" +
                "}";

        Configuration config = jsonb.fromJson(text, Configuration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.name, equalTo("test"));
        assertThat(config.bindings, emptyCollectionOf(Binding.class));
        assertThat(config.namespaces, hasSize(1));
        assertThat(config.namespaces.get(0).name, equalTo("test"));
        assertThat(config.namespaces.get(0).links, equalTo(emptyMap()));
    }

    @Test
    public void shouldWriteConfigurationWithNamespace()
    {
        NamespaceRef reference = new NamespaceRef("test", emptyMap());
        Configuration config = new Configuration("test", singletonList(reference), emptyList(), emptyList());

        String text = jsonb.toJson(config);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\",\"namespaces\":[{\"name\":\"test\"}]}"));
    }
}
