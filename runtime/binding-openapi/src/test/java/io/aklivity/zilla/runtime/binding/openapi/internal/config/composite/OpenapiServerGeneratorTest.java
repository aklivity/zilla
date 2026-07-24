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
package io.aklivity.zilla.runtime.binding.openapi.internal.config.composite;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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

import io.aklivity.zilla.config.binding.http.HttpAuthorizationConfig;
import io.aklivity.zilla.config.binding.http.HttpConditionConfig;
import io.aklivity.zilla.config.binding.http.HttpOptionsConfig;
import io.aklivity.zilla.config.binding.openapi.OpenapiConditionConfig;
import io.aklivity.zilla.config.binding.openapi.OpenapiOptionsConfig;
import io.aklivity.zilla.config.binding.tls.TlsConditionConfig;
import io.aklivity.zilla.config.binding.tls.TlsOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.BindingConfigBuilder;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

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
            "oauthScheme": { "type": "oauth2", "flows": {} },
            "apiKeyHeaderAuth": { "type": "apiKey", "in": "header", "name": "X-Api-Key" },
            "apiKeyQueryAuth": { "type": "apiKey", "in": "query", "name": "api_key" },
            "apiKeyCookieAuth": { "type": "apiKey", "in": "cookie", "name": "api_key" }
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

    private static final String MULTI_SERVER_SPEC =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": [
            { "url": "http://localhost:9090/prod" },
            { "url": "http://localhost:8080/qa" }
          ],
          "paths": {
            "/pets": {
              "get": {
                "operationId": "listPets",
                "responses": { "200": { "description": "ok" } }
              }
            },
            "/orders": {
              "get": {
                "operationId": "listOrders",
                "servers": [ { "url": "http://localhost:8080/qa" } ],
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
        lenient().when(catalog.resolve(eq("multi"), eq("latest"))).thenReturn(9);
        lenient().when(catalog.resolve(7)).thenReturn(SPEC);
        lenient().when(catalog.resolve(8)).thenReturn(SECURE_SPEC);
        lenient().when(catalog.resolve(9)).thenReturn(MULTI_SERVER_SPEC);
    }

    private OpenapiBindingConfig newBindingConfig(
        BindingConfig binding)
    {
        return new OpenapiBindingConfig(
            context, binding, new HttpBeginExFW(), new HttpBeginExFW.Builder(),
            new UnsafeBufferEx(new byte[1024]), 0);
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
                    List.of("http://localhost:8080"),
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
                    List.of("http://localhost:8080"),
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
                    List.of("https://api.example.com:443"),
                    List.of(new OpenapiCatalogConfig("catalog0", "secure", "latest")),
                    null))
                .build())
            .exit("openapi0")
            .build();
        binding.resolveId = resolveId;
        return binding;
    }

    private BindingConfig bindingMultiServers(
        ConditionConfig when)
    {
        BindingConfigBuilder<BindingConfig> builder = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("openapi")
            .kind(SERVER)
            .options(OpenapiOptionsConfig.builder()
                .spec(new OpenapiSpecificationConfig(
                    "petstore",
                    List.of("http://localhost:8080"),
                    List.of(new OpenapiCatalogConfig("catalog0", "multi", "latest")),
                    null))
                .build());

        if (when != null)
        {
            builder
                .route()
                    .exit("openapi0")
                    .when(when)
                    .build();
        }
        else
        {
            builder.exit("openapi0");
        }

        BindingConfig binding = builder.build();
        binding.resolveId = resolveId;
        return binding;
    }

    private List<RouteConfig> routesFor(
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
            .toList();
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

    private HttpOptionsConfig httpServerOptions(
        OpenapiCompositeConfig composite)
    {
        BindingConfig httpServer = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "http_server0".equals(b.name))
            .findFirst()
            .orElseThrow();

        return (HttpOptionsConfig) httpServer.options;
    }

    @Test
    public void shouldGuardMappedOperation()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding(
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
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding(
            Map.of("bearerAuth", "guard0"))));

        RouteConfig route = routeFor(composite, "/unmapped");

        assertThat(route.guarded, empty());
    }

    @Test
    public void shouldAllowUnguardedWhenSecurityMapAbsent()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding(null)));

        RouteConfig mapped = routeFor(composite, "/mapped");
        RouteConfig unmapped = routeFor(composite, "/unmapped");

        assertThat(mapped.guarded, empty());
        assertThat(unmapped.guarded, empty());
        assertThat(generator.deniedOperations(), empty());
    }

    @Test
    public void shouldNotNpeWhenHttpAuthorizationNotConfigured()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding(
            Map.of("bearerAuth", "guard0"))));

        assertThat(composite, notNullValue());
    }

    @Test
    public void shouldSynthesizeHttpAuthorizationFromBearerScheme()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding(
            Map.of("bearerAuth", "guard0"))));

        HttpAuthorizationConfig authorization = httpServerOptions(composite).authorization;

        assertThat(authorization, notNullValue());
        assertThat(authorization.name, equalTo("guard0"));
        assertThat(authorization.credentials.headers, hasSize(1));
        assertThat(authorization.credentials.headers.get(0).name, equalTo("authorization"));
        assertThat(authorization.credentials.headers.get(0).pattern, equalTo("Bearer {credentials}"));
    }

    @Test
    public void shouldNotSynthesizeHttpAuthorizationWhenSecurityMapAbsent()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding(null)));

        assertThat(httpServerOptions(composite).authorization, nullValue());
    }

    @Test
    public void shouldNotSynthesizeHttpAuthorizationForUnsupportedSchemeType()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding(
            Map.of("oauthScheme", "guard0"))));

        assertThat(httpServerOptions(composite).authorization, nullValue());
    }

    @Test
    public void shouldDenyUnsupportedSecuritySchemeType()
    {
        generator.generate(newBindingConfig(binding(
            Map.of("oauthScheme", "guard0"))));

        assertThat(generator.deniedOperations(), hasSize(1));
    }

    @Test
    public void shouldDenyUnresolvableSecurityScheme()
    {
        generator.generate(newBindingConfig(binding(
            Map.of("missingScheme", "guard0"))));

        assertThat(generator.deniedOperations(), hasSize(1));
    }

    @Test
    public void shouldSynthesizeHttpAuthorizationFromApiKeyHeaderScheme()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding(
            Map.of("apiKeyHeaderAuth", "guard0"))));

        HttpAuthorizationConfig authorization = httpServerOptions(composite).authorization;

        assertThat(authorization, notNullValue());
        assertThat(authorization.name, equalTo("guard0"));
        assertThat(authorization.credentials.headers, hasSize(1));
        assertThat(authorization.credentials.headers.get(0).name, equalTo("X-Api-Key"));
        assertThat(authorization.credentials.headers.get(0).pattern, equalTo("{credentials}"));
    }

    @Test
    public void shouldSynthesizeHttpAuthorizationFromApiKeyQueryScheme()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding(
            Map.of("apiKeyQueryAuth", "guard0"))));

        HttpAuthorizationConfig authorization = httpServerOptions(composite).authorization;

        assertThat(authorization, notNullValue());
        assertThat(authorization.credentials.parameters, hasSize(1));
        assertThat(authorization.credentials.parameters.get(0).name, equalTo("api_key"));
        assertThat(authorization.credentials.parameters.get(0).pattern, equalTo("{credentials}"));
    }

    @Test
    public void shouldSynthesizeHttpAuthorizationFromApiKeyCookieScheme()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding(
            Map.of("apiKeyCookieAuth", "guard0"))));

        HttpAuthorizationConfig authorization = httpServerOptions(composite).authorization;

        assertThat(authorization, notNullValue());
        assertThat(authorization.credentials.cookies, hasSize(1));
        assertThat(authorization.credentials.cookies.get(0).name, equalTo("api_key"));
        assertThat(authorization.credentials.cookies.get(0).pattern, equalTo("{credentials}"));
    }

    @Test
    public void shouldApplyOverlayBeforeGeneratingRoutes()
    {
        lenient().when(catalog.resolve(eq("test-overlay"), eq("latest"))).thenReturn(8);
        lenient().when(catalog.resolve(eq(8))).thenReturn(OVERLAY);

        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(bindingWithOverlay(null)));

        RouteConfig route = routeFor(composite, "/added");

        assertThat(route.guarded, empty());
    }

    @Test
    public void shouldPopulateTlsAuthorityForSniRouting()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(bindingSecure()));

        BindingConfig tlsServer = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "tls_server0".equals(b.name))
            .findFirst()
            .orElseThrow();

        TlsConditionConfig when = (TlsConditionConfig) tlsServer.routes.get(0).when.get(0);

        assertThat(when.authority, equalTo("api.example.com"));
    }

    @Test
    public void shouldComputeTlsAlpnFromHttpVersions()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(bindingSecure()));

        BindingConfig tlsServer = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "tls_server0".equals(b.name))
            .findFirst()
            .orElseThrow();

        TlsOptionsConfig options = (TlsOptionsConfig) tlsServer.options;

        assertThat(options.alpn, contains("h2", "http/1.1"));
    }

    @Test
    public void shouldGenerateSingleRoutePerOperationRegardlessOfCanonicalServerCount()
    {
        BindingConfig binding = bindingMultiServers(null);

        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding));

        assertThat(routesFor(composite, "/pets"), hasSize(1));
    }

    @Test
    public void shouldIncludeOperationWhenServersMatchWhenClause()
    {
        ConditionConfig when = OpenapiConditionConfig.builder()
            .server()
                .url("http://localhost:9090/prod")
                .build()
            .build();
        BindingConfig binding = bindingMultiServers(when);

        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding));

        assertThat(routesFor(composite, "/pets"), hasSize(1));
    }

    @Test
    public void shouldExcludeOperationViaWhenServers()
    {
        ConditionConfig when = OpenapiConditionConfig.builder()
            .server()
                .url("http://localhost:9090/prod")
                .build()
            .build();
        BindingConfig binding = bindingMultiServers(when);

        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding));

        assertThat(routesFor(composite, "/orders"), empty());
    }
}
