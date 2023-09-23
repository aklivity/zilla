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
package io.aklivity.zilla.runtime.engine.internal.config;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.REMOTE_SERVER;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.util.function.Function.identity;
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
        BindingConfig[] bindings =
        {
            BindingConfig.builder()
                .inject(identity())
                .name("test")
                .type("test")
                .kind(SERVER)
                .build()
        };

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
        BindingConfig[] bindings =
        {
            BindingConfig.builder()
                .inject(identity())
                .vault("test")
                .name("test")
                .type("test")
                .kind(SERVER)
                .build()
        };

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
        {
            BindingConfig.builder()
                .name("test")
                .type("test")
                .kind(SERVER)
                .options(TestBindingOptionsConfig::builder)
                    .inject(identity())
                    .mode("test")
                    .build()
                .build()
        };

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
    public void shouldWriteBindingWithExit()
    {
        BindingConfig[] bindings =
        {
            BindingConfig.builder()
                .inject(identity())
                .name("test")
                .type("test")
                .kind(SERVER)
                .exit("test")
                .build()
        };

        String text = jsonb.toJson(bindings);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"test\":{\"type\":\"test\",\"kind\":\"server\",\"exit\":\"test\"}}"));
    }
    @Test
    public void shouldWriteBindingWithRoute()
    {
        BindingConfig[] bindings =
        {
            BindingConfig.builder()
                .name("test")
                .type("test")
                .kind(SERVER)
                .route()
                    .exit("test")
                    .guarded()
                        .inject(identity())
                        .name("test0")
                        .role("read")
                        .build()
                    .build()
                .build()
        };

        String text = jsonb.toJson(bindings);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"test\":{\"type\":\"test\",\"kind\":\"server\"," +
                "\"routes\":[{\"exit\":\"test\",\"guarded\":{\"test0\":[\"read\"]}}]}}"));
    }

    @Test
    public void shouldReadBindingWithTelemetry()
    {
        String text =
                "{" +
                    "\"test\":" +
                    "{" +
                        "\"type\": \"test\"," +
                        "\"kind\": \"server\"," +
                        "\"telemetry\":" +
                        "{" +
                            "\"metrics\":" +
                            "[" +
                                "\"test.counter\"" +
                            "]" +
                        "}" +
                    "}" +
                "}";

        BindingConfig[] bindings = jsonb.fromJson(text, BindingConfig[].class);

        assertThat(bindings[0], not(nullValue()));
        assertThat(bindings[0].name, equalTo("test"));
        assertThat(bindings[0].kind, equalTo(SERVER));
        assertThat(bindings[0].telemetryRef.metricRefs, hasSize(1));
        assertThat(bindings[0].telemetryRef.metricRefs.get(0).name, equalTo("test.counter"));
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
    public void shouldWriteBindingWithTelemetry()
    {
        BindingConfig[] bindings =
        {
            BindingConfig.builder()
                .name("test")
                .type("test")
                .kind(SERVER)
                .telemetry()
                    .metric()
                        .name("test.counter")
                        .build()
                    .build()
                .build()
        };

        String text = jsonb.toJson(bindings);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"test\":{\"type\":\"test\",\"kind\":\"server\"," +
                "\"telemetry\":{\"metrics\":[\"test.counter\"]}}}"));
    }

    @Test
    public void shouldWriteBindingWithRemoteServerKind()
    {
        BindingConfig[] bindings =
        {
            BindingConfig.builder()
                .name("test")
                .type("test")
                .kind(REMOTE_SERVER)
                .entry("test_entry")
                .route()
                    .exit("test")
                    .build()
                .build()
        };

        String text = jsonb.toJson(bindings);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"test\":{\"type\":\"test\",\"kind\":\"remote_server\"," +
                "\"entry\":\"test_entry\",\"exit\":\"test\"}}"));
    }
}
