/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.a2a.http.internal.config;

import static io.aklivity.zilla.runtime.binding.a2a.http.internal.config.A2aHttpBindingConfig.argPathValid;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.http.A2aHttpConditionConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpOptionsConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpWithConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.GenericRouteConfigBuilder;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.A2aHttpConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public class A2aHttpBindingConfigTest
{
    private static final float[][] VECTORS = {
        {1f, 0f},
        {0.9f, 0.1f},
        {0f, 1f}
    };

    private static RouteConfig route(
        int order,
        String skill,
        boolean authorized,
        List<String> roles)
    {
        GenericRouteConfigBuilder<RouteConfig> builder = RouteConfig.builder().order(order);
        if (skill != null)
        {
            builder = builder.when(A2aHttpConditionConfig.builder().skill(skill).build())
                .with(A2aHttpWithConfig::builder)
                    .header(":path", "/items/${args.id}")
                    .build()
                .exit("http0");
        }
        if (roles != null)
        {
            builder = builder.guarded().name("test0").roles(roles).build();
        }
        RouteConfig config = builder.build();
        config.authorized = (auth, identity) -> authorized;
        return config;
    }

    private static A2aHttpBindingConfig binding(
        A2aHttpOptionsConfig options,
        List<RouteConfig> routes)
    {
        final EngineContext context = mock(EngineContext.class);
        when(context.signaler()).thenReturn(mock(Signaler.class));
        final BindingConfig config = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("a2a-http")
            .kind(KindConfig.PROXY)
            .options(options)
            .routes(routes)
            .build();
        config.resolveId = name -> 1L;
        return new A2aHttpBindingConfig(config, context, new A2aHttpConfiguration());
    }

    private static A2aHttpOptionsConfig options(
        double threshold)
    {
        return A2aHttpOptionsConfig.builder()
            .embedding("embedding0")
            .store("store0")
            .threshold(threshold)
            .skill()
                .id("get_item")
                .name("Get item")
                .description("Look up an item by id")
                .example("get item 42")
                .tag("items")
                .build()
            .skill()
                .id("create_item")
                .description("Create \"an\" item")
                .build()
            .build();
    }

    @Test
    public void shouldMatchBestSkillAtOrAboveThreshold()
    {
        final A2aHttpBindingConfig binding = binding(options(0.9), List.of());

        assertThat(binding.match(VECTORS, new float[] {1f, 0f}), equalTo("get_item"));
        assertThat(binding.match(VECTORS, new float[] {0f, 1f}), equalTo("create_item"));
    }

    @Test
    public void shouldNotMatchBelowThreshold()
    {
        final A2aHttpBindingConfig binding = binding(options(0.999), List.of());

        assertNull(binding.match(VECTORS, new float[] {0.5f, 0.5f}));
    }

    @Test
    public void shouldSummarizeSkills()
    {
        final A2aHttpBindingConfig binding = binding(options(0.9), List.of());

        assertThat(binding.skillsSummary(),
            equalTo("get_item: Look up an item by id; create_item: Create \"an\" item"));
        assertThat(binding.skills().size(), equalTo(2));
        assertNotNull(binding.skill("get_item"));
        assertNull(binding.skill("unknown"));
    }

    @Test
    public void shouldGenerateCardWithDefaults()
    {
        final A2aHttpBindingConfig binding = binding(options(0.9), List.of());

        final String card = new String(binding.cardJson(), StandardCharsets.UTF_8);

        assertThat(card, containsString("\"name\":\"app0\""));
        assertThat(card, containsString("\"version\":\"1.0.0\""));
        assertThat(card, containsString("\"capabilities\":{\"streaming\":true,\"pushNotifications\":false}"));
        assertThat(card, containsString("\"defaultInputModes\":[\"text/plain\",\"application/json\"]"));
        assertThat(card, containsString("\"id\":\"get_item\",\"name\":\"Get item\""));
        assertThat(card, containsString("\"id\":\"create_item\",\"name\":\"create_item\""));
    }

    @Test
    public void shouldGenerateCardWithoutStreamingWhenDisabled()
    {
        final A2aHttpOptionsConfig options = A2aHttpOptionsConfig.builder()
            .embedding("embedding0")
            .store("store0")
            .card()
                .streaming(false)
                .build()
            .skill()
                .id("get_item")
                .description("Look up an item by id")
                .build()
            .build();
        final A2aHttpBindingConfig binding = binding(options, List.of());

        final String card = new String(binding.cardJson(), StandardCharsets.UTF_8);

        assertThat(card, containsString("\"capabilities\":{\"streaming\":false,\"pushNotifications\":false}"));
    }

    @Test
    public void shouldResolveRouteForSkillOnly()
    {
        final A2aHttpBindingConfig binding = binding(options(0.9), List.of(
            route(0, null, true, null),
            route(1, "get_item", true, null)));

        assertNotNull(binding.resolveRoute("get_item"));
        assertNull(binding.resolveRoute("create_item"));
    }

    @Test
    public void shouldAuthorizeOnlyWhenAllApplicableLayersAuthorize()
    {
        final A2aHttpBindingConfig binding = binding(options(0.9), List.of(
            route(0, null, false, List.of("global:read")),
            route(1, "get_item", true, List.of("items:read"))));

        assertFalse(binding.authorized("get_item", 1L));
        assertThat(binding.scopes("get_item"), equalTo("global:read items:read"));
    }

    @Test
    public void shouldAuthorizeWithoutGuardedRoles()
    {
        final A2aHttpBindingConfig binding = binding(options(0.9), List.of(
            route(0, "get_item", true, null)));

        assertTrue(binding.authorized("get_item", 1L));
        assertThat(binding.scopes("get_item"), nullValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectRouteWithUnresolvedSkill()
    {
        binding(options(0.9), List.of(route(0, "unknown", true, null)));
    }

    @Test
    public void shouldNotBeCredentialedWithoutAuthorization()
    {
        final A2aHttpBindingConfig binding = binding(options(0.9), List.of());

        assertFalse(binding.credentialed());
    }

    @Test
    public void shouldValidateArgumentPaths()
    {
        final String schema = "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}," +
            "\"nested\":{\"type\":\"object\",\"properties\":{\"leaf\":{\"type\":\"string\"}}}}}";

        assertTrue(argPathValid(schema, "id"));
        assertTrue(argPathValid(schema, "nested.leaf"));
        assertFalse(argPathValid(schema, "idd"));
        assertFalse(argPathValid(schema, "nested.missing"));
        assertTrue(argPathValid("{\"additionalProperties\":true}", "anything"));
        assertTrue(argPathValid(null, "id"));
        assertTrue(argPathValid("not json", "id"));
    }
}
