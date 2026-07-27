/*
 * Copyright 2021-2026 Aklivity Inc.
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

import static io.aklivity.zilla.config.engine.KindConfig.PROXY;
import static io.aklivity.zilla.config.engine.KindConfig.REMOTE_SERVER;
import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.EngineInfo;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.internal.BindingConfigAdapter;
import io.aklivity.zilla.runtime.engine.test.internal.binding.config.TestBindingOptionsConfig;

public class BindingConfigAdapterTest
{
    private BindingConfigAdapter adapter;

    @Before
    public void initJson()
    {
        EngineInfo info = new EngineInfo();
        adapter = new BindingConfigAdapter(info);
        adapter.adaptNamespace("test");
    }

    @Test
    public void shouldReadBinding() throws Exception
    {
        String text =
                "{" +
                    "\"type\": \"test\"," +
                    "\"kind\": \"proxy\"," +
                    "\"routes\":" +
                    "[" +
                    "]" +
                "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        BindingConfig binding = adapter.adaptFromJson("test", object);

        assertThat(binding, not(nullValue()));
        assertThat(binding.kind, equalTo(PROXY));
        assertThat(binding.routes, emptyCollectionOf(RouteConfig.class));
    }

    @Test
    public void shouldWriteBinding() throws Exception
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .inject(identity())
            .namespace("test")
            .name("test")
            .type("test")
            .kind(SERVER)
            .build();

        JsonObject object = adapter.adaptToJson(binding);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"type\":\"test\",\"kind\":\"server\"}"));
    }

    @Test
    public void shouldReadBindingWithVault() throws Exception
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

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        BindingConfig binding = adapter.adaptFromJson("test", object);

        assertThat(binding, not(nullValue()));
        assertThat(binding.vault, not(nullValue()));
        assertThat(binding.vault, equalTo("test"));
        assertThat(binding.kind, equalTo(SERVER));
        assertThat(binding.routes, emptyCollectionOf(RouteConfig.class));
    }

    @Test
    public void shouldWriteBindingWithVault() throws Exception
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .inject(identity())
            .namespace("test")
            .name("test")
            .type("test")
            .kind(SERVER)
            .vault("test")
            .build();

        JsonObject object = adapter.adaptToJson(binding);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"type\":\"test\",\"kind\":\"server\",\"vault\":\"test\"}"));
    }

    @Test
    public void shouldReadBindingWithOptions() throws Exception
    {
        String text =
                "{" +
                    "\"type\": \"test\"," +
                    "\"kind\": \"server\"," +
                    "\"options\":" +
                    "{" +
                        "\"mode\": \"test\"" +
                    "}" +
                "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        BindingConfig binding = adapter.adaptFromJson("test", object);

        assertThat(binding, not(nullValue()));
        assertThat(binding.name, equalTo("test"));
        assertThat(binding.kind, equalTo(SERVER));
        assertThat(binding.options, instanceOf(TestBindingOptionsConfig.class));
        assertThat(((TestBindingOptionsConfig) binding.options).mode, equalTo("test"));
    }

    @Test
    public void shouldWriteBindingWithOptions() throws Exception
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("test")
            .type("test")
            .kind(SERVER)
            .options(TestBindingOptionsConfig::builder)
                .inject(identity())
                .mode("test")
                .build()
            .build();

        JsonObject object = adapter.adaptToJson(binding);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"type\":\"test\",\"kind\":\"server\",\"options\":{\"mode\":\"test\"}}"));
    }

    @Test
    public void shouldReadBindingWithRoute() throws Exception
    {
        String text =
                "{" +
                    "\"type\": \"test\"," +
                    "\"kind\": \"server\"," +
                    "\"routes\":" +
                    "[" +
                        "{" +
                            "\"exit\": \"test\"" +
                        "}" +
                    "]" +
                "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        BindingConfig binding = adapter.adaptFromJson("test", object);

        assertThat(binding, not(nullValue()));
        assertThat(binding.name, equalTo("test"));
        assertThat(binding.kind, equalTo(SERVER));
        assertThat(binding.routes, hasSize(1));
        assertThat(binding.routes.get(0).exit, equalTo("test"));
        assertThat(binding.routes.get(0).when, empty());
    }

    @Test
    public void shouldWriteBindingWithExit() throws Exception
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .inject(identity())
            .namespace("test")
            .name("test")
            .type("test")
            .kind(SERVER)
            .exit("test")
            .build();

        JsonObject object = adapter.adaptToJson(binding);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"type\":\"test\",\"kind\":\"server\",\"exit\":\"test\"}"));
    }

    @Test
    public void shouldWriteBindingWithRoute() throws Exception
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
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
            .build();

        JsonObject object = adapter.adaptToJson(binding);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"type\":\"test\",\"kind\":\"server\"," +
                "\"routes\":[{\"exit\":\"test\",\"guarded\":{\"test0\":[\"read\"]}}]}"));
    }

    @Test
    public void shouldReadBindingWithTelemetry() throws Exception
    {
        String text =
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
                "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        BindingConfig binding = adapter.adaptFromJson("test", object);

        assertThat(binding, not(nullValue()));
        assertThat(binding.name, equalTo("test"));
        assertThat(binding.kind, equalTo(SERVER));
        assertThat(binding.telemetryRef.metricRefs, hasSize(1));
        assertThat(binding.telemetryRef.metricRefs.get(0).name, equalTo("test.counter"));
    }

    @Test
    public void shouldReadBindingWithRemoteServerKind() throws Exception
    {
        String text =
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
            "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        BindingConfig binding = adapter.adaptFromJson("test", object);

        assertThat(binding, not(nullValue()));
        assertThat(binding.name, equalTo("test"));
        assertThat(binding.kind, equalTo(REMOTE_SERVER));
        assertThat(binding.entry, equalTo("test_entry"));
        assertThat(binding.routes, hasSize(1));
        assertThat(binding.routes.get(0).exit, equalTo("test"));
        assertThat(binding.routes.get(0).when, empty());
    }

    @Test
    public void shouldWriteBindingWithTelemetry() throws Exception
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("test")
            .type("test")
            .kind(SERVER)
            .telemetry()
                .metric()
                    .name("test.counter")
                    .build()
                .build()
            .build();

        JsonObject object = adapter.adaptToJson(binding);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"type\":\"test\",\"kind\":\"server\"," +
                "\"telemetry\":{\"metrics\":[\"test.counter\"]}}"));
    }

    @Test
    public void shouldReadBindingWithTelemetryAttributes() throws Exception
    {
        String text =
                "{" +
                    "\"type\": \"test\"," +
                    "\"kind\": \"server\"," +
                    "\"telemetry\":" +
                    "{" +
                        "\"metrics\":" +
                        "[" +
                            "\"test.counter\"" +
                        "]," +
                        "\"attributes\":" +
                        "{" +
                            "\"method\": \"${http.request.method}\"," +
                            "\"status\": \"${http.response.status}\"" +
                        "}" +
                    "}" +
                "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        BindingConfig binding = adapter.adaptFromJson("test", object);

        assertThat(binding, not(nullValue()));
        assertThat(binding.name, equalTo("test"));
        assertThat(binding.kind, equalTo(SERVER));
        assertThat(binding.telemetryRef.metricRefs, hasSize(1));
        assertThat(binding.telemetryRef.metricRefs.get(0).name, equalTo("test.counter"));
        assertThat(binding.telemetryRef.attributes, hasSize(2));
    }

    @Test
    public void shouldWriteBindingWithTelemetryAttributes() throws Exception
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("test")
            .type("test")
            .kind(SERVER)
            .telemetry()
                .metric()
                    .name("test.counter")
                    .build()
                .attribute()
                    .name("method")
                    .value("${http.request.method}")
                    .build()
                .build()
            .build();

        JsonObject object = adapter.adaptToJson(binding);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"type\":\"test\",\"kind\":\"server\"," +
                "\"telemetry\":{\"metrics\":[\"test.counter\"],\"attributes\":{\"method\":\"${http.request.method}\"}}}"));
    }

    @Test
    public void shouldWriteBindingWithCatalog() throws Exception
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("test")
            .type("test")
            .kind(SERVER)
            .catalog()
                .name("catalog0")
                    .schema()
                    .subject("echo")
                    .build()
                .build()
            .build();

        JsonObject object = adapter.adaptToJson(binding);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"type\":\"test\",\"kind\":\"server\",\"catalog\":" +
            "[{\"catalog0\":[{\"subject\":\"echo\"}]}]}"));
    }

    @Test
    public void shouldReadBindingWithCatalog() throws Exception
    {
        String text =
            "{" +
            "    \"type\": \"test\"," +
            "    \"kind\": \"server\"," +
            "    \"catalog\":" +
            "     {" +
            "      \"catalog0\":" +
            "      [" +
            "        {" +
            "          \"subject\": \"echo\"" +
            "        }" +
            "      ]" +
            "    }" +
            "}";

        JsonObject object = Json.createReader(new StringReader(text)).readObject();
        BindingConfig binding = adapter.adaptFromJson("test", object);

        assertThat(binding, not(nullValue()));
        assertThat(binding.name, equalTo("test"));
        assertThat(binding.kind, equalTo(SERVER));
        assertThat(binding.catalogs, hasSize(1));
        assertThat(binding.catalogs.stream().findFirst().get().name, equalTo("catalog0"));
    }

    @Test
    public void shouldWriteBindingWithRemoteServerKind() throws Exception
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("test")
            .type("test")
            .kind(REMOTE_SERVER)
            .entry("test_entry")
            .route()
                .exit("test")
                .build()
            .build();

        JsonObject object = adapter.adaptToJson(binding);

        assertThat(object, not(nullValue()));
        assertThat(object.toString(), equalTo("{\"type\":\"test\",\"kind\":\"remote_server\"," +
                "\"entry\":\"test_entry\",\"exit\":\"test\"}"));
    }
}
