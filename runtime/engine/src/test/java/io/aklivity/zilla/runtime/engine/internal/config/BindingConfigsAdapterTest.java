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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.REMOTE_SERVER;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
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
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.test.internal.binding.config.TestBindingOptionsConfig;

public class BindingConfigsAdapterTest
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
                .withAdapters(new BindingConfigsAdapter(context));
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadBinding()
    {
        String text =
                "{" +
                    "\"test\":" +
                    "{" +
                        "\"type\": \"test\"," +
                        "\"kind\": \"server\"," +
                        "\"routes\":" +
                        "[" +
                        "]" +
                    "}" +
                "}";

        BindingConfig[] bindings = jsonb.fromJson(text, BindingConfig[].class);

        assertThat(bindings[0], not(nullValue()));
        assertThat(bindings[0].kind, equalTo(SERVER));
        assertThat(bindings[0].routes, emptyCollectionOf(RouteConfig.class));
    }

    @Test
    public void shouldWriteBinding()
    {
        BindingConfig[] bindings = { new BindingConfig(null, "test", null, "test",
            SERVER, null, emptyList()) };

        String text = jsonb.toJson(bindings);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"test\":{\"type\":\"test\",\"kind\":\"server\"}}"));
    }

    @Test
    public void shouldReadBindingWithVault()
    {
        String text =
                "{" +
                    "\"test\":" +
                    "{" +
                        "\"vault\": \"test\"," +
                        "\"type\": \"test\"," +
                        "\"kind\": \"server\"," +
                        "\"routes\":" +
                        "[" +
                        "]" +
                    "}" +
                "}";

        BindingConfig[] bindings = jsonb.fromJson(text, BindingConfig[].class);

        assertThat(bindings[0], not(nullValue()));
        assertThat(bindings[0].vault, not(nullValue()));
        assertThat(bindings[0].vault, equalTo("test"));
        assertThat(bindings[0].kind, equalTo(SERVER));
        assertThat(bindings[0].routes, emptyCollectionOf(RouteConfig.class));
    }

    @Test
    public void shouldWriteBindingWithVault()
    {
        BindingConfig[] bindings = { new BindingConfig("test", "test",  null, "test", SERVER, null, emptyList()) };

        String text = jsonb.toJson(bindings);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"test\":{\"vault\":\"test\",\"type\":\"test\",\"kind\":\"server\"}}"));
    }

    @Test
    public void shouldReadBindingWithOptions()
    {
        String text =
                "{" +
                    "\"test\":" +
                    "{" +
                        "\"type\": \"test\"," +
                        "\"kind\": \"server\"," +
                        "\"options\":" +
                        "{" +
                            "\"mode\": \"test\"" +
                        "}" +
                    "}" +
                "}";

        BindingConfig[] bindings = jsonb.fromJson(text, BindingConfig[].class);

        assertThat(bindings[0], not(nullValue()));
        assertThat(bindings[0].name, equalTo("test"));
        assertThat(bindings[0].kind, equalTo(SERVER));
        assertThat(bindings[0].options, instanceOf(TestBindingOptionsConfig.class));
        assertThat(((TestBindingOptionsConfig) bindings[0].options).mode, equalTo("test"));
    }

    @Test
    public void shouldWriteBindingWithOptions()
    {
        BindingConfig[] bindings =
            { new BindingConfig(null, "test",  null, "test", SERVER, new TestBindingOptionsConfig("test"), emptyList()) };

        String text = jsonb.toJson(bindings);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"test\":{\"type\":\"test\",\"kind\":\"server\",\"options\":{\"mode\":\"test\"}}}"));
    }

    @Test
    public void shouldReadBindingWithRoute()
    {
        String text =
                "{" +
                    "\"test\":" +
                    "{" +
                        "\"type\": \"test\"," +
                        "\"kind\": \"server\"," +
                        "\"routes\":" +
                        "[" +
                            "{" +
                                "\"exit\": \"test\"" +
                            "}" +
                        "]" +
                    "}" +
                "}";

        BindingConfig[] bindings = jsonb.fromJson(text, BindingConfig[].class);

        assertThat(bindings[0], not(nullValue()));
        assertThat(bindings[0].name, equalTo("test"));
        assertThat(bindings[0].kind, equalTo(SERVER));
        assertThat(bindings[0].routes, hasSize(1));
        assertThat(bindings[0].routes.get(0).exit, equalTo("test"));
        assertThat(bindings[0].routes.get(0).when, empty());
    }

    @Test
    public void shouldWriteBindingWithRoute()
    {
        BindingConfig[] bindings =
            { new BindingConfig(null, "test", null, "test", SERVER, null, singletonList(new RouteConfig("test"))) };

        String text = jsonb.toJson(bindings);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"test\":{\"type\":\"test\",\"kind\":\"server\",\"routes\":[{\"exit\":\"test\"}]}}"));
    }

    @Test
    public void shouldReadBindingWithRemoteServerKind()
    {
        String text =
            "{" +
                "\"test\":" +
                "{" +
                "\"type\": \"test\"," +
                "\"kind\": \"remote_server\"," +
                "\"entry\": \"test_entry\"," +
                "\"routes\":" +
                "[" +
                "{" +
                "\"exit\": \"test\"" +
                "}" +
                "]" +
                "}" +
                "}";

        BindingConfig[] bindings = jsonb.fromJson(text, BindingConfig[].class);

        assertThat(bindings[0], not(nullValue()));
        assertThat(bindings[0].name, equalTo("test"));
        assertThat(bindings[0].kind, equalTo(REMOTE_SERVER));
        assertThat(bindings[0].entry, equalTo("test_entry"));
        assertThat(bindings[0].routes, hasSize(1));
        assertThat(bindings[0].routes.get(0).exit, equalTo("test"));
        assertThat(bindings[0].routes.get(0).when, empty());
    }

    @Test
    public void shouldWriteBindingWithRemoteServerKind()
    {
        BindingConfig[] bindings =
            { new BindingConfig(null, "test", "test_entry", "test", REMOTE_SERVER,
                null, singletonList(new RouteConfig("test"))) };

        String text = jsonb.toJson(bindings);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"test\":{\"type\":\"test\",\"kind\":\"remote_server\"," +
            "\"entry\":\"test_entry\",\"routes\":[{\"exit\":\"test\"}]}}"));
    }

}
