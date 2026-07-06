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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.composite;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.PROXY;
import static java.util.List.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpToolConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiToolConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenapiCompositeConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

public class McpOpenapiCompositeGeneratorTest
{
    private static final String SPEC =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "github", "version": "1.0.0" },
          "servers": [ { "url": "https://api.github.com" } ],
          "components": { "securitySchemes": {
            "bearerAuth": { "type": "http", "scheme": "bearer", "bearerFormat": "jwt" },
            "oauthScheme": { "type": "oauth2", "flows": {} },
            "apiKeyScheme": { "type": "apiKey", "in": "header", "name": "X-Api-Key" }
          } },
          "paths": {
            "/repos/{owner}/{repo}/pulls": {
              "post": {
                "operationId": "pulls/create",
                "security": [ { "bearerAuth": [ "repo", "pr:write" ] } ],
                "parameters": [
                  { "name": "owner", "in": "path", "required": true, "schema": { "type": "string" } },
                  { "name": "repo", "in": "path", "required": true, "schema": { "type": "string" } }
                ],
                "requestBody": {
                  "content": { "application/json": { "schema": {
                    "type": "object", "required": ["title"],
                    "properties": { "title": { "type": "string" }, "owner": { "type": "string" } } } } }
                },
                "responses": { "201": { "description": "created",
                  "content": { "application/json": { "schema": {
                    "type": "object", "properties": { "number": { "type": "integer" },
                      "html_url": { "type": "string" }, "state": { "type": "string" },
                      "title": { "type": "string" } } } } } } }
              }
            },
            "/repos/{owner}/{repo}": {
              "get": {
                "operationId": "repos/get",
                "tags": [ "reads" ],
                "parameters": [
                  { "name": "owner", "in": "path", "required": true, "schema": { "type": "string" } },
                  { "name": "repo", "in": "path", "required": true, "schema": { "type": "string" } }
                ],
                "responses": { "200": { "description": "ok",
                  "content": { "application/json": { "schema": { "type": "object" } } } } }
              }
            },
            "/search/code": {
              "get": {
                "operationId": "search/code",
                "security": [ { "oauthScheme": [ "read" ] } ],
                "parameters": [
                  { "name": "q", "in": "query", "required": true, "schema": { "type": "string" } },
                  { "name": "page", "in": "query", "schema": { "type": "integer" } }
                ],
                "responses": { "200": { "description": "ok",
                  "content": { "application/json": { "schema": { "type": "object" } } } } }
              }
            },
            "/repos/{owner}/{repo}/issues": {
              "post": {
                "operationId": "issues/create",
                "security": [ { "apiKeyScheme": [] } ],
                "parameters": [
                  { "name": "owner", "in": "path", "required": true, "schema": { "type": "string" } },
                  { "name": "repo", "in": "path", "required": true, "schema": { "type": "string" } }
                ],
                "responses": { "201": { "description": "created",
                  "content": { "application/json": { "schema": { "type": "object" } } } } }
              },
              "get": {
                "operationId": "issues/list",
                "tags": [ "reads" ],
                "summary": "List repository issues",
                "description": "Retrieve a paginated list of open and closed issues for the specified repository.",
                "security": [],
                "parameters": [
                  { "name": "owner", "in": "path", "required": true, "schema": { "type": "string" } },
                  { "name": "repo", "in": "path", "required": true, "schema": { "type": "string" } }
                ],
                "responses": { "200": { "description": "ok",
                  "content": { "application/json": { "schema": { "type": "object" } } } } }
              }
            },
            "/repos/{owner}/{repo}/pulls/{number}/merge": {
              "put": {
                "operationId": "pulls/merge",
                "security": [ { "bearerAuth": [ "repo", "pr:write" ],
                  "apiKeyScheme": [ "pr:write", "admin" ] } ],
                "parameters": [
                  { "name": "owner", "in": "path", "required": true, "schema": { "type": "string" } },
                  { "name": "repo", "in": "path", "required": true, "schema": { "type": "string" } },
                  { "name": "number", "in": "path", "required": true, "schema": { "type": "integer" } }
                ],
                "responses": { "200": { "description": "ok",
                  "content": { "application/json": { "schema": { "type": "object" } } } } }
              }
            },
            "/repos/{owner}/{repo}/pulls/{number}/close": {
              "post": {
                "operationId": "pulls/close",
                "security": [ { "bearerAuth": [ "repo" ] }, { "apiKeyScheme": [ "repo" ] } ],
                "parameters": [
                  { "name": "owner", "in": "path", "required": true, "schema": { "type": "string" } },
                  { "name": "repo", "in": "path", "required": true, "schema": { "type": "string" } },
                  { "name": "number", "in": "path", "required": true, "schema": { "type": "integer" } }
                ],
                "responses": { "200": { "description": "ok",
                  "content": { "application/json": { "schema": { "type": "object" } } } } }
              }
            }
          }
        }
        """;

    private static final String SPEC_WITH_DEFAULT_SECURITY =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "internal", "version": "1.0.0" },
          "servers": [ { "url": "https://api.internal.example" } ],
          "security": [ { "bearerAuth": [ "repo" ] } ],
          "components": { "securitySchemes": {
            "bearerAuth": { "type": "http", "scheme": "bearer", "bearerFormat": "jwt" }
          } },
          "paths": {
            "/ping": {
              "get": {
                "operationId": "ping",
                "responses": { "200": { "description": "ok",
                  "content": { "application/json": { "schema": { "type": "object" } } } } }
              }
            }
          }
        }
        """;

    private static final String WIDGETS_SPEC =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "widgets", "version": "1.0.0" },
          "servers": [ { "url": "https://api.widgets.example" } ],
          "paths": {
            "/widgets/a": {
              "get": {
                "operationId": "get_widget",
                "responses": { "200": { "description": "ok",
                  "content": { "application/json": { "schema": { "type": "object" } } } } }
              }
            },
            "/widgets/b": {
              "get": {
                "operationId": "getWidget",
                "responses": { "200": { "description": "ok",
                  "content": { "application/json": { "schema": { "type": "object" } } } } }
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

    private final McpOpenapiCompositeGenerator generator = new McpOpenapiCompositeGenerator("sys:http_client");

    @Before
    public void initMocks()
    {
        lenient().when(context.supplyCatalog(eq(1L))).thenReturn(catalog);
        lenient().when(context.supplyBindingId(any(), any())).thenReturn(42L);
        lenient().when(context.supplyQName(eq(2L))).thenReturn("test0");
        lenient().when(context.supplyQName(eq(3L))).thenReturn("test1");
        lenient().when(catalog.resolve(eq("rest-api"), eq("latest"))).thenReturn(7);
        lenient().when(catalog.resolve(anyInt())).thenReturn(SPEC);
    }

    @Test
    public void shouldGenerateToolAndResource()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                    "https://api.github.com",
                    of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                    Map.of("bearerAuth", "guard0", "oauthScheme", "guard0")))
                .build())
            .routes(of(
                RouteConfig.builder()
                    .order(0)
                    .when(McpOpenapiConditionConfig.builder().tool("create_pr").build())
                    .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("pulls/create").build())
                    .build(),
                RouteConfig.builder()
                    .order(1)
                    .when(McpOpenapiConditionConfig.builder().resource("repo://{owner}/{repo}").build())
                    .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("repos/get").build())
                    .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        assertThat(composite, notNullValue());
        assertThat(composite.namespaces.size(), equalTo(1));
        assertThat(composite.routes.size(), equalTo(1));

        NamespaceConfig namespace = composite.namespaces.get(0);
        BindingConfig mcpHttp = namespace.bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);

        assertThat(mcpHttp, notNullValue());
        assertThat(mcpHttp.type, equalTo("mcp_http"));
        assertThat(mcpHttp.kind, equalTo(PROXY));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        assertThat(mcpHttpOptions.tools, notNullValue());
        McpHttpToolConfig tool = mcpHttpOptions.tools.stream()
            .filter(t -> "create_pr".equals(t.name))
            .findFirst()
            .orElse(null);
        assertThat(tool, notNullValue());
        assertThat(tool.input, notNullValue());
        assertThat(tool.output, notNullValue());

        assertThat(mcpHttpOptions.resources, notNullValue());
        assertThat(mcpHttpOptions.resources.get(0).uri, equalTo("/repos/{owner}/{repo}"));

        assertThat(mcpHttp.routes.get(0).exit, equalTo("sys:http_client"));

        McpHttpWithConfig toolWith = withForMethod(mcpHttp, "POST");
        assertThat(toolWith, notNullValue());
        assertThat(toolWith.headers.get(":scheme"), equalTo("https"));
        assertThat(toolWith.headers.get(":authority"), equalTo("api.github.com"));
        assertThat(toolWith.headers.get(":path"), equalTo("/repos/${args.owner}/${args.repo}/pulls"));
        assertThat(toolWith.body, notNullValue());

        McpHttpWithConfig resourceWith = withForMethod(mcpHttp, "GET");
        assertThat(resourceWith, notNullValue());
        assertThat(resourceWith.headers.get(":path"), equalTo("/repos/${params.owner}/${params.repo}"));
        assertThat(resourceWith.body, nullValue());
    }

    @Test
    public void shouldFlattenInputSchemaWithBodyCollisionSuffix()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                    "https://api.github.com",
                    of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                    Map.of("bearerAuth", "guard0", "oauthScheme", "guard0")))
                .build())
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("create_pr").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("pulls/create").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        NamespaceConfig namespace = composite.namespaces.get(0);
        String inputSchema = inlineSubjectSchema(namespace, "create_pr-input");

        assertThat(inputSchema, notNullValue());
        assertThat(inputSchema, containsString("\"owner\""));
        assertThat(inputSchema, containsString("\"repo\""));
        assertThat(inputSchema, containsString("\"title\""));
        assertThat(inputSchema, containsString("\"owner_body\""));

        List<String> required = requiredOf(inputSchema);
        assertThat(required, hasItem("owner"));
        assertThat(required, hasItem("repo"));
        assertThat(required, hasItem("title"));
        assertThat(required, not(hasItem("owner_body")));
    }

    @Test
    public void shouldOverrideOutputSchema()
    {
        ModelConfig override = StringModelConfig.builder().build();
        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                "https://api.github.com",
                of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                Map.of("bearerAuth", "guard0")))
            .tool(new McpOpenapiToolConfig("create_pr", "Create a pull request.", override))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(options)
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("create_pr").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("pulls/create").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp(composite).options;
        McpHttpToolConfig tool = mcpHttpOptions.tools.stream()
            .filter(t -> "create_pr".equals(t.name))
            .findFirst()
            .orElse(null);
        assertThat(tool, notNullValue());
        assertThat(tool.description, equalTo("Create a pull request."));
        assertThat(tool.output, sameInstance(override));
    }

    @Test
    public void shouldUseOpenapiSummaryAsToolSummary()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                    "https://api.github.com",
                    of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                    Map.of("bearerAuth", "guard0", "oauthScheme", "guard0")))
                .build())
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("list_issues").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("issues/list").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp(composite).options;
        McpHttpToolConfig tool = mcpHttpOptions.tools.stream()
            .filter(t -> "list_issues".equals(t.name))
            .findFirst()
            .orElse(null);
        assertThat(tool, notNullValue());
        assertThat(tool.summary, equalTo("List repository issues"));
    }

    @Test
    public void shouldDefaultToolDescriptionToOperationDescriptionWhenPresent()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                    "https://api.github.com",
                    of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                    Map.of("bearerAuth", "guard0", "oauthScheme", "guard0")))
                .build())
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("list_issues").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("issues/list").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp(composite).options;
        McpHttpToolConfig tool = mcpHttpOptions.tools.stream()
            .filter(t -> "list_issues".equals(t.name))
            .findFirst()
            .orElse(null);
        assertThat(tool, notNullValue());
        assertThat(tool.description,
            equalTo("Retrieve a paginated list of open and closed issues for the specified repository."));
    }

    @Test
    public void shouldDefaultToolDescriptionToOperationIdWhenDescriptionAbsent()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                    "https://api.github.com",
                    of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                    Map.of("bearerAuth", "guard0", "oauthScheme", "guard0")))
                .build())
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("get_repo").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("repos/get").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp(composite).options;
        McpHttpToolConfig tool = mcpHttpOptions.tools.stream()
            .filter(t -> "get_repo".equals(t.name))
            .findFirst()
            .orElse(null);
        assertThat(tool, notNullValue());
        assertThat(tool.description, equalTo("repos/get"));
        assertThat(tool.summary, equalTo("Call repos/get"));
    }

    @Test
    public void shouldInterpolateQueryParameters()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                    "https://api.github.com",
                    of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                    Map.of("bearerAuth", "guard0", "oauthScheme", "guard0")))
                .build())
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("search_code").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("search/code").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        McpHttpWithConfig with = withForMethod(mcpHttp(composite), "GET");
        assertThat(with, notNullValue());
        assertThat(with.headers.get(":path"), equalTo("/search/code?q=${args.q}&page=${args.page}"));
    }

    @Test
    public void shouldAggregateMultipleSpecs()
    {
        lenient().when(catalog.resolve(eq("other-api"), eq("latest"))).thenReturn(9);

        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("api_a",
                "https://api.github.com",
                of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                Map.of("bearerAuth", "guard0")))
            .spec(new McpOpenapiSpecificationConfig("api_b",
                "https://api.github.com",
                of(new McpOpenapiCatalogConfig("catalog0", "other-api", "latest")),
                Map.of("oauthScheme", "guard0")))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(options)
            .routes(of(
                RouteConfig.builder()
                    .order(0)
                    .when(McpOpenapiConditionConfig.builder().tool("create_pr").build())
                    .with(McpOpenapiWithConfig.builder().spec("api_a").operation("pulls/create").build())
                    .build(),
                RouteConfig.builder()
                    .order(1)
                    .when(McpOpenapiConditionConfig.builder().tool("search_code").build())
                    .with(McpOpenapiWithConfig.builder().spec("api_b").operation("search/code").build())
                    .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        assertThat(composite.namespaces.size(), equalTo(1));
        assertThat(composite.routes.size(), equalTo(1));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp(composite).options;
        List<String> toolNames = mcpHttpOptions.tools.stream().map(t -> t.name).toList();
        assertThat(toolNames, hasItem("create_pr"));
        assertThat(toolNames, hasItem("search_code"));

        NamespaceConfig namespace = composite.namespaces.get(0);
        assertThat(inlineSubjectSchema(namespace, "create_pr-input"), notNullValue());
        assertThat(inlineSubjectSchema(namespace, "search_code-input"), notNullValue());
    }

    @Test
    public void shouldPassThroughRolesForGuardedScheme()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                    "https://api.github.com",
                    of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                    Map.of("bearerAuth", "guard0", "oauthScheme", "guard0")))
                .build())
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("create_pr").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("pulls/create").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        List<GuardedConfig> guarded = routeForMethod(mcpHttp(composite), "POST").guarded;
        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).name, equalTo("test0"));
        assertThat(guarded.get(0).roles, containsInAnyOrder("repo", "pr:write"));
    }

    @Test
    public void shouldGuardApiKeySecurityScheme()
    {
        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                "https://api.github.com",
                of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                Map.of("apiKeyScheme", "guard0")))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(options)
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("create_issue").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("issues/create").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        List<GuardedConfig> guarded = routeForMethod(mcpHttp(composite), "POST").guarded;
        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).roles, empty());
    }

    @Test
    public void shouldUnionRolesWhenSameAlternativeSchemesMapToSameGuard()
    {
        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                "https://api.github.com",
                of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                Map.of("bearerAuth", "guard0", "apiKeyScheme", "guard0")))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(options)
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("merge_pr").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("pulls/merge").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        List<GuardedConfig> guarded = routeForMethod(mcpHttp(composite), "PUT").guarded;
        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).roles, containsInAnyOrder("repo", "pr:write", "admin"));
        assertThat(guarded.get(0).roles, hasSize(3));
    }

    @Test
    public void shouldDenyOperationRequiringMultipleDistinctGuards()
    {
        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                "https://api.github.com",
                of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                Map.of("bearerAuth", "guard0", "apiKeyScheme", "guard1")))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(options)
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("merge_pr").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("pulls/merge").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        assertThat(routeForMethod(mcpHttp(composite), "PUT"), nullValue());
        assertThat(generator.deniedOperations(), hasSize(1));
        String reason = generator.deniedOperations().get(0);
        assertThat(reason, containsString("pulls/merge"));
        assertThat(reason, containsString("multiple distinct guards"));
    }

    @Test
    public void shouldDenyOperationWithOrAlternativeSecurity()
    {
        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                "https://api.github.com",
                of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                Map.of("bearerAuth", "guard0", "apiKeyScheme", "guard0")))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(options)
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("close_pr").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("pulls/close").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        assertThat(routeForMethod(mcpHttp(composite), "POST"), nullValue());
        assertThat(generator.deniedOperations(), hasSize(1));
        String reason = generator.deniedOperations().get(0);
        assertThat(reason, containsString("pulls/close"));
        assertThat(reason, containsString("alternative security requirements"));
    }

    @Test
    public void shouldDenyOperationWhenSchemeHasNoGuardConfigured()
    {
        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                "https://api.github.com",
                of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                Map.of("bearerAuth", "guard0")))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(options)
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("search_code").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("search/code").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        assertThat(routeForMethod(mcpHttp(composite), "GET"), nullValue());
        assertThat(generator.deniedOperations(), hasSize(1));
        String reason = generator.deniedOperations().get(0);
        assertThat(reason, containsString("search/code"));
        assertThat(reason, containsString("oauthScheme"));
        assertThat(reason, containsString("no guard configured"));
    }

    @Test
    public void shouldDenyOperationWhenSecurityMapAbsentButOperationRequiresSecurity()
    {
        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                "https://api.github.com",
                of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest"))))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(options)
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("create_pr").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("pulls/create").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        assertThat(routeForMethod(mcpHttp(composite), "POST"), nullValue());
        assertThat(generator.deniedOperations(), hasSize(1));
        String reason = generator.deniedOperations().get(0);
        assertThat(reason, containsString("pulls/create"));
        assertThat(reason, containsString("bearerAuth"));
    }

    @Test
    public void shouldAllowExplicitEmptySecurityWithoutGuard()
    {
        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                "https://api.github.com",
                of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest"))))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(options)
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("list_issues").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("issues/list").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        assertThat(routeForMethod(mcpHttp(composite), "GET").guarded, empty());
    }

    @Test
    public void shouldFallBackToDocumentLevelDefaultSecurity()
    {
        lenient().when(catalog.resolve(eq("internal-api"), eq("latest"))).thenReturn(55);
        lenient().when(catalog.resolve(eq(55))).thenReturn(SPEC_WITH_DEFAULT_SECURITY);

        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("internal",
                "https://api.internal.example",
                of(new McpOpenapiCatalogConfig("catalog0", "internal-api", "latest")),
                Map.of("bearerAuth", "guard0")))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(options)
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().tool("ping_tool").build())
                .with(McpOpenapiWithConfig.builder().spec("internal").operation("ping").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        List<GuardedConfig> guarded = routeForMethod(mcpHttp(composite), "GET").guarded;
        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).roles, containsInAnyOrder("repo"));
    }

    @Test
    public void shouldNotGuardOperationWithNoSecurityRequirement()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                    "https://api.github.com",
                    of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                    Map.of("bearerAuth", "guard0", "oauthScheme", "guard0")))
                .build())
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().resource("repo://{owner}/{repo}").build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("repos/get").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        assertThat(mcpHttp(composite).routes.get(0).guarded, empty());
    }

    @Test
    public void shouldDefaultToolNameToSnakeCasedOperationId()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                    "https://api.github.com",
                    of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                    Map.of("bearerAuth", "guard0", "oauthScheme", "guard0")))
                .build())
            .routes(of(RouteConfig.builder()
                .order(0)
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("repos/get").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp(composite).options;
        List<String> toolNames = mcpHttpOptions.tools.stream().map(t -> t.name).toList();
        assertThat(toolNames, containsInAnyOrder("repos_get"));
    }

    @Test
    public void shouldExpandAllOperationsForSpecOnlyRoute()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                    "https://api.github.com",
                    of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                    Map.of("bearerAuth", "guard0", "oauthScheme", "guard0")))
                .build())
            .routes(of(RouteConfig.builder()
                .order(0)
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp(composite).options;
        List<String> toolNames = mcpHttpOptions.tools.stream().map(t -> t.name).toList();
        assertThat(toolNames, containsInAnyOrder("pulls_create", "repos_get", "search_code", "issues_list"));
        assertThat(mcpHttpOptions.resources, nullValue());
    }

    @Test
    public void shouldExpandOperationsByTag()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                    "https://api.github.com",
                    of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                    Map.of("bearerAuth", "guard0", "oauthScheme", "guard0")))
                .build())
            .routes(of(RouteConfig.builder()
                .order(0)
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").tag("reads").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp(composite).options;
        List<String> toolNames = mcpHttpOptions.tools.stream().map(t -> t.name).toList();
        assertThat(toolNames, containsInAnyOrder("repos_get", "issues_list"));
    }

    @Test
    public void shouldExpandOperationsByGlobPattern()
    {
        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                "https://api.github.com",
                of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                Map.of("bearerAuth", "guard0", "apiKeyScheme", "guard0")))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(options)
            .routes(of(RouteConfig.builder()
                .order(0)
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("pulls/*").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp(composite).options;
        List<String> toolNames = mcpHttpOptions.tools.stream().map(t -> t.name).toList();
        assertThat(toolNames, containsInAnyOrder("pulls_create", "pulls_merge"));
        assertThat(generator.deniedOperations(), hasSize(1));
        assertThat(generator.deniedOperations().get(0), containsString("pulls/close"));
    }

    @Test
    public void shouldClaimOperationForEarlierExplicitRouteBeforeLaterBulkRoute()
    {
        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                "https://api.github.com",
                of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                Map.of("bearerAuth", "guard0", "apiKeyScheme", "guard0")))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(options)
            .routes(of(
                RouteConfig.builder()
                    .order(0)
                    .when(McpOpenapiConditionConfig.builder().tool("my_pr").build())
                    .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("pulls/create").build())
                    .build(),
                RouteConfig.builder()
                    .order(1)
                    .with(McpOpenapiWithConfig.builder().spec("openapi_github0").operation("pulls/*").build())
                    .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp(composite).options;
        List<String> toolNames = mcpHttpOptions.tools.stream().map(t -> t.name).toList();
        assertThat(toolNames, containsInAnyOrder("my_pr", "pulls_merge"));
        assertThat(toolNames, not(hasItem("pulls_create")));
    }

    @Test
    public void shouldExcludeBulkOperationsFilteredOutByCapability()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                    "https://api.github.com",
                    of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                    Map.of("bearerAuth", "guard0", "oauthScheme", "guard0")))
                .build())
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().capability(of("resource")).build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").tag("reads").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp(composite).options;
        assertThat(mcpHttpOptions.tools, nullValue());
        assertThat(mcpHttpOptions.resources, nullValue());
    }

    @Test
    public void shouldIncludeBulkOperationsAdmittedByCapability()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                    "https://api.github.com",
                    of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest")),
                    Map.of("bearerAuth", "guard0", "oauthScheme", "guard0")))
                .build())
            .routes(of(RouteConfig.builder()
                .order(0)
                .when(McpOpenapiConditionConfig.builder().capability(of("tool")).build())
                .with(McpOpenapiWithConfig.builder().spec("openapi_github0").tag("reads").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp(composite).options;
        List<String> toolNames = mcpHttpOptions.tools.stream().map(t -> t.name).toList();
        assertThat(toolNames, containsInAnyOrder("repos_get", "issues_list"));
    }

    @Test
    public void shouldFallBackToMethodPathSlugOnNameCollision()
    {
        lenient().when(catalog.resolve(eq("widgets-api"), eq("latest"))).thenReturn(77);
        lenient().when(catalog.resolve(eq(77))).thenReturn(WIDGETS_SPEC);

        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("widgets",
                "https://api.widgets.example",
                of(new McpOpenapiCatalogConfig("catalog0", "widgets-api", "latest"))))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(options)
            .routes(of(RouteConfig.builder()
                .order(0)
                .with(McpOpenapiWithConfig.builder().spec("widgets").build())
                .build()))
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        case "guard1" -> 3L;
        default -> 2L;
        };

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp(composite).options;
        List<String> toolNames = mcpHttpOptions.tools.stream().map(t -> t.name).toList();
        assertThat(toolNames, containsInAnyOrder("get_widget", "get_widgets_b"));
    }

    private static BindingConfig mcpHttp(
        McpOpenapiCompositeConfig composite)
    {
        return composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
    }

    private static McpHttpWithConfig withForMethod(
        BindingConfig mcpHttp,
        String method)
    {
        return mcpHttp.routes.stream()
            .map(r -> (McpHttpWithConfig) r.with)
            .filter(w -> method.equals(w.headers.get(":method")))
            .findFirst()
            .orElse(null);
    }

    private static RouteConfig routeForMethod(
        BindingConfig mcpHttp,
        String method)
    {
        return mcpHttp.routes.stream()
            .filter(r -> method.equals(((McpHttpWithConfig) r.with).headers.get(":method")))
            .findFirst()
            .orElse(null);
    }

    private static String inlineSubjectSchema(
        NamespaceConfig namespace,
        String subject)
    {
        return namespace.catalogs.stream()
            .map(c -> c.options)
            .filter(InlineOptionsConfig.class::isInstance)
            .map(InlineOptionsConfig.class::cast)
            .flatMap(o -> o.subjects.stream())
            .filter(s -> subject.equals(s.subject))
            .map(s -> s.schema)
            .findFirst()
            .orElse(null);
    }

    private static List<String> requiredOf(
        String schema)
    {
        JsonObject object = Json.createReader(new StringReader(schema)).readObject();
        return object.containsKey("required")
            ? object.getJsonArray("required").getValuesAs(JsonString.class).stream()
                .map(JsonString::getString)
                .toList()
            : List.of();
    }
}
