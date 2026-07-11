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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.composite;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.PROXY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiSpecConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiConditionConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiWithConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public class OpenapiAsyncapiProxyGeneratorTest
{
    private static final String OPENAPI_SPEC =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": [ { "url": "http://localhost:8080" } ],
          "components": { "securitySchemes": {
            "bearerAuth": { "type": "http", "scheme": "bearer", "bearerFormat": "jwt" },
            "oauthScheme": { "type": "oauth2", "flows": {} }
          } },
          "paths": {
            "/mapped": {
              "get": {
                "operationId": "mapped",
                "security": [ { "bearerAuth": [ "read" ] } ],
                "x-zilla-http-kafka": { "filters": [ { "key": "{identity}" } ] },
                "responses": { "200": { "description": "ok" } }
              }
            },
            "/unmapped": {
              "get": {
                "operationId": "unmapped",
                "security": [ { "oauthScheme": [ "read" ] } ],
                "x-zilla-http-kafka": { "filters": [ { "key": "{identity}" } ] },
                "responses": { "200": { "description": "ok" } }
              }
            }
          }
        }
        """;

    private static final String ASYNCAPI_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "broker": { "host": "localhost:9092", "protocol": "kafka" } },
          "channels": {
            "mapped": { "address": "mapped", "messages": { "pet": { "$ref": "#/components/messages/pet" } } },
            "unmapped": { "address": "unmapped", "messages": { "pet": { "$ref": "#/components/messages/pet" } } }
          },
          "operations": {
            "mapped": {
              "action": "receive",
              "channel": { "$ref": "#/channels/mapped" },
              "messages": [ { "$ref": "#/channels/mapped/messages/pet" } ]
            },
            "unmapped": {
              "action": "receive",
              "channel": { "$ref": "#/channels/unmapped" },
              "messages": [ { "$ref": "#/channels/unmapped/messages/pet" } ]
            }
          },
          "components": {
            "messages": { "pet": { "payload": { "type": "object" } } }
          }
        }
        """;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EngineContext context;

    @Mock
    private CatalogHandler openapiCatalog;

    @Mock
    private CatalogHandler asyncapiCatalog;

    private final OpenapiAsyncapiProxyGenerator generator = new OpenapiAsyncapiProxyGenerator();

    private final ToLongFunction<String> resolveId = name -> switch (name)
    {
    case "catalog0" -> 1L;
    case "catalog1" -> 5L;
    case "guard0" -> 2L;
    default -> 3L;
    };

    @Before
    public void initMocks()
    {
        lenient().when(context.supplyCatalog(eq(1L))).thenReturn(openapiCatalog);
        lenient().when(context.supplyCatalog(eq(5L))).thenReturn(asyncapiCatalog);
        lenient().when(context.supplyTypeId(any())).thenReturn(9);
        lenient().when(context.supplyBindingId(any(), any())).thenReturn(42L);
        lenient().when(context.supplyQName(eq(2L))).thenReturn("guard0");
        lenient().when(openapiCatalog.resolve(eq("test"), eq("latest"))).thenReturn(7);
        lenient().when(openapiCatalog.resolve(anyInt())).thenReturn(OPENAPI_SPEC);
        lenient().when(asyncapiCatalog.resolve(eq("test"), eq("latest"))).thenReturn(8);
        lenient().when(asyncapiCatalog.resolve(anyInt())).thenReturn(ASYNCAPI_SPEC);
    }

    private BindingConfig binding(
        Map<String, String> security)
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("openapi-asyncapi")
            .kind(PROXY)
            .options(new OpenapiAsyncapiOptionsConfig(new OpenapiAsyncapiSpecConfig(
                Set.of(new OpenapiSpecificationConfig(
                    "openapi-id",
                    null,
                    List.of(),
                    List.of(new OpenapiCatalogConfig("catalog0", "test", "latest")),
                    security)),
                Set.of(AsyncapiSpecificationConfig.builder()
                    .label("asyncapi-id")
                    .catalog(new AsyncapiCatalogConfig("catalog1", "test", "latest"))
                    .build()))))
            .route()
                .exit("asyncapi_client0")
                .when(new OpenapiAsyncapiConditionConfig("openapi-id", null))
                .with(new OpenapiAsyncapiWithConfig("asyncapi-id", null, null))
                .build()
            .build();
        binding.resolveId = resolveId;
        return binding;
    }

    private RouteConfig routeFor(
        OpenapiAsyncapiCompositeConfig composite,
        String path)
    {
        BindingConfig httpKafka = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "http_kafka_proxy0".equals(b.name))
            .findFirst()
            .orElseThrow();

        return httpKafka.routes.stream()
            .filter(r -> r.when.stream()
                .anyMatch(c -> path.equals(((HttpKafkaConditionConfig) c).path)))
            .findFirst()
            .orElseThrow();
    }

    @Test
    public void shouldGuardMappedOperation()
    {
        OpenapiAsyncapiCompositeConfig composite = generator.generate(new OpenapiAsyncapiBindingConfig(context, binding(
            Map.of("bearerAuth", "guard0"))));

        RouteConfig route = routeFor(composite, "/mapped");
        List<GuardedConfig> guarded = route.guarded;

        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).name, equalTo("guard0"));
        assertThat(guarded.get(0).roles, contains("read"));
    }

    @Test
    public void shouldAllowUnguardedWhenSchemeNotMapped()
    {
        OpenapiAsyncapiCompositeConfig composite = generator.generate(new OpenapiAsyncapiBindingConfig(context, binding(
            Map.of("bearerAuth", "guard0"))));

        RouteConfig route = routeFor(composite, "/unmapped");

        assertThat(route.guarded, empty());
    }

    @Test
    public void shouldAllowUnguardedWhenSecurityMapAbsent()
    {
        OpenapiAsyncapiCompositeConfig composite = generator.generate(new OpenapiAsyncapiBindingConfig(context, binding(null)));

        RouteConfig mapped = routeFor(composite, "/mapped");
        RouteConfig unmapped = routeFor(composite, "/unmapped");

        assertThat(mapped.guarded, empty());
        assertThat(unmapped.guarded, empty());
        assertThat(generator.deniedOperations(), empty());
    }

    @Test
    public void shouldResolveIdentityUsingMappedGuard()
    {
        OpenapiAsyncapiCompositeConfig composite = generator.generate(new OpenapiAsyncapiBindingConfig(context, binding(
            Map.of("bearerAuth", "guard0"))));

        RouteConfig route = routeFor(composite, "/mapped");
        HttpKafkaWithConfig with = (HttpKafkaWithConfig) route.with;

        assertThat(with.fetch.get().filters.get(), hasSize(1));
        assertThat(with.fetch.get().filters.get().get(0).key.get(), equalTo("${guarded['guard0'].identity}"));
    }

    @Test
    public void shouldLeaveIdentityUnresolvedWhenSchemeNotMapped()
    {
        OpenapiAsyncapiCompositeConfig composite = generator.generate(new OpenapiAsyncapiBindingConfig(context, binding(
            Map.of("bearerAuth", "guard0"))));

        RouteConfig route = routeFor(composite, "/unmapped");
        HttpKafkaWithConfig with = (HttpKafkaWithConfig) route.with;

        assertThat(route.guarded, empty());
        assertThat(with.fetch.get().filters.get().get(0).key.get(), equalTo("{identity}"));
    }
}
