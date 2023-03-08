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
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfig;
import io.aklivity.zilla.runtime.engine.test.internal.binding.config.TestBindingOptionsConfig;

public class BindingConfigsAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new BindingConfigsAdapter());
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
        BindingConfig[] bindings = { new BindingConfig(null, "test", "test", SERVER, null, emptyList(), null) };

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
        BindingConfig[] bindings = { new BindingConfig("test", "test", "test", SERVER, null, emptyList(), null) };

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
        assertThat(bindings[0].entry, equalTo("test"));
        assertThat(bindings[0].kind, equalTo(SERVER));
        assertThat(bindings[0].options, instanceOf(TestBindingOptionsConfig.class));
        assertThat(((TestBindingOptionsConfig) bindings[0].options).mode, equalTo("test"));
    }

    @Test
    public void shouldWriteBindingWithOptions()
    {
        BindingConfig[] bindings =
            { new BindingConfig(null, "test", "test", SERVER, new TestBindingOptionsConfig("test"), emptyList(), null) };

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
        assertThat(bindings[0].entry, equalTo("test"));
        assertThat(bindings[0].kind, equalTo(SERVER));
        assertThat(bindings[0].routes, hasSize(1));
        assertThat(bindings[0].routes.get(0).exit, equalTo("test"));
        assertThat(bindings[0].routes.get(0).when, empty());
    }

    @Test
    public void shouldWriteBindingWithRoute()
    {
        BindingConfig[] bindings =
            { new BindingConfig(null, "test", "test", SERVER, null, singletonList(new RouteConfig("test")), null) };

        String text = jsonb.toJson(bindings);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"test\":{\"type\":\"test\",\"kind\":\"server\",\"routes\":[{\"exit\":\"test\"}]}}"));
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
        assertThat(bindings[0].entry, equalTo("test"));
        assertThat(bindings[0].kind, equalTo(SERVER));
        assertThat(bindings[0].telemetryRef.metricRefs, hasSize(1));
        assertThat(bindings[0].telemetryRef.metricRefs.get(0).name, equalTo("test.counter"));
    }

    @Test
    @Ignore // TODO: Ati
    public void shouldWriteBindingWithTelemetry()
    {
        TelemetryRefConfig telemetry = new TelemetryRefConfig(List.of(new MetricRefConfig("test.counter")));
        BindingConfig[] bindings =
            { new BindingConfig(null, "test", "test", SERVER, null, List.of(), telemetry) };

        String text = jsonb.toJson(bindings);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"test\":{\"type\":\"test\",\"kind\":\"server\"," +
                "\"telemetry\":{\"metrics\":[\"test.counter\"]}}}"));
    }
}
