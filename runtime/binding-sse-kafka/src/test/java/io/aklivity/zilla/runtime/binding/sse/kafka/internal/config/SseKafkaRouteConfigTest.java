/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithFilterConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithFilterHeaderConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class SseKafkaRouteConfigTest
{
    @Test
    public void shouldResolveGuardViaEngineContextWhenNotInGuardedSection()
    {
        // Given: EngineContext with a JWT guard
        EngineContext context = mock(EngineContext.class);
        GuardHandler guardHandler = mock(GuardHandler.class);

        int guardId = 0x0001;

        when(context.supplyTypeId("jwt")).thenReturn(guardId);
        when(context.supplyGuard(guardId)).thenReturn(guardHandler);

        // Given: Route config with guard expression in filters but no guarded section
        SseKafkaWithConfig withConfig = SseKafkaWithConfig.builder()
            .topic("test-topic")
            .eventId(SseKafkaWithConfig.EVENT_ID_DEFAULT)
            .filters(List.of(
                SseKafkaWithFilterConfig.builder()
                    .key("partition-key")
                    .headers(List.of(
                        new SseKafkaWithFilterHeaderConfig(
                            "zilla:identity",
                            "${guarded['jwt'].identity}")))
                    .build()))
            .build();

        RouteConfig route = RouteConfig.builder()
            .exit("kafka_cache_client")
            .with(withConfig)
            .build();

        // When: Creating SseKafkaRouteConfig
        SseKafkaRouteConfig sseRoute = new SseKafkaRouteConfig(route, context);

        // Then: EngineContext should be called to resolve guard
        verify(context).supplyTypeId("jwt");
        verify(context).supplyGuard(guardId);

        // Then: Route config should not be null
        assertThat(sseRoute, not(nullValue()));
        assertThat(sseRoute.id, equalTo(route.id));
    }

    @Test
    public void shouldUseExplicitGuardWhenInGuardedSection()
    {
        // Given: EngineContext
        EngineContext context = mock(EngineContext.class);

        // Given: GuardedConfig with identity function
        GuardedConfig guardedConfig = GuardedConfig.builder()
            .name("jwt")
            .roles(List.of("read:User"))
            .build();
        guardedConfig.identity = s -> "explicit-user@example.com";
        guardedConfig.attributes = (s, n) -> "explicit-attribute";

        // Given: Route config with guard in BOTH guarded section AND expression
        SseKafkaWithConfig withConfig = SseKafkaWithConfig.builder()
            .topic("test-topic")
            .eventId(SseKafkaWithConfig.EVENT_ID_DEFAULT)
            .filters(List.of(
                SseKafkaWithFilterConfig.builder()
                    .key("partition-key")
                    .headers(List.of(
                        new SseKafkaWithFilterHeaderConfig(
                            "zilla:identity",
                            "${guarded['jwt'].identity}")))
                    .build()))
            .build();

        RouteConfig route = RouteConfig.builder()
            .exit("kafka_cache_client")
            .with(withConfig)
            .guarded(guardedConfig)
            .build();

        // When: Creating SseKafkaRouteConfig
        SseKafkaRouteConfig sseRoute = new SseKafkaRouteConfig(route, context);

        // Then: EngineContext should NOT be called (explicit guard takes precedence)
        verify(context, never()).supplyTypeId(anyString());
        verify(context, never()).supplyGuard(anyInt());

        // Then: Route config should not be null
        assertThat(sseRoute, not(nullValue()));
    }

    @Test
    public void shouldResolveMultipleGuardsFromExpressions()
    {
        // Given: EngineContext with multiple guards
        EngineContext context = mock(EngineContext.class);
        GuardHandler jwtGuard = mock(GuardHandler.class);
        GuardHandler apiKeyGuard = mock(GuardHandler.class);

        int jwtGuardId = 0x0001;
        int apiKeyGuardId = 0x0002;

        when(context.supplyTypeId("jwt")).thenReturn(jwtGuardId);
        when(context.supplyGuard(jwtGuardId)).thenReturn(jwtGuard);

        when(context.supplyTypeId("apikey")).thenReturn(apiKeyGuardId);
        when(context.supplyGuard(apiKeyGuardId)).thenReturn(apiKeyGuard);

        // Given: Route config with multiple guard expressions
        SseKafkaWithConfig withConfig = SseKafkaWithConfig.builder()
            .topic("test-topic")
            .eventId(SseKafkaWithConfig.EVENT_ID_DEFAULT)
            .filters(List.of(
                SseKafkaWithFilterConfig.builder()
                    .key("partition-key")
                    .headers(List.of(
                        new SseKafkaWithFilterHeaderConfig(
                            "zilla:jwt-identity",
                            "${guarded['jwt'].identity}"),
                        new SseKafkaWithFilterHeaderConfig(
                            "zilla:apikey-identity",
                            "${guarded['apikey'].identity}")))
                    .build()))
            .build();

        RouteConfig route = RouteConfig.builder()
            .exit("kafka_cache_client")
            .with(withConfig)
            .build();

        // When: Creating SseKafkaRouteConfig
        SseKafkaRouteConfig sseRoute = new SseKafkaRouteConfig(route, context);

        // Then: Both guards should be resolved
        verify(context).supplyTypeId("jwt");
        verify(context).supplyGuard(jwtGuardId);
        verify(context).supplyTypeId("apikey");
        verify(context).supplyGuard(apiKeyGuardId);

        assertThat(sseRoute, not(nullValue()));
    }

    @Test
    public void shouldHandleGuardNotFoundGracefully()
    {
        // Given: EngineContext that returns null for unknown guard
        EngineContext context = mock(EngineContext.class);

        when(context.supplyTypeId("unknown")).thenReturn(0x0001);
        when(context.supplyGuard(0x0001)).thenReturn(null);  // Guard not found

        // Given: Route config with unknown guard expression
        SseKafkaWithConfig withConfig = SseKafkaWithConfig.builder()
            .topic("test-topic")
            .eventId(SseKafkaWithConfig.EVENT_ID_DEFAULT)
            .filters(List.of(
                SseKafkaWithFilterConfig.builder()
                    .key("partition-key")
                    .headers(List.of(
                        new SseKafkaWithFilterHeaderConfig(
                            "zilla:identity",
                            "${guarded['unknown'].identity}")))
                    .build()))
            .build();

        RouteConfig route = RouteConfig.builder()
            .exit("kafka_cache_client")
            .with(withConfig)
            .build();

        // When: Creating SseKafkaRouteConfig (should not throw exception)
        SseKafkaRouteConfig sseRoute = new SseKafkaRouteConfig(route, context);

        // Then: Route config should still be created successfully
        assertThat(sseRoute, not(nullValue()));
    }

    @Test
    public void shouldNotCallEngineContextWhenNoGuardExpressions()
    {
        // Given: EngineContext
        EngineContext context = mock(EngineContext.class);

        // Given: Route config without guard expressions
        SseKafkaWithConfig withConfig = SseKafkaWithConfig.builder()
            .topic("test-topic")
            .eventId(SseKafkaWithConfig.EVENT_ID_DEFAULT)
            .filters(List.of(
                SseKafkaWithFilterConfig.builder()
                    .key("partition-key")
                    .headers(List.of(
                        new SseKafkaWithFilterHeaderConfig(
                            "timestamp",
                            "${timestamp}")))
                    .build()))
            .build();

        RouteConfig route = RouteConfig.builder()
            .exit("kafka_cache_client")
            .with(withConfig)
            .build();

        // When: Creating SseKafkaRouteConfig
        SseKafkaRouteConfig sseRoute = new SseKafkaRouteConfig(route, context);

        // Then: EngineContext should not be called
        verify(context, never()).supplyTypeId(anyString());
        verify(context, never()).supplyGuard(anyInt());

        assertThat(sseRoute, not(nullValue()));
    }
}
