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
package io.aklivity.zilla.runtime.binding.openapi.internal.config.composite;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsConditionConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public class OpenapiServerGeneratorTest
{
    private static final String SECURE_SPEC =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": [ { "url": "https://api.example.com:443" } ],
          "paths": {
            "/mapped": {
              "get": {
                "operationId": "mapped",
                "responses": { "200": { "description": "ok" } }
              }
            }
          }
        }
        """;

    private static final String SPEC =
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
                "responses": { "200": { "description": "ok" } }
              }
            },
            "/unmapped": {
              "get": {
                "operationId": "unmapped",
                "security": [ { "oauthScheme": [ "read" ] } ],
                "responses": { "200": { "description": "ok" } }
              }
            }
          }
        }
        """;

    private static final String OVERLAY =
        """
        {
          "overlay": "1.0.0",
          "info": { "title": "test-overlay", "version": "1.0.0" },
          "actions": [
            {
              "target": "$.paths",
              "update": {
                "/added": {
                  "get": {
                    "operationId": "added",
                    "responses": { "200": { "description": "ok" } }
                  }
                }
              }
            }
          ]
        }
        """;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EngineContext context;

    @Mock
    private CatalogHandler catalog;

    private final OpenapiServerGenerator generator = new OpenapiServerGenerator();

    private final ToLongFunction<String> resolveId = name -> switch (name)
    {
    case "catalog0" -> 1L;
    case "guard0" -> 2L;
    default -> 3L;
    };

    @Before
    public void initMocks()
    {
        lenient().when(context.supplyCatalog(eq(1L))).thenReturn(catalog);
        lenient().when(context.supplyBindingId(any(), any())).thenReturn(42L);
        lenient().when(context.supplyQName(eq(2L))).thenReturn("guard0");
        lenient().when(catalog.resolve(eq("test"), eq("latest"))).thenReturn(7);
        lenient().when(catalog.resolve(eq("secure"), eq("latest"))).thenReturn(8);
        lenient().when(catalog.resolve(7)).thenReturn(SPEC);
        lenient().when(catalog.resolve(8)).thenReturn(SECURE_SPEC);
    }

    private BindingConfig binding(
        Map<String, String> security)
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("openapi")
            .kind(SERVER)
            .options(OpenapiOptionsConfig.builder()
                .spec(new OpenapiSpecificationConfig(
                    "petstore",
                    null,
                    List.of(),
                    List.of(new OpenapiCatalogConfig("catalog0", "test", "latest")),
                    security))
                .build())
            .exit("openapi0")
            .build();
        binding.resolveId = resolveId;
        return binding;
    }

    private BindingConfig bindingWithOverlay(
        Map<String, String> security)
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("openapi")
            .kind(SERVER)
            .options(OpenapiOptionsConfig.builder()
                .spec(new OpenapiSpecificationConfig(
                    "petstore",
                    null,
                    List.of(),
                    List.of(new OpenapiCatalogConfig("catalog0", "test", "latest")),
                    security,
                    new OpenapiCatalogConfig("catalog0", "test-overlay", "latest")))
                .build())
            .exit("openapi0")
            .build();
        binding.resolveId = resolveId;
        return binding;
    }

    private BindingConfig bindingSecure()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("openapi")
            .kind(SERVER)
            .options(OpenapiOptionsConfig.builder()
                .spec(new OpenapiSpecificationConfig(
                    "petstore",
                    null,
                    List.of(),
                    List.of(new OpenapiCatalogConfig("catalog0", "secure", "latest")),
                    null))
                .build())
            .exit("openapi0")
            .build();
        binding.resolveId = resolveId;
        return binding;
    }

    private RouteConfig routeFor(
        OpenapiCompositeConfig composite,
        String path)
    {
        BindingConfig httpServer = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "http_server0".equals(b.name))
            .findFirst()
            .orElseThrow();

        return httpServer.routes.stream()
            .filter(r -> r.when.stream()
                .anyMatch(c -> path.equals(((HttpConditionConfig) c).headers.get(":path"))))
            .findFirst()
            .orElseThrow();
    }

    @Test
    public void shouldGuardMappedOperation()
    {
        OpenapiCompositeConfig composite = generator.generate(new OpenapiBindingConfig(context, binding(
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
        OpenapiCompositeConfig composite = generator.generate(new OpenapiBindingConfig(context, binding(
            Map.of("bearerAuth", "guard0"))));

        RouteConfig route = routeFor(composite, "/unmapped");

        assertThat(route.guarded, empty());
    }

    @Test
    public void shouldAllowUnguardedWhenSecurityMapAbsent()
    {
        OpenapiCompositeConfig composite = generator.generate(new OpenapiBindingConfig(context, binding(null)));

        RouteConfig mapped = routeFor(composite, "/mapped");
        RouteConfig unmapped = routeFor(composite, "/unmapped");

        assertThat(mapped.guarded, empty());
        assertThat(unmapped.guarded, empty());
        assertThat(generator.deniedOperations(), empty());
    }

    @Test
    public void shouldNotNpeWhenHttpAuthorizationNotConfigured()
    {
        OpenapiCompositeConfig composite = generator.generate(new OpenapiBindingConfig(context, binding(
            Map.of("bearerAuth", "guard0"))));

        assertThat(composite, notNullValue());
    }

    @Test
    public void shouldApplyOverlayBeforeGeneratingRoutes()
    {
        lenient().when(catalog.resolve(eq("test-overlay"), eq("latest"))).thenReturn(8);
        lenient().when(catalog.resolve(eq(8))).thenReturn(OVERLAY);

        OpenapiCompositeConfig composite = generator.generate(new OpenapiBindingConfig(context, bindingWithOverlay(null)));

        RouteConfig route = routeFor(composite, "/added");

        assertThat(route.guarded, empty());
    }

    @Test
    public void shouldPopulateTlsAuthorityForSniRouting()
    {
        OpenapiCompositeConfig composite = generator.generate(new OpenapiBindingConfig(context, bindingSecure()));

        BindingConfig tlsServer = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "tls_server0".equals(b.name))
            .findFirst()
            .orElseThrow();

        TlsConditionConfig when = (TlsConditionConfig) tlsServer.routes.get(0).when.get(0);

        assertThat(when.authority, equalTo("api.example.com"));
    }
}
