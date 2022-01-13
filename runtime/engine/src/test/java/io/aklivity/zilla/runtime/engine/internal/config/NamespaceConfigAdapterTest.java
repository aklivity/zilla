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

import static io.aklivity.zilla.runtime.engine.config.RoleConfig.SERVER;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;

public class NamespaceConfigAdapterTest
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

        NamespaceConfig namespace = jsonb.fromJson(text, NamespaceConfig.class);

        assertThat(namespace, not(nullValue()));
        assertThat(namespace.name, equalTo("default"));
        assertThat(namespace.bindings, emptyCollectionOf(BindingConfig.class));
    }

    @Test
    public void shouldWriteEmptyNamespace()
    {
        NamespaceConfig namespace = new NamespaceConfig("default", emptyList(), emptyList());

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
                    "{" +
                    "}," +
                    "\"bindings\":" +
                    "{" +
                    "}" +
                "}";

        NamespaceConfig namespace = jsonb.fromJson(text, NamespaceConfig.class);

        assertThat(namespace, not(nullValue()));
        assertThat(namespace.name, equalTo("test"));
        assertThat(namespace.bindings, emptyCollectionOf(BindingConfig.class));
        assertThat(namespace.vaults, emptyCollectionOf(VaultConfig.class));
    }

    @Test
    public void shouldWriteNamespace()
    {
        NamespaceConfig namespace = new NamespaceConfig("test", emptyList(), emptyList());

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
                    "{" +
                    "}," +
                    "\"bindings\":" +
                    "{" +
                        "\"test\":" +
                        "{" +
                            "\"type\": \"test\"," +
                            "\"kind\": \"server\"" +
                        "}" +
                    "}" +
                "}";

        NamespaceConfig namespace = jsonb.fromJson(text, NamespaceConfig.class);

        assertThat(namespace, not(nullValue()));
        assertThat(namespace.name, equalTo("test"));
        assertThat(namespace.bindings, hasSize(1));
        assertThat(namespace.bindings.get(0).entry, equalTo("test"));
        assertThat(namespace.bindings.get(0).type, equalTo("test"));
        assertThat(namespace.bindings.get(0).kind, equalTo(SERVER));
        assertThat(namespace.vaults, emptyCollectionOf(VaultConfig.class));
    }

    @Test
    public void shouldWriteNamespaceWithBinding()
    {
        BindingConfig binding = new BindingConfig(null, "test", "test", SERVER, null, emptyList(), null);
        NamespaceConfig namespace = new NamespaceConfig("test", emptyList(), singletonList(binding));

        String text = jsonb.toJson(namespace);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\",\"bindings\":{\"test\":{\"type\":\"test\",\"kind\":\"server\"}}}"));
    }

    @Test
    public void shouldReadConfigurationWithVault()
    {
        String text =
                "{" +
                    "\"name\": \"test\"," +
                    "\"bindings\":" +
                    "{" +
                    "}," +
                    "\"vaults\":" +
                    "{" +
                        "\"default\":" +
                        "{" +
                            "\"type\": \"test\"" +
                        "}" +
                    "}" +
                "}";

        NamespaceConfig namespace = jsonb.fromJson(text, NamespaceConfig.class);

        assertThat(namespace, not(nullValue()));
        assertThat(namespace.name, equalTo("test"));
        assertThat(namespace.vaults, hasSize(1));
        assertThat(namespace.vaults.get(0).name, equalTo("default"));
        assertThat(namespace.vaults.get(0).type, equalTo("test"));
    }

    @Test
    public void shouldWriteConfigurationWithVault()
    {
        VaultConfig vault = new VaultConfig("default", "test", null);
        NamespaceConfig namespace = new NamespaceConfig("test", singletonList(vault), emptyList());

        String text = jsonb.toJson(namespace);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\",\"vaults\":{\"default\":{\"type\":\"test\"}}}"));
    }
}
