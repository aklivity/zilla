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

import static io.aklivity.zilla.config.engine.KindConfig.CLIENT;
import static org.hamcrest.MatcherAssert.assertThat;
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
import io.aklivity.zilla.config.binding.http.HttpOptionsConfig;
import io.aklivity.zilla.config.binding.openapi.OpenapiOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public class OpenapiClientGeneratorTest
{
    private static final String SPEC =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": [ { "url": "http://localhost:8080" } ],
          "components": { "securitySchemes": {
            "bearerAuth": { "type": "http", "scheme": "bearer", "bearerFormat": "jwt" },
            "oauthScheme": { "type": "oauth2", "flows": {} },
            "apiKeyHeaderAuth": { "type": "apiKey", "in": "header", "name": "X-Api-Key" }
          } },
          "paths": {
            "/mapped": {
              "get": {
                "operationId": "mapped",
                "security": [ { "bearerAuth": [ "read" ] } ],
                "responses": { "200": { "description": "ok" } }
              }
            }
          }
        }
        """;

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

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EngineContext context;

    @Mock
    private CatalogHandler catalog;

    private final OpenapiClientGenerator generator = new OpenapiClientGenerator();

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
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("openapi")
            .kind(CLIENT)
            .options(OpenapiOptionsConfig.builder()
                .spec(new OpenapiSpecificationConfig(
                    "petstore",
                    List.of("http://localhost:8080"),
                    List.of(new OpenapiCatalogConfig("catalog0", "test", "latest")),
                    security))
                .build())
            .build();
        binding.resolveId = resolveId;
        return binding;
    }

    private BindingConfig bindingSecure()
    {
        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("openapi")
            .kind(CLIENT)
            .options(OpenapiOptionsConfig.builder()
                .spec(new OpenapiSpecificationConfig(
                    "petstore",
                    List.of("https://api.example.com:443"),
                    List.of(new OpenapiCatalogConfig("catalog0", "secure", "latest")),
                    null))
                .build())
            .build();
        binding.resolveId = resolveId;
        return binding;
    }

    private HttpOptionsConfig httpClientOptions(
        OpenapiCompositeConfig composite)
    {
        BindingConfig httpClient = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "http_client0".equals(b.name))
            .findFirst()
            .orElseThrow();

        return (HttpOptionsConfig) httpClient.options;
    }

    @Test
    public void shouldSynthesizeHttpAuthorizationFromBearerScheme()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding(
            Map.of("bearerAuth", "guard0"))));

        HttpAuthorizationConfig authorization = httpClientOptions(composite).authorization;

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

        assertThat(httpClientOptions(composite).authorization, nullValue());
    }

    @Test
    public void shouldSynthesizeHttpAuthorizationFromApiKeyHeaderScheme()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(binding(
            Map.of("apiKeyHeaderAuth", "guard0"))));

        HttpAuthorizationConfig authorization = httpClientOptions(composite).authorization;

        assertThat(authorization, notNullValue());
        assertThat(authorization.name, equalTo("guard0"));
        assertThat(authorization.credentials.headers, hasSize(1));
        assertThat(authorization.credentials.headers.get(0).name, equalTo("X-Api-Key"));
        assertThat(authorization.credentials.headers.get(0).pattern, equalTo("{credentials}"));
    }

    @Test
    public void shouldGenerateTlsClientWithoutExplicitOptions()
    {
        OpenapiCompositeConfig composite = generator.generate(newBindingConfig(bindingSecure()));

        BindingConfig tlsClient = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "tls_client0".equals(b.name))
            .findFirst()
            .orElseThrow();

        assertThat(tlsClient.options, nullValue());
    }
}
