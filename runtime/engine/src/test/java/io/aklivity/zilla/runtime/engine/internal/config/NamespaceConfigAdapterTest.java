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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
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
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;

public class NamespaceConfigAdapterTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private ConfigAdapterContext context;

    private Jsonb jsonb;


    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new NamespaceAdapter(context));
        jsonb = JsonbBuilder.create(config);
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
                    "}," +
                    "\"references\":" +
                    "[" +
                    "]" +
                "}";

        NamespaceConfig config = jsonb.fromJson(text, NamespaceConfig.class);

        assertThat(config, not(nullValue()));
        assertThat(config.name, equalTo("test"));
        assertThat(config.bindings, emptyCollectionOf(BindingConfig.class));
        assertThat(config.vaults, emptyCollectionOf(VaultConfig.class));
        assertThat(config.references, emptyCollectionOf(NamespaceRef.class));
    }

    @Test
    public void shouldWriteNamespace()
    {
        NamespaceConfig config = new NamespaceConfig("test", emptyList(), emptyList(), emptyList(), emptyList());

        String text = jsonb.toJson(config);

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

        NamespaceConfig config = jsonb.fromJson(text, NamespaceConfig.class);

        assertThat(config, not(nullValue()));
        assertThat(config.name, equalTo("test"));
        assertThat(config.bindings, hasSize(1));
        assertThat(config.bindings.get(0).name, equalTo("test"));
        assertThat(config.bindings.get(0).type, equalTo("test"));
        assertThat(config.bindings.get(0).kind, equalTo(SERVER));
        assertThat(config.vaults, emptyCollectionOf(VaultConfig.class));
        assertThat(config.references, emptyCollectionOf(NamespaceRef.class));
    }

    @Test
    public void shouldWriteNamespaceWithBinding()
    {
        BindingConfig binding = new BindingConfig(null, "test", "test", SERVER, null, null, emptyList());
        NamespaceConfig namespace = new NamespaceConfig("test", emptyList(), singletonList(binding), emptyList(), emptyList());

        String text = jsonb.toJson(namespace);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\",\"bindings\":{\"test\":{\"type\":\"test\",\"kind\":\"server\"}}}"));
    }

    @Test
    public void shouldReadNamespaceWithGuard()
    {
        String text =
                "{" +
                    "\"name\": \"test\"," +
                    "\"bindings\":" +
                    "{" +
                    "}," +
                    "\"guards\":" +
                    "{" +
                        "\"default\":" +
                        "{" +
                            "\"type\": \"test\"" +
                        "}" +
                    "}" +
                "}";

        NamespaceConfig config = jsonb.fromJson(text, NamespaceConfig.class);

        assertThat(config, not(nullValue()));
        assertThat(config.name, equalTo("test"));
        assertThat(config.guards, hasSize(1));
        assertThat(config.guards.get(0).name, equalTo("default"));
        assertThat(config.guards.get(0).type, equalTo("test"));
    }

    @Test
    public void shouldWriteNamespaceWithGuard()
    {
        GuardConfig guard = new GuardConfig("default", "test", null);
        NamespaceConfig config = new NamespaceConfig("test", emptyList(), emptyList(), singletonList(guard), emptyList());

        String text = jsonb.toJson(config);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\",\"guards\":{\"default\":{\"type\":\"test\"}}}"));
    }

    @Test
    public void shouldReadNamespaceWithVault()
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

        NamespaceConfig config = jsonb.fromJson(text, NamespaceConfig.class);

        assertThat(config, not(nullValue()));
        assertThat(config.name, equalTo("test"));
        assertThat(config.vaults, hasSize(1));
        assertThat(config.vaults.get(0).name, equalTo("default"));
        assertThat(config.vaults.get(0).type, equalTo("test"));
    }

    @Test
    public void shouldWriteNamespaceWithVault()
    {
        VaultConfig vault = new VaultConfig("default", "test", null);
        NamespaceConfig config = new NamespaceConfig("test", emptyList(), emptyList(), emptyList(), singletonList(vault));

        String text = jsonb.toJson(config);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\",\"vaults\":{\"default\":{\"type\":\"test\"}}}"));
    }

    @Test
    public void shouldReadNamespaceWithReference()
    {
        String text =
                "{" +
                    "\"name\": \"test\"," +
                    "\"references\":" +
                    "[" +
                        "{" +
                            "\"name\": \"test\"" +
                        "}" +
                    "]" +
                "}";

        NamespaceConfig config = jsonb.fromJson(text, NamespaceConfig.class);

        assertThat(config, not(nullValue()));
        assertThat(config.name, equalTo("test"));
        assertThat(config.bindings, emptyCollectionOf(BindingConfig.class));
        assertThat(config.vaults, emptyCollectionOf(VaultConfig.class));
        assertThat(config.references, hasSize(1));
        assertThat(config.references.get(0).name, equalTo("test"));
        assertThat(config.references.get(0).links, equalTo(emptyMap()));
    }

    @Test
    public void shouldWriteNamespaceWithReference()
    {
        NamespaceRef reference = new NamespaceRef("test", emptyMap());
        NamespaceConfig config = new NamespaceConfig("test", singletonList(reference), emptyList(), emptyList(), emptyList());

        String text = jsonb.toJson(config);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"name\":\"test\",\"references\":[{\"name\":\"test\"}]}"));
    }
}
