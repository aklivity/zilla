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
import java.util.function.ToLongFunction;

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
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpResourceConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpToolConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiOptionsConfig;
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
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

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
                  { "name": "page", "in": "query", "schema": { "type": "integer" } },
                  { "name": "X-Request-Id", "in": "header", "schema": { "type": "string" } },
                  { "name": "session", "in": "cookie", "schema": { "type": "string" } }
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

    private static final String NOTIFICATIONS_SPEC =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "notifications", "version": "1.0.0" },
          "servers": [ { "url": "https://api.example.com" } ],
          "paths": {
            "/notifications": {
              "get": {
                "operationId": "notifications/list",
                "parameters": [
                  { "name": "unread", "in": "query", "schema": { "type": "boolean" } }
                ],
                "responses": { "200": { "description": "ok",
                  "content": { "application/json": { "schema": { "type": "object" } } } } }
              }
            },
            "/repos/{owner}/{repo}/notifications": {
              "get": {
                "operationId": "repo_notifications/list",
                "parameters": [
                  { "name": "owner", "in": "path", "required": true, "schema": { "type": "string" } },
                  { "name": "repo", "in": "path", "required": true, "schema": { "type": "string" } },
                  { "name": "unread", "in": "query", "schema": { "type": "boolean" } }
                ],
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

    private static final String PETSTORE_SPEC =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "petstore", "version": "1.0.0" },
          "servers": [ { "url": "https://api.petstore.example.com" } ],
          "paths": {
            "/pets": {
              "get": {
                "operationId": "list_pets",
                "responses": { "200": { "description": "ok",
                  "content": { "application/json": { "schema": {
                    "type": "array",
                    "items": { "type": "object", "properties": {
                      "id": { "type": "integer" }, "name": { "type": "string" } } } } } } } }
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

    private final ToLongFunction<String> resolveId = name -> switch (name)
    {
    case "catalog0" -> 1L;
    case "guard1" -> 3L;
    default -> 2L;
    };

    @Before
    public void initMocks()
    {
        lenient().when(context.supplyCatalog(eq(1L))).thenReturn(catalog);
        lenient().when(context.supplyBindingId(any(), any())).thenReturn(42L);
        lenient().when(context.supplyQName(eq(1L))).thenReturn("test:catalog0");
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
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("create_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/create")
                    .build())
                .build()
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .resource("repo://{owner}/{repo}")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("repos/get")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

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

        McpHttpWithConfig toolWith = mcpHttp.routes.stream()
            .map(r -> (McpHttpWithConfig) r.with)
            .filter(w -> "POST".equals(w.headers.get(":method")))
            .findFirst()
            .orElse(null);
        assertThat(toolWith, notNullValue());
        assertThat(toolWith.headers.get(":scheme"), equalTo("https"));
        assertThat(toolWith.headers.get(":authority"), equalTo("api.github.com"));
        assertThat(toolWith.headers.get(":path"), equalTo("/repos/${args.owner}/${args.repo}/pulls"));
        assertThat(toolWith.body, notNullValue());

        McpHttpWithConfig resourceWith = mcpHttp.routes.stream()
            .map(r -> (McpHttpWithConfig) r.with)
            .filter(w -> "GET".equals(w.headers.get(":method")))
            .findFirst()
            .orElse(null);
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
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("create_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/create")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        NamespaceConfig namespace = composite.namespaces.get(0);
        String inputSchema = namespace.catalogs.stream()
            .map(c -> c.options)
            .filter(InlineOptionsConfig.class::isInstance)
            .map(InlineOptionsConfig.class::cast)
            .flatMap(o -> o.subjects.stream())
            .filter(s -> "create_pr-input".equals(s.subject))
            .map(s -> s.schema)
            .findFirst()
            .orElse(null);

        assertThat(inputSchema, notNullValue());
        assertThat(inputSchema, containsString("\"owner\""));
        assertThat(inputSchema, containsString("\"repo\""));
        assertThat(inputSchema, containsString("\"title\""));
        assertThat(inputSchema, containsString("\"owner_body\""));

        JsonObject inputSchemaObject = Json.createReader(new StringReader(inputSchema)).readObject();
        List<String> required = inputSchemaObject.containsKey("required")
            ? inputSchemaObject.getJsonArray("required").getValuesAs(JsonString.class).stream()
                .map(JsonString::getString)
                .toList()
            : List.of();
        assertThat(required, hasItem("owner"));
        assertThat(required, hasItem("repo"));
        assertThat(required, hasItem("title"));
        assertThat(required, not(hasItem("owner_body")));
    }

    @Test
    public void shouldOverrideOutputSchema()
    {
        ModelConfig override = StringModelConfig.builder().build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0"))
                    .build()
                .tool()
                    .name("create_pr")
                    .description("Create a pull request.")
                    .output(override)
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("create_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/create")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        McpHttpToolConfig tool = mcpHttpOptions.tools.stream()
            .filter(t -> "create_pr".equals(t.name))
            .findFirst()
            .orElse(null);
        assertThat(tool, notNullValue());
        assertThat(tool.description, equalTo("Create a pull request."));
        assertThat(tool.output, sameInstance(override));
    }

    @Test
    public void shouldOverrideSummary()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0"))
                    .build()
                .tool()
                    .name("create_pr")
                    .description("Create a pull request.")
                    .summary("Open a new PR")
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("create_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/create")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        McpHttpToolConfig tool = mcpHttpOptions.tools.stream()
            .filter(t -> "create_pr".equals(t.name))
            .findFirst()
            .orElse(null);
        assertThat(tool, notNullValue());
        assertThat(tool.summary, equalTo("Open a new PR"));
    }

    @Test
    public void shouldOverrideInputSchema()
    {
        ModelConfig override = JsonModelConfig.builder()
            .catalog()
                .name("catalog0")
                .schema()
                    .subject("create_pr_input")
                    .version("latest")
                    .build()
                .build()
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0"))
                    .build()
                .tool()
                    .name("create_pr")
                    .description("Create a pull request.")
                    .input(override)
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("create_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/create")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        NamespaceConfig namespace = composite.namespaces.get(0);
        BindingConfig mcpHttp = namespace.bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        McpHttpToolConfig tool = mcpHttpOptions.tools.stream()
            .filter(t -> "create_pr".equals(t.name))
            .findFirst()
            .orElse(null);
        assertThat(tool, notNullValue());
        assertThat(tool.input, notNullValue());
        assertThat(tool.input.model, equalTo("json"));
        assertThat(tool.input.cataloged, hasSize(1));
        // the authored reference names the bare "catalog0" from the caller's own namespace; forwarded
        // into the generated mcp_http0 binding's own (different) namespace, it must be qualified
        // ("test:catalog0"), otherwise it would resolve against that namespace's own local "catalog0"
        assertThat(tool.input.cataloged.get(0).name, equalTo("test:catalog0"));
        assertThat(tool.input.cataloged.get(0).schemas.get(0).subject, equalTo("create_pr_input"));
    }

    @Test
    public void shouldOverrideResourceDescriptionAndOutputSchema()
    {
        ModelConfig override = StringModelConfig.builder().build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .resource()
                    .uri("repo://{owner}/{repo}")
                    .description("A GitHub repository.")
                    .output(override)
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .resource("repo://{owner}/{repo}")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("repos/get")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        McpHttpResourceConfig resource = mcpHttpOptions.resources.stream()
            .filter(r -> "repo://{owner}/{repo}".equals(r.name))
            .findFirst()
            .orElse(null);
        assertThat(resource, notNullValue());
        assertThat(resource.description, equalTo("A GitHub repository."));
        assertThat(resource.output, sameInstance(override));
    }

    @Test
    public void shouldDefaultResourceDescriptionToNullWhenNoOverrideConfigured()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .resource("repo://{owner}/{repo}")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("repos/get")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        McpHttpResourceConfig resource = mcpHttpOptions.resources.get(0);
        assertThat(resource.description, nullValue());
        assertThat(resource.output, notNullValue());
    }

    @Test
    public void shouldClassifyConcreteResourceWithQueryCaptureSuffix()
    {
        lenient().when(catalog.resolve(eq("notifications-api"), eq("latest"))).thenReturn(88);
        lenient().when(catalog.resolve(eq(88))).thenReturn(NOTIFICATIONS_SPEC);

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("notifications")
                    .server("https://api.example.com")
                    .catalog()
                        .name("catalog0")
                        .subject("notifications-api")
                        .version("latest")
                        .build()
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .resource("notifications")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("notifications")
                    .operation("notifications/list")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        McpHttpResourceConfig resource = mcpHttpOptions.resources.get(0);
        assertThat(resource.template, equalTo(false));
        assertThat(resource.uri, equalTo("/notifications{?unread}"));
    }

    @Test
    public void shouldClassifyPathParameterizedResourceAsTemplate()
    {
        lenient().when(catalog.resolve(eq("notifications-api"), eq("latest"))).thenReturn(88);
        lenient().when(catalog.resolve(eq(88))).thenReturn(NOTIFICATIONS_SPEC);

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("notifications")
                    .server("https://api.example.com")
                    .catalog()
                        .name("catalog0")
                        .subject("notifications-api")
                        .version("latest")
                        .build()
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .resource("repo://{owner}/{repo}/notifications")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("notifications")
                    .operation("repo_notifications/list")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        McpHttpResourceConfig resource = mcpHttpOptions.resources.get(0);
        assertThat(resource.template, equalTo(true));
        assertThat(resource.uri, equalTo("/repos/{owner}/{repo}/notifications{?unread}"));
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
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("list_issues")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("issues/list")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
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
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("list_issues")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("issues/list")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
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
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("get_repo")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("repos/get")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
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
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("search_code")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("search/code")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpWithConfig with = mcpHttp.routes.stream()
            .map(r -> (McpHttpWithConfig) r.with)
            .filter(w -> "GET".equals(w.headers.get(":method")))
            .findFirst()
            .orElse(null);
        assertThat(with, notNullValue());
        assertThat(with.headers.get(":path"), equalTo("/search/code?q=${args.q}&${?args.page=page}"));
        assertThat(with.headers.get("x-request-id"), equalTo("${args.X-Request-Id}"));
        assertThat(with.cookies.get("session"), equalTo("${args.session}"));
    }

    @Test
    public void shouldRebindPathParameterViaParams()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("create_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/create")
                    .params(Map.of("owner", "${args.repository.owner}"))
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpWithConfig with = mcpHttp.routes.stream()
            .map(r -> (McpHttpWithConfig) r.with)
            .filter(w -> "POST".equals(w.headers.get(":method")))
            .findFirst()
            .orElse(null);
        assertThat(with, notNullValue());
        assertThat(with.headers.get(":path"), equalTo("/repos/${args.repository.owner}/${args.repo}/pulls"));
    }

    @Test
    public void shouldEmitBodyTemplateWhenBodyPresent()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("create_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/create")
                    .body(Map.of(
                        "title", "${args.title}",
                        "owner", "${args.pr.owner}"))
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpWithConfig with = mcpHttp.routes.stream()
            .map(r -> (McpHttpWithConfig) r.with)
            .filter(w -> "POST".equals(w.headers.get(":method")))
            .findFirst()
            .orElse(null);
        assertThat(with, notNullValue());
        assertThat(with.body, notNullValue());
        assertThat(with.body.model, nullValue());
        assertThat(with.body.template, equalTo(Map.of(
            "title", "${args.title}",
            "owner", "${args.pr.owner}")));
    }

    @Test
    public void shouldRebindRequiredQueryParameterViaParams()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("search_code")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("search/code")
                    .params(Map.of("q", "${args.query.text}"))
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpWithConfig with = mcpHttp.routes.stream()
            .map(r -> (McpHttpWithConfig) r.with)
            .filter(w -> "GET".equals(w.headers.get(":method")))
            .findFirst()
            .orElse(null);
        assertThat(with, notNullValue());
        assertThat(with.headers.get(":path"), equalTo("/search/code?q=${args.query.text}&${?args.page=page}"));
    }

    @Test
    public void shouldRebindOptionalQueryParameterViaParams()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("search_code")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("search/code")
                    .params(Map.of("page", "${args.paging.page}"))
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpWithConfig with = mcpHttp.routes.stream()
            .map(r -> (McpHttpWithConfig) r.with)
            .filter(w -> "GET".equals(w.headers.get(":method")))
            .findFirst()
            .orElse(null);
        assertThat(with, notNullValue());
        assertThat(with.headers.get(":path"), equalTo("/search/code?q=${args.q}&${?args.paging.page=page}"));
    }

    @Test
    public void shouldRebindHeaderParameterViaParams()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("search_code")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("search/code")
                    .params(Map.of("X-Request-Id", "${args.request.id}"))
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpWithConfig with = mcpHttp.routes.stream()
            .map(r -> (McpHttpWithConfig) r.with)
            .filter(w -> "GET".equals(w.headers.get(":method")))
            .findFirst()
            .orElse(null);
        assertThat(with, notNullValue());
        assertThat(with.headers.get("x-request-id"), equalTo("${args.request.id}"));
    }

    @Test
    public void shouldRebindCookieParameterViaParams()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("search_code")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("search/code")
                    .params(Map.of("session", "${args.session.token}"))
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpWithConfig with = mcpHttp.routes.stream()
            .map(r -> (McpHttpWithConfig) r.with)
            .filter(w -> "GET".equals(w.headers.get(":method")))
            .findFirst()
            .orElse(null);
        assertThat(with, notNullValue());
        assertThat(with.cookies.get("session"), equalTo("${args.session.token}"));
    }

    @Test
    public void shouldAggregateMultipleSpecs()
    {
        lenient().when(catalog.resolve(eq("other-api"), eq("latest"))).thenReturn(9);

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("api_a")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0"))
                    .build()
                .spec()
                    .label("api_b")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("other-api")
                        .version("latest")
                        .build()
                    .security(Map.of("oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("create_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("api_a")
                    .operation("pulls/create")
                    .build())
                .build()
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("search_code")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("api_b")
                    .operation("search/code")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        assertThat(composite.namespaces.size(), equalTo(1));
        assertThat(composite.routes.size(), equalTo(1));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        List<String> toolNames = mcpHttpOptions.tools.stream().map(t -> t.name).toList();
        assertThat(toolNames, hasItem("create_pr"));
        assertThat(toolNames, hasItem("search_code"));

        NamespaceConfig namespace = composite.namespaces.get(0);
        String createPrInputSchema = namespace.catalogs.stream()
            .map(c -> c.options)
            .filter(InlineOptionsConfig.class::isInstance)
            .map(InlineOptionsConfig.class::cast)
            .flatMap(o -> o.subjects.stream())
            .filter(s -> "create_pr-input".equals(s.subject))
            .map(s -> s.schema)
            .findFirst()
            .orElse(null);
        String searchCodeInputSchema = namespace.catalogs.stream()
            .map(c -> c.options)
            .filter(InlineOptionsConfig.class::isInstance)
            .map(InlineOptionsConfig.class::cast)
            .flatMap(o -> o.subjects.stream())
            .filter(s -> "search_code-input".equals(s.subject))
            .map(s -> s.schema)
            .findFirst()
            .orElse(null);
        assertThat(createPrInputSchema, notNullValue());
        assertThat(searchCodeInputSchema, notNullValue());
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
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("create_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/create")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        RouteConfig postRoute = mcpHttp.routes.stream()
            .filter(r -> "POST".equals(((McpHttpWithConfig) r.with).headers.get(":method")))
            .findFirst()
            .orElse(null);
        List<GuardedConfig> guarded = postRoute.guarded;
        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).name, equalTo("test0"));
        assertThat(guarded.get(0).roles, containsInAnyOrder("repo", "pr:write"));
    }

    @Test
    public void shouldGuardApiKeySecurityScheme()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("apiKeyScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("create_issue")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("issues/create")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        RouteConfig postRoute = mcpHttp.routes.stream()
            .filter(r -> "POST".equals(((McpHttpWithConfig) r.with).headers.get(":method")))
            .findFirst()
            .orElse(null);
        List<GuardedConfig> guarded = postRoute.guarded;
        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).roles, empty());
    }

    @Test
    public void shouldUnionRolesWhenSameAlternativeSchemesMapToSameGuard()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "apiKeyScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("merge_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/merge")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        RouteConfig putRoute = mcpHttp.routes.stream()
            .filter(r -> "PUT".equals(((McpHttpWithConfig) r.with).headers.get(":method")))
            .findFirst()
            .orElse(null);
        List<GuardedConfig> guarded = putRoute.guarded;
        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).roles, containsInAnyOrder("repo", "pr:write", "admin"));
        assertThat(guarded.get(0).roles, hasSize(3));
    }

    @Test
    public void shouldGuardOperatorDeclaredRouteWithNoSpecSecurity()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .resource("repo://{owner}/{repo}")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("repos/get")
                    .build())
                .guarded()
                    .name("guard1")
                    .role("read")
                    .build()
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        RouteConfig getRoute = mcpHttp.routes.stream()
            .filter(r -> "GET".equals(((McpHttpWithConfig) r.with).headers.get(":method")))
            .findFirst()
            .orElse(null);
        List<GuardedConfig> guarded = getRoute.guarded;
        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).name, equalTo("test1"));
        assertThat(guarded.get(0).roles, containsInAnyOrder("read"));
    }

    @Test
    public void shouldComposeOperatorDeclaredGuardWithDistinctSpecDerivedGuard()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("create_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/create")
                    .build())
                .guarded()
                    .name("guard1")
                    .role("write")
                    .build()
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        RouteConfig postRoute = mcpHttp.routes.stream()
            .filter(r -> "POST".equals(((McpHttpWithConfig) r.with).headers.get(":method")))
            .findFirst()
            .orElse(null);
        List<GuardedConfig> guarded = postRoute.guarded;
        assertThat(guarded, hasSize(2));
        GuardedConfig specDerived = guarded.stream().filter(g -> "test0".equals(g.name)).findFirst().orElse(null);
        GuardedConfig operatorDeclared = guarded.stream().filter(g -> "test1".equals(g.name)).findFirst().orElse(null);
        assertThat(specDerived, notNullValue());
        assertThat(specDerived.roles, containsInAnyOrder("repo", "pr:write"));
        assertThat(operatorDeclared, notNullValue());
        assertThat(operatorDeclared.roles, containsInAnyOrder("write"));
    }

    @Test
    public void shouldUnionOperatorDeclaredRolesWithSpecDerivedGuardForSameGuard()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("create_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/create")
                    .build())
                .guarded()
                    .name("guard0")
                    .role("write")
                    .build()
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        RouteConfig postRoute = mcpHttp.routes.stream()
            .filter(r -> "POST".equals(((McpHttpWithConfig) r.with).headers.get(":method")))
            .findFirst()
            .orElse(null);
        List<GuardedConfig> guarded = postRoute.guarded;
        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).name, equalTo("test0"));
        assertThat(guarded.get(0).roles, containsInAnyOrder("repo", "pr:write", "write"));
    }

    @Test
    public void shouldDenyOperationRequiringMultipleDistinctGuards()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "apiKeyScheme", "guard1"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("merge_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/merge")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        RouteConfig putRoute = mcpHttp.routes.stream()
            .filter(r -> "PUT".equals(((McpHttpWithConfig) r.with).headers.get(":method")))
            .findFirst()
            .orElse(null);
        assertThat(putRoute, nullValue());
        assertThat(generator.deniedOperations(), hasSize(1));
        String reason = generator.deniedOperations().get(0);
        assertThat(reason, containsString("pulls/merge"));
        assertThat(reason, containsString("multiple distinct guards"));
    }

    @Test
    public void shouldDenyOperationWithOrAlternativeSecurity()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "apiKeyScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("close_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/close")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        RouteConfig postRoute = mcpHttp.routes.stream()
            .filter(r -> "POST".equals(((McpHttpWithConfig) r.with).headers.get(":method")))
            .findFirst()
            .orElse(null);
        assertThat(postRoute, nullValue());
        assertThat(generator.deniedOperations(), hasSize(1));
        String reason = generator.deniedOperations().get(0);
        assertThat(reason, containsString("pulls/close"));
        assertThat(reason, containsString("alternative security requirements"));
    }

    @Test
    public void shouldDenyOperationWhenSchemeHasNoGuardConfigured()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("search_code")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("search/code")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        RouteConfig getRoute = mcpHttp.routes.stream()
            .filter(r -> "GET".equals(((McpHttpWithConfig) r.with).headers.get(":method")))
            .findFirst()
            .orElse(null);
        assertThat(getRoute, nullValue());
        assertThat(generator.deniedOperations(), hasSize(1));
        String reason = generator.deniedOperations().get(0);
        assertThat(reason, containsString("search/code"));
        assertThat(reason, containsString("oauthScheme"));
        assertThat(reason, containsString("no guard configured"));
    }

    @Test
    public void shouldDenyOperationWhenSecurityMapAbsentButOperationRequiresSecurity()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("create_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/create")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        RouteConfig postRoute = mcpHttp.routes.stream()
            .filter(r -> "POST".equals(((McpHttpWithConfig) r.with).headers.get(":method")))
            .findFirst()
            .orElse(null);
        assertThat(postRoute, nullValue());
        assertThat(generator.deniedOperations(), hasSize(1));
        String reason = generator.deniedOperations().get(0);
        assertThat(reason, containsString("pulls/create"));
        assertThat(reason, containsString("bearerAuth"));
    }

    @Test
    public void shouldAllowExplicitEmptySecurityWithoutGuard()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("list_issues")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("issues/list")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        RouteConfig getRoute = mcpHttp.routes.stream()
            .filter(r -> "GET".equals(((McpHttpWithConfig) r.with).headers.get(":method")))
            .findFirst()
            .orElse(null);
        assertThat(getRoute.guarded, empty());
    }

    @Test
    public void shouldFallBackToDocumentLevelDefaultSecurity()
    {
        lenient().when(catalog.resolve(eq("internal-api"), eq("latest"))).thenReturn(55);
        lenient().when(catalog.resolve(eq(55))).thenReturn(SPEC_WITH_DEFAULT_SECURITY);

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("internal")
                    .server("https://api.internal.example")
                    .catalog()
                        .name("catalog0")
                        .subject("internal-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("ping_tool")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("internal")
                    .operation("ping")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        RouteConfig getRoute = mcpHttp.routes.stream()
            .filter(r -> "GET".equals(((McpHttpWithConfig) r.with).headers.get(":method")))
            .findFirst()
            .orElse(null);
        List<GuardedConfig> guarded = getRoute.guarded;
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
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .resource("repo://{owner}/{repo}")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("repos/get")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        assertThat(mcpHttp.routes.get(0).guarded, empty());
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
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("repos/get")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
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
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
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
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .tag("reads")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        List<String> toolNames = mcpHttpOptions.tools.stream().map(t -> t.name).toList();
        assertThat(toolNames, containsInAnyOrder("repos_get", "issues_list"));
    }

    @Test
    public void shouldExpandOperationsByGlobPattern()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "apiKeyScheme", "guard0"))
                    .build()
                .build())
            .route()
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/*")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        List<String> toolNames = mcpHttpOptions.tools.stream().map(t -> t.name).toList();
        assertThat(toolNames, containsInAnyOrder("pulls_create", "pulls_merge"));
        assertThat(generator.deniedOperations(), hasSize(1));
        assertThat(generator.deniedOperations().get(0), containsString("pulls/close"));
    }

    @Test
    public void shouldClaimOperationForEarlierExplicitRouteBeforeLaterBulkRoute()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "apiKeyScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("my_pr")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/create")
                    .build())
                .build()
            .route()
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .operation("pulls/*")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
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
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .capability(of("resource"))
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .tag("reads")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
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
                .spec()
                    .label("openapi_github0")
                    .server("https://api.github.com")
                    .catalog()
                        .name("catalog0")
                        .subject("rest-api")
                        .version("latest")
                        .build()
                    .security(Map.of("bearerAuth", "guard0", "oauthScheme", "guard0"))
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .capability(of("tool"))
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("openapi_github0")
                    .tag("reads")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        List<String> toolNames = mcpHttpOptions.tools.stream().map(t -> t.name).toList();
        assertThat(toolNames, containsInAnyOrder("repos_get", "issues_list"));
    }

    @Test
    public void shouldFallBackToMethodPathSlugOnNameCollision()
    {
        lenient().when(catalog.resolve(eq("widgets-api"), eq("latest"))).thenReturn(77);
        lenient().when(catalog.resolve(eq(77))).thenReturn(WIDGETS_SPEC);

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("widgets")
                    .server("https://api.widgets.example")
                    .catalog()
                        .name("catalog0")
                        .subject("widgets-api")
                        .version("latest")
                        .build()
                    .build()
                .build())
            .route()
                .with(McpOpenapiWithConfig.builder()
                    .spec("widgets")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        List<String> toolNames = mcpHttpOptions.tools.stream().map(t -> t.name).toList();
        assertThat(toolNames, containsInAnyOrder("get_widget", "get_widgets_b"));
    }

    @Test
    public void shouldWrapOutputSchemaForArrayTypedResponse()
    {
        lenient().when(catalog.resolve(eq("petstore-api"), eq("latest"))).thenReturn(66);
        lenient().when(catalog.resolve(eq(66))).thenReturn(PETSTORE_SPEC);

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("petstore")
                    .server("https://api.petstore.example.com")
                    .catalog()
                        .name("catalog0")
                        .subject("petstore-api")
                        .version("latest")
                        .build()
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("list_pets")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("petstore")
                    .operation("list_pets")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        NamespaceConfig namespace = composite.namespaces.get(0);
        BindingConfig mcpHttp = namespace.bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        McpHttpToolConfig tool = mcpHttpOptions.tools.stream()
            .filter(t -> "list_pets".equals(t.name))
            .findFirst()
            .orElse(null);

        assertThat(tool, notNullValue());
        assertThat(tool.output, notNullValue());
        assertThat(tool.outputWrapped, equalTo(true));

        String outputSchema = namespace.catalogs.stream()
            .map(c -> c.options)
            .filter(InlineOptionsConfig.class::isInstance)
            .map(InlineOptionsConfig.class::cast)
            .flatMap(o -> o.subjects.stream())
            .filter(s -> "list_pets-output".equals(s.subject))
            .map(s -> s.schema)
            .findFirst()
            .orElse(null);

        assertThat(outputSchema, notNullValue());
        JsonObject outputSchemaObject = Json.createReader(new StringReader(outputSchema)).readObject();
        assertThat(outputSchemaObject.getString("type"), equalTo("object"));
        assertThat(outputSchemaObject.getJsonArray("required").getString(0), equalTo("result"));
        JsonObject result = outputSchemaObject.getJsonObject("properties").getJsonObject("result");
        assertThat(result.getString("type"), equalTo("array"));
    }

    @Test
    public void shouldNotWrapExplicitOutputOverrideEvenForArrayTypedResponse()
    {
        lenient().when(catalog.resolve(eq("petstore-api"), eq("latest"))).thenReturn(66);
        lenient().when(catalog.resolve(eq(66))).thenReturn(PETSTORE_SPEC);

        ModelConfig override = StringModelConfig.builder().build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(CLIENT)
            .options(McpOpenapiOptionsConfig.builder()
                .spec()
                    .label("petstore")
                    .server("https://api.petstore.example.com")
                    .catalog()
                        .name("catalog0")
                        .subject("petstore-api")
                        .version("latest")
                        .build()
                    .build()
                .tool()
                    .name("list_pets")
                    .description("List pets in the store.")
                    .output(override)
                    .build()
                .build())
            .route()
                .when(McpOpenapiConditionConfig.builder()
                    .tool("list_pets")
                    .build())
                .with(McpOpenapiWithConfig.builder()
                    .spec("petstore")
                    .operation("list_pets")
                    .build())
                .build()
            .build();
        binding.resolveId = resolveId;

        McpOpenapiCompositeConfig composite = generator.generate(new McpOpenapiBindingConfig(context, binding));

        BindingConfig mcpHttp = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mcp_http0".equals(b.name))
            .findFirst()
            .orElse(null);
        McpHttpOptionsConfig mcpHttpOptions = (McpHttpOptionsConfig) mcpHttp.options;
        McpHttpToolConfig tool = mcpHttpOptions.tools.stream()
            .filter(t -> "list_pets".equals(t.name))
            .findFirst()
            .orElse(null);

        assertThat(tool, notNullValue());
        assertThat(tool.output, sameInstance(override));
        assertThat(tool.outputWrapped, equalTo(false));
    }
}
