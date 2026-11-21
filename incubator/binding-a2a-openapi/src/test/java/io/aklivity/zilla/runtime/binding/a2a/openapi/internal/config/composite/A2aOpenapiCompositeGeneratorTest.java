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
package io.aklivity.zilla.runtime.binding.a2a.openapi.internal.config.composite;

import static io.aklivity.zilla.config.engine.KindConfig.PROXY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

import io.aklivity.zilla.config.binding.a2a.http.A2aHttpConditionConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpOptionsConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpSkillConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpWithConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiAuthorizationConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiConditionConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiOptionsConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiOptionsConfigBuilder;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiWithConfig;
import io.aklivity.zilla.config.catalog.inline.InlineOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ModelConfigAdapter;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.config.A2aOpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.config.A2aOpenapiCompositeConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public class A2aOpenapiCompositeGeneratorTest
{
    private static final String SHOP =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "Shop", "description": "Shop API", "version": "2.1.0" },
          "servers": [ { "url": "https://api.shop.example" } ],
          "components": { "securitySchemes": {
            "bearerAuth": { "type": "http", "scheme": "bearer", "bearerFormat": "jwt" },
            "apiKeyAuth": { "type": "apiKey", "in": "header", "name": "X-Api-Key" }
          } },
          "paths": {
            "/orders/{orderId}": {
              "get": {
                "operationId": "orders/read",
                "summary": "Read an order",
                "description": "Returns the order with the given identifier.",
                "tags": [ "orders" ],
                "parameters": [
                  { "name": "orderId", "in": "path", "required": true, "schema": { "type": "string" } }
                ],
                "responses": {
                  "200": { "description": "ok",
                    "content": { "application/json": { "schema": {
                      "type": "object", "properties": { "id": { "type": "string" }, "status": { "type": "string" } } } } } }
                }
              }
            },
            "/items": {
              "get": {
                "operationId": "searchItems",
                "summary": "Search items",
                "tags": [ "items" ],
                "parameters": [
                  { "name": "q", "in": "query", "required": true, "schema": { "type": "string" } },
                  { "name": "limit", "in": "query", "schema": { "type": "integer" } }
                ],
                "responses": {
                  "200": { "description": "ok",
                    "content": { "application/json": { "schema": {
                      "type": "object", "properties": { "items": { "type": "array", "items": { "type": "string" } } } } } } }
                }
              }
            },
            "/repos/{owner}/{repo}/pulls": {
              "post": {
                "operationId": "pulls/create",
                "summary": "Create a pull request",
                "tags": [ "pulls" ],
                "security": [ { "bearerAuth": [ "repo", "pr:write" ] } ],
                "parameters": [
                  { "name": "owner", "in": "path", "required": true, "schema": { "type": "string" } },
                  { "name": "repo", "in": "path", "required": true, "schema": { "type": "string" } }
                ],
                "requestBody": {
                  "required": true,
                  "content": { "application/json": { "schema": {
                    "type": "object", "required": [ "title" ],
                    "properties": { "title": { "type": "string" }, "owner": { "type": "string" } } } } }
                },
                "responses": {
                  "201": { "description": "created",
                    "content": { "application/xml": { "schema": { "type": "string" } },
                      "application/json": { "schema": {
                        "type": "object", "properties": { "number": { "type": "integer" } } } } } }
                }
              }
            },
            "/events": {
              "get": {
                "operationId": "watchEvents",
                "summary": "Watch events",
                "responses": {
                  "200": { "description": "ok",
                    "content": { "text/event-stream": { "schema": { "type": "string" } } } }
                }
              }
            }
          }
        }
        """;

    private static final Map<String, String> BEARER = Map.of("bearerAuth", "guard0");

    private static final String HEADERS_COOKIES =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "Prefs", "version": "1.0.0" },
          "servers": [ { "url": "http://prefs.example:8080/v1" } ],
          "paths": {
            "/prefs": {
              "get": {
                "operationId": "read_prefs",
                "parameters": [
                  { "name": "X-Tenant", "in": "header", "required": true, "schema": { "type": "string" } },
                  { "name": "X-Trace", "in": "header", "schema": { "type": "string" } },
                  { "name": "sid", "in": "cookie", "required": true, "schema": { "type": "string" } },
                  { "name": "pref", "in": "cookie", "schema": { "type": "string" } }
                ],
                "responses": { "200": { "description": "ok" } }
              }
            }
          }
        }
        """;

    private static final String UNSUPPORTED =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "Unsupported", "version": "1.0.0" },
          "servers": [ { "url": "https://api.unsupported.example" } ],
          "paths": {
            "/forms": {
              "post": {
                "operationId": "submit_form",
                "requestBody": { "content": { "application/x-www-form-urlencoded": { "schema": { "type": "object" } } } },
                "responses": { "200": { "description": "ok" } }
              }
            },
            "/tags": {
              "get": {
                "operationId": "list_by_tags",
                "parameters": [
                  { "name": "tags", "in": "query", "schema": { "type": "array", "items": { "type": "string" } } }
                ],
                "responses": { "200": { "description": "ok" } }
              }
            },
            "/ping": {
              "get": {
                "operationId": "ping",
                "responses": { "200": { "description": "ok" } }
              }
            }
          }
        }
        """;

    private static final String SECURED =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "Secured", "version": "1.0.0" },
          "servers": [ { "url": "https://api.secured.example" } ],
          "components": { "securitySchemes": {
            "bearerAuth": { "type": "http", "scheme": "bearer", "bearerFormat": "jwt" },
            "apiKeyAuth": { "type": "apiKey", "in": "header", "name": "X-Api-Key" },
            "oauthAuth": { "type": "oauth2", "flows": {} }
          } },
          "paths": {
            "/guarded": { "get": { "operationId": "guarded",
              "security": [ { "bearerAuth": [ "read" ] } ],
              "responses": { "200": { "description": "ok" } } } },
            "/unmapped": { "get": { "operationId": "unmapped",
              "security": [ { "oauthAuth": [ "read" ] } ],
              "responses": { "200": { "description": "ok" } } } },
            "/mixed": { "get": { "operationId": "mixed",
              "security": [ { "bearerAuth": [ "read" ], "oauthAuth": [ "read" ] } ],
              "responses": { "200": { "description": "ok" } } } },
            "/both": { "get": { "operationId": "both",
              "security": [ { "bearerAuth": [ "read" ], "apiKeyAuth": [ "admin" ] } ],
              "responses": { "200": { "description": "ok" } } } },
            "/alternatives": { "get": { "operationId": "alternatives",
              "security": [ { "bearerAuth": [ "read" ] }, { "apiKeyAuth": [ "read" ] } ],
              "responses": { "200": { "description": "ok" } } } },
            "/public": { "get": { "operationId": "public_read",
              "security": [],
              "responses": { "200": { "description": "ok" } } } },
            "/open": { "get": { "operationId": "open_read",
              "responses": { "200": { "description": "ok" } } } }
          }
        }
        """;

    private static final String COLLISIONS =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "Collisions", "version": "1.0.0" },
          "servers": [ { "url": "https://api.collisions.example" } ],
          "paths": {
            "/widgets/a": { "get": { "operationId": "get_widget", "responses": { "200": { "description": "ok" } } } },
            "/widgets/b": { "get": { "operationId": "getWidget", "responses": { "200": { "description": "ok" } } } },
            "/widgets/c": { "get": { "operationId": "GetWidget", "responses": { "200": { "description": "ok" } } } },
            "/widgets/d": { "get": { "responses": { "200": { "description": "ok" } } } }
          }
        }
        """;

    private static final String OPEN_BODY =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "Things", "version": "1.0.0" },
          "servers": [ { "url": "https://api.things.example" } ],
          "paths": {
            "/things/{id}": {
              "put": {
                "operationId": "update_thing",
                "parameters": [ { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } } ],
                "requestBody": { "required": true, "content": { "application/json": { "schema": {
                  "type": "object", "properties": { "name": { "type": "string" } }, "additionalProperties": true } } } },
                "responses": { "200": { "description": "ok" } }
              }
            }
          }
        }
        """;

    private static final String EXTENSION =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "Extension", "version": "1.0.0" },
          "servers": [ { "url": "https://api.extension.example" } ],
          "paths": {
            "/orders/{orderId}": {
              "get": {
                "operationId": "read_order",
                "summary": "Read an order",
                "description": "Native description.",
                "tags": [ "native" ],
                "x-zilla-a2a": {
                  "name": "Order lookup",
                  "description": "Look up the status of an order",
                  "examples": [ "where is my order", "track order 7" ],
                  "tags": [ "orders", "tracking" ]
                },
                "parameters": [ { "name": "orderId", "in": "path", "required": true, "schema": { "type": "string" } } ],
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
          "info": { "title": "shop-overlay", "version": "1.0.0" },
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

    private static final String PETS =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "Petstore", "version": "1.0.0" },
          "servers": [ { "url": "https://api.petstore.example.com" } ],
          "paths": {
            "/pets": { "get": { "operationId": "list_pets", "summary": "List pets",
              "responses": { "200": { "description": "ok" } } } }
          }
        }
        """;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EngineContext context;

    @Mock
    private CatalogHandler catalog;

    private final A2aOpenapiCompositeGenerator generator = new A2aOpenapiCompositeGenerator();

    private final ToLongFunction<String> resolveId = name -> switch (name)
    {
    case "catalog0" -> 1L;
    case "guard1" -> 3L;
    case "embedding0" -> 4L;
    case "store0" -> 5L;
    default -> 2L;
    };

    private int nextSchemaId = 100;

    @Before
    public void initMocks()
    {
        lenient().when(context.supplyCatalog(eq(1L))).thenReturn(catalog);
        lenient().when(context.supplyBindingId(any(), any())).thenReturn(42L);
        lenient().when(context.supplyQName(eq(1L))).thenReturn("test:catalog0");
        lenient().when(context.supplyQName(eq(2L))).thenReturn("test0");
        lenient().when(context.supplyQName(eq(3L))).thenReturn("test1");
        lenient().when(context.supplyQName(eq(4L))).thenReturn("test:embedding0");
        lenient().when(context.supplyQName(eq(5L))).thenReturn("test:store0");
    }

    @Test
    public void shouldGenerateProxyCompositeWithSkillPerOperation()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, BEARER).build(),
            route("shop").exit("http0"));

        assertThat(composite.namespaces, hasSize(1));
        assertThat(composite.routes, hasSize(1));
        assertThat(composite.namespaces.get(0).name, equalTo("test:a2a-openapi0/a2a-http"));

        BindingConfig a2aHttp = a2aHttp(composite);
        assertThat(a2aHttp.type, equalTo("a2a-http"));
        assertThat(a2aHttp.kind, equalTo(PROXY));

        List<String> ids = skills(composite).stream().map(s -> s.id).toList();
        assertThat(ids, contains("watch_events", "search_items", "orders_read", "pulls_create"));
        assertThat(a2aHttp.routes, hasSize(4));
        assertThat(a2aHttp.routes.stream().map(r -> r.exit).distinct().toList(), contains("test:http0"));
        for (A2aHttpSkillConfig skill : skills(composite))
        {
            assertThat(skill.id, equalTo(conditionOf(route(composite, skill.id)).skill));
        }
    }

    @Test
    public void shouldDeriveSkillFromOperation()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, BEARER).build(),
            route("shop").exit("http0"));

        A2aHttpSkillConfig read = skill(composite, "orders_read");
        assertThat(read.name, equalTo("Read an order"));
        assertThat(read.description, equalTo("Returns the order with the given identifier."));
        assertThat(read.tags, contains("orders"));
        assertThat(read.examples, empty());
        assertThat(subject(read.input), equalTo("orders_read-input"));
        assertThat(subject(read.output), equalTo("orders_read-output"));

        A2aHttpSkillConfig search = skill(composite, "search_items");
        assertThat(search.name, equalTo("Search items"));
        assertThat(search.description, equalTo("Search items"));
        assertThat(search.tags, contains("items"));
    }

    @Test
    public void shouldReadSkillFromExtension()
    {
        A2aOpenapiCompositeConfig composite = generate(options("api", EXTENSION, null, null).build(),
            route("api").exit("http0"));

        A2aHttpSkillConfig skill = skill(composite, "read_order");
        assertThat(skill.name, equalTo("Order lookup"));
        assertThat(skill.description, equalTo("Look up the status of an order"));
        assertThat(skill.examples, contains("where is my order", "track order 7"));
        assertThat(skill.tags, contains("orders", "tracking"));
    }

    @Test
    public void shouldOverrideSkill()
    {
        A2aOpenapiOptionsConfig options = options("api", EXTENSION, null, null)
            .skill()
                .id("read_order")
                .name("Order status")
                .description("Reports where an order is")
                .examples(List.of("order status please"))
                .tags(List.of("status"))
                .input(json("custom-input"))
                .output(json("custom-output"))
                .build()
            .build();

        A2aOpenapiCompositeConfig composite = generate(options, route("api").exit("http0"));

        A2aHttpSkillConfig skill = skill(composite, "read_order");
        assertThat(skill.name, equalTo("Order status"));
        assertThat(skill.description, equalTo("Reports where an order is"));
        assertThat(skill.examples, contains("order status please"));
        assertThat(skill.tags, contains("status"));
        assertThat(subject(skill.input), equalTo("custom-input"));
        assertThat(subject(skill.output), equalTo("custom-output"));
        assertThat(catalogName(skill.input), equalTo("test:catalog0"));
    }

    @Test
    public void shouldDefaultSkillIdToSnakeCasedOperationId()
    {
        A2aOpenapiCompositeConfig composite = generate(options("api", COLLISIONS, null, null).build(),
            route("api").exit("http0"));

        assertThat(skills(composite).stream().map(s -> s.id).toList(),
            contains("get_widget", "get_widgets_b", "get_widgets_c", "get_widgets_d"));
    }

    @Test
    public void shouldDeduplicateSkillIdsAcrossSpecsWithNumericSuffix()
    {
        register("pets1", PETS);
        register("pets2", PETS);
        A2aOpenapiOptionsConfig options = options("pets0", PETS, null, null)
            .spec()
                .label("pets1")
                .catalog()
                    .name("catalog0")
                    .subject("pets1")
                    .version("latest")
                    .build()
                .build()
            .spec()
                .label("pets2")
                .catalog()
                    .name("catalog0")
                    .subject("pets2")
                    .version("latest")
                    .build()
                .build()
            .build();

        A2aOpenapiCompositeConfig composite = generate(options,
            route("pets0").exit("http0"), route("pets1").exit("http0"), route("pets2").exit("http0"));

        assertThat(skills(composite).stream().map(s -> s.id).toList(),
            contains("list_pets", "get_pets", "list_pets_2"));
    }

    @Test
    public void shouldNameSingleOperationRouteFromWhen()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, null).build(),
            route("shop").operation("searchItems").skill("find_items").exit("http0"));

        assertThat(skills(composite).stream().map(s -> s.id).toList(), contains("find_items"));
        assertThat(subject(skill(composite, "find_items").input), equalTo("find_items-input"));
        assertThat(conditionOf(a2aHttp(composite).routes.get(0)).skill, equalTo("find_items"));
    }

    @Test
    public void shouldExpandOperationsByTag()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, null).build(),
            route("shop").tag("orders").exit("http0"));

        assertThat(skills(composite).stream().map(s -> s.id).toList(), contains("orders_read"));
    }

    @Test
    public void shouldExpandOperationsByGlob()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, BEARER).build(),
            route("shop").operation("*/*").exit("http0"));

        assertThat(skills(composite).stream().map(s -> s.id).toList(), contains("orders_read", "pulls_create"));
    }

    @Test
    public void shouldClaimOperationForEarlierRouteBeforeLaterBulkRoute()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, BEARER).build(),
            route("shop").operation("searchItems").skill("find_items").exit("http0"),
            route("shop").exit("http1"));

        assertThat(skills(composite).stream().map(s -> s.id).toList(),
            contains("find_items", "watch_events", "orders_read", "pulls_create"));
        assertThat(route(composite, "find_items").exit, equalTo("test:http0"));
        assertThat(route(composite, "orders_read").exit, equalTo("test:http1"));
    }

    @Test
    public void shouldUseBindingExitWhenRouteDeclaresNone()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, null).build(), "http9",
            route("shop").tag("orders"),
            route("shop").tag("items").exit("http1"));

        assertThat(route(composite, "orders_read").exit, equalTo("test:http9"));
        assertThat(route(composite, "search_items").exit, equalTo("test:http1"));
    }

    @Test
    public void shouldMapPathQueryAndBody()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, BEARER).build(),
            route("shop").exit("http0"));

        A2aHttpWithConfig read = with(route(composite, "orders_read"));
        assertThat(read.headers.get(":method"), equalTo("GET"));
        assertThat(read.headers.get(":scheme"), equalTo("https"));
        assertThat(read.headers.get(":authority"), equalTo("api.shop.example:443"));
        assertThat(read.headers.get(":path"), equalTo("/orders/${args.orderId}"));
        assertThat(read.body, nullValue());

        A2aHttpWithConfig search = with(route(composite, "search_items"));
        assertThat(search.headers.get(":path"), equalTo("/items?q=${args.q}&${?args.limit=limit}"));

        A2aHttpWithConfig create = with(route(composite, "pulls_create"));
        assertThat(create.headers.get(":method"), equalTo("POST"));
        assertThat(create.headers.get(":path"), equalTo("/repos/${args.owner}/${args.repo}/pulls"));
        assertThat(subject(create.body), equalTo("pulls_create-body"));
    }

    @Test
    public void shouldMapRequiredHeadersAndCookiesOnly()
    {
        A2aOpenapiCompositeConfig composite = generate(options("prefs", HEADERS_COOKIES, null, null).build(),
            route("prefs").exit("http0"));

        A2aHttpWithConfig with = with(route(composite, "read_prefs"));
        assertThat(with.headers.get(":scheme"), equalTo("http"));
        assertThat(with.headers.get(":authority"), equalTo("prefs.example:8080"));
        assertThat(with.headers.get(":path"), equalTo("/v1/prefs"));
        assertThat(with.headers.get("x-tenant"), equalTo("${args.X-Tenant}"));
        assertThat(with.headers.get("cookie"), equalTo("sid=${args.sid}"));
        assertThat(with.headers.containsKey("x-trace"), equalTo(false));

        JsonObject input = schema(composite, "read_prefs-input");
        assertThat(input.getJsonObject("properties").keySet(), containsInAnyOrder("X-Tenant", "sid"));
        assertThat(input.getJsonArray("required").getValuesAs(JsonString.class).stream()
            .map(JsonString::getString).toList(), containsInAnyOrder("X-Tenant", "sid"));
    }

    @Test
    public void shouldRebindParametersViaParams()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, null).build(),
            route("shop").operation("orders/read").params(Map.of("orderId", "${args.id}")).exit("http0"));

        assertThat(with(route(composite, "orders_read")).headers.get(":path"), equalTo("/orders/${args.id}"));
    }

    @Test
    public void shouldOverrideServer()
    {
        A2aOpenapiCompositeConfig composite = generate(
            options("shop", SHOP, "https://api.internal.example:8443/shop", null).build(),
            route("shop").tag("orders").exit("http0"));

        A2aHttpWithConfig read = with(route(composite, "orders_read"));
        assertThat(read.headers.get(":scheme"), equalTo("https"));
        assertThat(read.headers.get(":authority"), equalTo("api.internal.example:8443"));
        assertThat(read.headers.get(":path"), equalTo("/shop/orders/${args.orderId}"));
    }

    @Test
    public void shouldDefaultServerPortFromScheme()
    {
        A2aOpenapiCompositeConfig composite = generate(
            options("shop", SHOP, "http://api.internal.example", null).build(),
            route("shop").tag("orders").exit("http0"));

        assertThat(with(route(composite, "orders_read")).headers.get(":authority"), equalTo("api.internal.example:80"));
    }

    @Test
    public void shouldComposeInputSchemaFromParametersAndBody()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, BEARER).build(),
            route("shop").exit("http0"));

        JsonObject input = schema(composite, "pulls_create-input");
        assertThat(input.getString("type"), equalTo("object"));
        assertThat(input.getJsonObject("properties").keySet(), containsInAnyOrder("owner", "repo", "title", "owner_body"));
        assertThat(input.getJsonArray("required").getValuesAs(JsonString.class).stream()
            .map(JsonString::getString).toList(), containsInAnyOrder("owner", "repo", "title"));

        JsonObject search = schema(composite, "search_items-input");
        assertThat(search.getJsonObject("properties").keySet(), containsInAnyOrder("q", "limit"));
        assertThat(search.getJsonObject("properties").getJsonObject("limit").getString("type"), equalTo("integer"));
    }

    @Test
    public void shouldDenyParametersShadowingBodyProperties()
    {
        A2aOpenapiCompositeConfig composite = generate(options("api", OPEN_BODY, null, null).build(),
            route("api").exit("http0"));

        JsonObject body = schema(composite, "update_thing-body");
        assertThat(body.getJsonObject("properties").getBoolean("id"), equalTo(false));
        assertThat(body.getJsonObject("properties").containsKey("name"), equalTo(true));

        JsonObject input = schema(composite, "update_thing-input");
        assertThat(input.get("additionalProperties"), notNullValue());
    }

    @Test
    public void shouldSelectFirstJsonSuccessResponseForOutput()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, BEARER).build(),
            route("shop").exit("http0"));

        JsonObject output = schema(composite, "pulls_create-output");
        assertThat(output.getString("type"), equalTo("object"));
        assertThat(output.getJsonObject("properties").containsKey("number"), equalTo(true));
    }

    @Test
    public void shouldNotGenerateOutputForEventStreamResponse()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, BEARER).build(),
            route("shop").exit("http0"));

        assertThat(skill(composite, "watch_events").output, nullValue());
        assertThat(schemaText(composite, "watch_events-output"), nullValue());
        assertThat(schemaText(composite, "watch_events-input"), notNullValue());
    }

    @Test
    public void shouldAdvertiseStreamingOnlyWhenEventStreamOperationExposed()
    {
        A2aOpenapiCompositeConfig streaming = generate(options("shop", SHOP, null, BEARER).build(),
            route("shop").exit("http0"));
        A2aOpenapiCompositeConfig unary = generate(options("shop", SHOP, null, BEARER).build(),
            route("shop").tag("orders").exit("http0"));

        assertThat(((A2aHttpOptionsConfig) a2aHttp(streaming).options).card.streaming, equalTo(true));
        assertThat(((A2aHttpOptionsConfig) a2aHttp(unary).options).card.streaming, equalTo(false));
    }

    @Test
    public void shouldGenerateCardFromSpecInfo()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, BEARER).build(),
            route("shop").exit("http0"));

        A2aHttpOptionsConfig options = (A2aHttpOptionsConfig) a2aHttp(composite).options;
        assertThat(options.card.name, equalTo("Shop"));
        assertThat(options.card.description, equalTo("Shop API"));
        assertThat(options.card.version, equalTo("2.1.0"));
    }

    @Test
    public void shouldOverrideAgent()
    {
        A2aOpenapiOptionsConfig options = options("shop", SHOP, null, BEARER)
            .agent()
                .name("Order Agent")
                .description("Handles orders")
                .version("3.0.0")
                .build()
            .build();

        A2aOpenapiCompositeConfig composite = generate(options, route("shop").exit("http0"));

        A2aHttpOptionsConfig a2aHttp = (A2aHttpOptionsConfig) a2aHttp(composite).options;
        assertThat(a2aHttp.card.name, equalTo("Order Agent"));
        assertThat(a2aHttp.card.description, equalTo("Handles orders"));
        assertThat(a2aHttp.card.version, equalTo("3.0.0"));
    }

    @Test
    public void shouldPassThroughEmbeddingStoreAndThreshold()
    {
        A2aOpenapiOptionsConfig options = options("shop", SHOP, null, BEARER)
            .threshold(0.9)
            .build();

        A2aOpenapiCompositeConfig composite = generate(options, route("shop").exit("http0"));

        A2aHttpOptionsConfig a2aHttp = (A2aHttpOptionsConfig) a2aHttp(composite).options;
        assertThat(a2aHttp.embedding, equalTo("test:embedding0"));
        assertThat(a2aHttp.store, equalTo("test:store0"));
        assertThat(a2aHttp.threshold, equalTo(0.9));
    }

    @Test
    public void shouldForwardAuthorization()
    {
        A2aOpenapiOptionsConfig options = options("shop", SHOP, null, BEARER)
            .authorization(new A2aOpenapiAuthorizationConfig("guard0", Map.of("authorization", "Bearer {credentials}")))
            .build();

        A2aOpenapiCompositeConfig composite = generate(options, route("shop").exit("http0"));

        A2aHttpOptionsConfig a2aHttp = (A2aHttpOptionsConfig) a2aHttp(composite).options;
        assertThat(a2aHttp.authorization.name, equalTo("test0"));
        assertThat(a2aHttp.authorization.headers, equalTo(Map.of("authorization", "Bearer {credentials}")));
    }

    @Test
    public void shouldApplyOverlayBeforeGeneratingSkills()
    {
        int overlayId = nextSchemaId++;
        lenient().when(catalog.resolve(eq("shop-overlay"), eq("latest"))).thenReturn(overlayId);
        lenient().when(catalog.resolve(eq(overlayId))).thenReturn(OVERLAY);

        A2aOpenapiOptionsConfig options = A2aOpenapiOptionsConfig.builder()
            .spec()
                .label("shop")
                .catalog()
                    .name("catalog0")
                    .subject("shop")
                    .version("latest")
                    .overlay()
                        .name("catalog0")
                        .subject("shop-overlay")
                        .version("latest")
                        .build()
                    .build()
                .build()
            .embedding("embedding0")
            .store("store0")
            .build();
        register("shop", SHOP);

        A2aOpenapiCompositeConfig composite = generate(options,
            route("shop").tag("orders").exit("http0"),
            route("shop").operation("added").exit("http0"));

        assertThat(skills(composite).stream().map(s -> s.id).toList(), contains("orders_read", "added"));
    }

    @Test
    public void shouldAggregateMultipleSpecs()
    {
        register("pets", PETS);
        A2aOpenapiOptionsConfig options = options("shop", SHOP, null, null)
            .spec()
                .label("pets")
                .server("https://pets.internal.example:9443")
                .catalog()
                    .name("catalog0")
                    .subject("pets")
                    .version("latest")
                    .build()
                .build()
            .build();

        A2aOpenapiCompositeConfig composite = generate(options,
            route("shop").tag("orders").exit("http0"), route("pets").exit("http1"));

        assertThat(skills(composite).stream().map(s -> s.id).toList(), contains("orders_read", "list_pets"));
        assertThat(with(route(composite, "list_pets")).headers.get(":authority"), equalTo("pets.internal.example:9443"));
        assertThat(with(route(composite, "orders_read")).headers.get(":authority"), equalTo("api.shop.example:443"));
        assertThat(((A2aHttpOptionsConfig) a2aHttp(composite).options).card.name, equalTo("Shop"));
    }

    @Test
    public void shouldGuardRouteFromSecurityRequirement()
    {
        A2aOpenapiCompositeConfig composite = generate(
            options("shop", SHOP, null, Map.of("bearerAuth", "guard0")).build(),
            route("shop").exit("http0"));

        List<GuardedConfig> guarded = route(composite, "pulls_create").guarded;
        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).name, equalTo("test0"));
        assertThat(guarded.get(0).roles, containsInAnyOrder("repo", "pr:write"));
        assertThat(route(composite, "orders_read").guarded, empty());
    }

    @Test
    public void shouldDenyOperationWhenSchemeHasNoGuard()
    {
        register("secured", SECURED);
        A2aOpenapiCompositeConfig composite = generate(
            options("secured", SECURED, null, Map.of("bearerAuth", "guard0")).build(),
            route("secured").exit("http0"));

        assertThat(skills(composite).stream().map(s -> s.id).toList(), contains("guarded", "open_read", "public_read"));
        assertThat(generator.deniedOperations(), hasItem(containsString("\"unmapped\"")));
        assertThat(generator.deniedOperations(), hasItem(containsString("oauthAuth")));
    }

    @Test
    public void shouldDenyOperationWhenSecurityMapAbsent()
    {
        A2aOpenapiCompositeConfig composite = generate(options("secured", SECURED, null, null).build(),
            route("secured").exit("http0"));

        assertThat(skills(composite).stream().map(s -> s.id).toList(), contains("open_read", "public_read"));
        assertThat(generator.deniedOperations(), hasSize(5));
    }

    @Test
    public void shouldDenyOperationWithPartiallyMappedRequirement()
    {
        A2aOpenapiCompositeConfig composite = generate(
            options("secured", SECURED, null, Map.of("bearerAuth", "guard0")).build(),
            route("secured").operation("mixed").exit("http0"));

        assertThat(skills(composite), empty());
        assertThat(generator.deniedOperations(), hasItem(containsString("\"mixed\"")));
    }

    @Test
    public void shouldDenyOperationRequiringMultipleGuards()
    {
        A2aOpenapiCompositeConfig composite = generate(
            options("secured", SECURED, null, Map.of("bearerAuth", "guard0", "apiKeyAuth", "guard1")).build(),
            route("secured").operation("both").exit("http0"));

        assertThat(skills(composite), empty());
        assertThat(generator.deniedOperations(), hasItem(containsString("multiple distinct guards")));
    }

    @Test
    public void shouldDenyOperationWithAlternativeSecurity()
    {
        A2aOpenapiCompositeConfig composite = generate(
            options("secured", SECURED, null, Map.of("bearerAuth", "guard0", "apiKeyAuth", "guard0")).build(),
            route("secured").operation("alternatives").exit("http0"));

        assertThat(skills(composite), empty());
        assertThat(generator.deniedOperations(), hasItem(containsString("alternative security requirements")));
    }

    @Test
    public void shouldUnionOperatorDeclaredRolesWithSpecDerivedRoles()
    {
        A2aOpenapiCompositeConfig composite = generate(
            options("shop", SHOP, null, Map.of("bearerAuth", "guard0")).build(),
            route("shop").operation("pulls/create").guarded("guard0", "admin").exit("http0"),
            route("shop").operation("orders/read").guarded("guard1", "read").exit("http0"));

        List<GuardedConfig> create = route(composite, "pulls_create").guarded;
        assertThat(create, hasSize(1));
        assertThat(create.get(0).roles, containsInAnyOrder("repo", "pr:write", "admin"));
        List<GuardedConfig> read = route(composite, "orders_read").guarded;
        assertThat(read, hasSize(1));
        assertThat(read.get(0).name, equalTo("test1"));
        assertThat(read.get(0).roles, contains("read"));
    }

    @Test
    public void shouldDenyUnsupportedRequestContentTypeAndParameterStyle()
    {
        A2aOpenapiCompositeConfig composite = generate(options("api", UNSUPPORTED, null, null).build(),
            route("api").exit("http0"));

        assertThat(skills(composite).stream().map(s -> s.id).toList(), contains("ping"));
        assertThat(generator.deniedOperations(), hasSize(2));
        assertThat(generator.deniedOperations(), hasItem(containsString("\"submit_form\"")));
        assertThat(generator.deniedOperations(), hasItem(containsString("unsupported request content type")));
        assertThat(generator.deniedOperations(), hasItem(containsString("\"list_by_tags\"")));
        assertThat(generator.deniedOperations(), hasItem(containsString("unsupported serialization of parameter \"tags\"")));
    }

    @Test
    public void shouldRejectUnknownSpecLabel()
    {
        A2aOpenapiOptionsConfig options = options("shop", SHOP, null, null).build();
        BindingConfig binding = binding(options, null, route("missing").exit("http0"));

        try
        {
            generator.generate(new A2aOpenapiBindingConfig(context, binding));
            throw new AssertionError("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException ex)
        {
            assertThat(ex.getMessage(), containsString("missing"));
        }
    }

    @Test
    public void shouldRejectSecurityMappingForUndeclaredScheme()
    {
        A2aOpenapiOptionsConfig options = options("shop", SHOP, null, Map.of("unknownAuth", "guard0")).build();
        BindingConfig binding = binding(options, null, route("shop").exit("http0"));

        try
        {
            generator.generate(new A2aOpenapiBindingConfig(context, binding));
            throw new AssertionError("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException ex)
        {
            assertThat(ex.getMessage(), containsString("unknownAuth"));
        }
    }

    @Test
    public void shouldGenerateConfigEquivalentToHandWrittenA2aHttp()
    {
        A2aOpenapiCompositeConfig composite = generate(options("shop", SHOP, null, null).build(),
            route("shop").tag("orders").exit("http0"));

        A2aHttpOptionsConfig expected = A2aHttpOptionsConfig.builder()
            .embedding("test:embedding0")
            .store("test:store0")
            .card()
                .name("Shop")
                .description("Shop API")
                .version("2.1.0")
                .streaming(false)
                .build()
            .skill()
                .id("orders_read")
                .name("Read an order")
                .description("Returns the order with the given identifier.")
                .tag("orders")
                .input(json("orders_read-input"))
                .output(json("orders_read-output"))
                .build()
            .build();

        A2aHttpOptionsConfig actual = (A2aHttpOptionsConfig) a2aHttp(composite).options;

        assertThat(describe(actual), equalTo(describe(expected)));

        RouteConfig route = a2aHttp(composite).routes.get(0);
        assertThat(route.exit, equalTo("test:http0"));
        assertThat(conditionOf(route).skill, equalTo("orders_read"));
        assertThat(with(route).headers, equalTo(Map.of(
            ":method", "GET",
            ":scheme", "https",
            ":authority", "api.shop.example:443",
            ":path", "/orders/${args.orderId}")));

        assertThat(schema(composite, "orders_read-input"), equalTo(parse(
            """
            {"type":"object","properties":{"orderId":{"type":"string"}},"required":["orderId"]}
            """)));
        assertThat(schema(composite, "orders_read-output"), equalTo(parse(
            """
            {"type":"object","properties":{"id":{"type":"string"},"status":{"type":"string"}}}
            """)));
    }

    private static String describe(
        A2aHttpOptionsConfig options)
    {
        final ModelConfigAdapter adapter = new ModelConfigAdapter();
        final StringBuilder text = new StringBuilder();
        text.append(options.embedding).append('|').append(options.store).append('|').append(options.threshold).append('|');
        text.append(options.card.name).append('|').append(options.card.description).append('|')
            .append(options.card.version).append('|').append(options.card.streaming).append('|')
            .append(options.card.defaultInputModes).append('|').append(options.card.defaultOutputModes).append('|');
        for (A2aHttpSkillConfig skill : options.skills)
        {
            text.append(skill.id).append('|').append(skill.name).append('|').append(skill.description).append('|')
                .append(skill.examples).append('|').append(skill.tags).append('|');
            adapter.adaptType(skill.input.model);
            text.append(adapter.adaptToJson(skill.input)).append('|');
            if (skill.output != null)
            {
                adapter.adaptType(skill.output.model);
                text.append(adapter.adaptToJson(skill.output));
            }
            text.append('|');
        }
        return text.toString();
    }

    private static ModelConfig json(
        String subject)
    {
        return JsonModelConfig.builder()
            .catalog()
                .name("catalog0")
                .schema()
                    .subject(subject)
                    .version("latest")
                    .build()
                .build()
            .build();
    }

    private A2aOpenapiOptionsConfigBuilder<A2aOpenapiOptionsConfig> options(
        String label,
        String document,
        String server,
        Map<String, String> security)
    {
        register(label, document);

        return A2aOpenapiOptionsConfig.builder()
            .spec()
                .label(label)
                .server(server)
                .catalog()
                    .name("catalog0")
                    .subject(label)
                    .version("latest")
                    .build()
                .security(security)
                .build()
            .embedding("embedding0")
            .store("store0");
    }

    private void register(
        String subject,
        String document)
    {
        final int schemaId = nextSchemaId++;
        lenient().when(catalog.resolve(eq(subject), eq("latest"))).thenReturn(schemaId);
        lenient().when(catalog.resolve(eq(schemaId))).thenReturn(document);
    }

    private A2aOpenapiCompositeConfig generate(
        A2aOpenapiOptionsConfig options,
        Rt... routes)
    {
        return generate(options, null, routes);
    }

    private A2aOpenapiCompositeConfig generate(
        A2aOpenapiOptionsConfig options,
        String exit,
        Rt... routes)
    {
        return generator.generate(new A2aOpenapiBindingConfig(context, binding(options, exit, routes)));
    }

    private BindingConfig binding(
        A2aOpenapiOptionsConfig options,
        String exit,
        Rt... routes)
    {
        final var builder = GenericBindingConfig.builder()
            .namespace("test")
            .name("a2a-openapi0")
            .type("a2a-openapi")
            .kind(PROXY)
            .options(options);

        for (Rt rt : routes)
        {
            final var route = builder.route();
            if (rt.skill != null)
            {
                route.when(A2aOpenapiConditionConfig.builder().skill(rt.skill).build());
            }
            route.with(A2aOpenapiWithConfig.builder()
                .spec(rt.spec)
                .operation(rt.operation)
                .tag(rt.tag)
                .params(rt.params)
                .build());
            if (rt.exit != null)
            {
                route.exit(rt.exit);
            }
            rt.guarded.forEach((name, roles) ->
            {
                final var guarded = route.guarded().name(name);
                roles.forEach(guarded::role);
                guarded.build();
            });
            route.build();
        }

        if (exit != null)
        {
            builder.exit(exit);
        }

        final BindingConfig binding = builder.build();
        binding.qname = "test:a2a-openapi0";
        binding.resolveId = resolveId;
        for (RouteConfig route : binding.routes)
        {
            if (route.exit != null)
            {
                route.qname = "test:%s".formatted(route.exit);
            }
            for (GuardedConfig guarded : route.guarded)
            {
                guarded.qname = "guard1".equals(guarded.name) ? "test1" : "test0";
            }
        }
        return binding;
    }

    private static Rt route(
        String spec)
    {
        return new Rt(spec);
    }

    private static BindingConfig a2aHttp(
        A2aOpenapiCompositeConfig composite)
    {
        final NamespaceConfig namespace = composite.namespaces.get(0);
        return namespace.bindings.stream()
            .filter(b -> "a2a-http0".equals(b.name))
            .findFirst()
            .orElseThrow();
    }

    private static List<A2aHttpSkillConfig> skills(
        A2aOpenapiCompositeConfig composite)
    {
        return ((A2aHttpOptionsConfig) a2aHttp(composite).options).skills;
    }

    private static A2aHttpSkillConfig skill(
        A2aOpenapiCompositeConfig composite,
        String id)
    {
        return skills(composite).stream()
            .filter(s -> id.equals(s.id))
            .findFirst()
            .orElse(null);
    }

    private static RouteConfig route(
        A2aOpenapiCompositeConfig composite,
        String skill)
    {
        return a2aHttp(composite).routes.stream()
            .filter(r -> skill.equals(conditionOf(r).skill))
            .findFirst()
            .orElseThrow();
    }

    private static A2aHttpConditionConfig conditionOf(
        RouteConfig route)
    {
        return (A2aHttpConditionConfig) route.when.get(0);
    }

    private static A2aHttpWithConfig with(
        RouteConfig route)
    {
        return (A2aHttpWithConfig) route.with;
    }

    private static String subject(
        ModelConfig model)
    {
        return model != null ? model.cataloged.get(0).schemas.get(0).subject : null;
    }

    private static String catalogName(
        ModelConfig model)
    {
        return model.cataloged.get(0).name;
    }

    private static JsonObject parse(
        String text)
    {
        return Json.createReader(new StringReader(text)).readObject();
    }

    private static String schemaText(
        A2aOpenapiCompositeConfig composite,
        String subject)
    {
        final List<String> schemas = new ArrayList<>();
        composite.namespaces.get(0).catalogs.stream()
            .map(c -> c.options)
            .filter(InlineOptionsConfig.class::isInstance)
            .map(InlineOptionsConfig.class::cast)
            .flatMap(o -> o.subjects.stream())
            .filter(s -> subject.equals(s.subject))
            .forEach(s -> schemas.add(s.schema));
        return schemas.isEmpty() ? null : schemas.get(0);
    }

    private static JsonObject schema(
        A2aOpenapiCompositeConfig composite,
        String subject)
    {
        final String text = schemaText(composite, subject);
        assertThat(text, notNullValue());
        return parse(text);
    }

    private static final class Rt
    {
        private final String spec;
        private String operation;
        private String tag;
        private String skill;
        private String exit;
        private Map<String, String> params;
        private final Map<String, List<String>> guarded = new LinkedHashMap<>();

        private Rt(
            String spec)
        {
            this.spec = spec;
        }

        private Rt operation(
            String operation)
        {
            this.operation = operation;
            return this;
        }

        private Rt tag(
            String tag)
        {
            this.tag = tag;
            return this;
        }

        private Rt skill(
            String skill)
        {
            this.skill = skill;
            return this;
        }

        private Rt exit(
            String exit)
        {
            this.exit = exit;
            return this;
        }

        private Rt params(
            Map<String, String> params)
        {
            this.params = params;
            return this;
        }

        private Rt guarded(
            String name,
            String... roles)
        {
            this.guarded.put(name, List.of(roles));
            return this;
        }
    }
}
