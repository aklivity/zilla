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
package io.aklivity.zilla.engine.drive.internal.config;

import static io.aklivity.zilla.engine.drive.config.Role.SERVER;
import static java.util.Collections.emptyList;
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

import io.aklivity.zilla.engine.drive.config.Binding;
import io.aklivity.zilla.engine.drive.config.Namespace;
import io.aklivity.zilla.engine.drive.config.Vault;

public class NamespaceAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new NamespaceAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadEmptyNamespace()
    {
        String text =
                "{" +
                "}";

        Namespace namespace = jsonb.fromJson(text, Namespace.class);

        assertThat(namespace, not(nullValue()));
        assertThat(namespace.name, equalTo("default"));
        assertThat(namespace.bindings, emptyCollectionOf(Binding.class));
    }

    @Test
    public void shouldWriteEmptyNamespace()
    {
        Namespace namespace = new Namespace("default", emptyList(), emptyList());

        String text = jsonb.toJson(namespace);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{}"));
    }

    @Test
    public void shouldReadNamespace()
    {
        String text =
                "{" +
                    "\"name\": \"test\"," +
                    "\"vaults\":" +
                    "[" +
                    "]," +
                    "\"bindings\":" +
                    "[" +
                    "]" +
                "}";

        Namespace namespace = jsonb.fromJson(text, Namespace.class);

        assertThat(namespace, not(nullValue()));
        assertThat(namespace.name, equalTo("test"));
        assertThat(namespace.bindings, emptyCollectionOf(Binding.class));
    }

    @Test
    public void shouldWriteNamespace()
    {
        Namespace namespace = new Namespace("test", emptyList(), emptyList());

        String text = jsonb.toJson(namespace);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\"}"));
    }

    @Test
    public void shouldReadNamespaceWithBinding()
    {
        String text =
                "{" +
                    "\"name\": \"test\"," +
                    "\"vaults\":" +
                    "[" +
                    "]," +
                    "\"bindings\":" +
                    "[" +
                        "{" +
                            "\"type\": \"test\"," +
                            "\"kind\": \"server\"" +
                        "}" +
                    "]" +
                "}";

        Namespace namespace = jsonb.fromJson(text, Namespace.class);

        assertThat(namespace, not(nullValue()));
        assertThat(namespace.name, equalTo("test"));
        assertThat(namespace.bindings, hasSize(1));
        assertThat(namespace.bindings.get(0).type, equalTo("test"));
        assertThat(namespace.bindings.get(0).kind, equalTo(SERVER));
    }

    @Test
    public void shouldWriteNamespaceWithBinding()
    {
        Binding binding = new Binding(null, null, "test", SERVER, null, emptyList(), null);
        Namespace namespace = new Namespace("test", emptyList(), singletonList(binding));

        String text = jsonb.toJson(namespace);

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
                    "]" +
                "}";

        Namespace namespace = jsonb.fromJson(text, Namespace.class);

        assertThat(namespace, not(nullValue()));
        assertThat(namespace.name, equalTo("test"));
        assertThat(namespace.vaults, hasSize(1));
        assertThat(namespace.vaults.get(0).name, equalTo("default"));
        assertThat(namespace.vaults.get(0).type, equalTo("test"));
    }

    @Test
    public void shouldWriteConfigurationWithVault()
    {
        Vault vault = new Vault("default", "test", null);
        Namespace namespace = new Namespace("test", singletonList(vault), emptyList());

        String text = jsonb.toJson(namespace);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\",\"vaults\":[{\"name\":\"default\",\"type\":\"test\"}]}"));
    }
}
