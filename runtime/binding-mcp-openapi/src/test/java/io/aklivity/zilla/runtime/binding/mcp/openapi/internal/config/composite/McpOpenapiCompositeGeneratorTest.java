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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.PROXY;
import static java.util.List.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.io.StringReader;
import java.util.List;

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
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenapiCompositeConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public class McpOpenapiCompositeGeneratorTest
{
    private static final String SPEC =
        "{" +
        "  \"openapi\": \"3.1.0\"," +
        "  \"info\": { \"title\": \"github\", \"version\": \"1.0.0\" }," +
        "  \"servers\": [ { \"url\": \"https://api.github.com\" } ]," +
        "  \"paths\": {" +
        "    \"/repos/{owner}/{repo}/pulls\": {" +
        "      \"post\": {" +
        "        \"operationId\": \"pulls/create\"," +
        "        \"parameters\": [" +
        "          { \"name\": \"owner\", \"in\": \"path\", \"required\": true, \"schema\": { \"type\": \"string\" } }," +
        "          { \"name\": \"repo\", \"in\": \"path\", \"required\": true, \"schema\": { \"type\": \"string\" } }" +
        "        ]," +
        "        \"requestBody\": {" +
        "          \"content\": { \"application/json\": { \"schema\": {" +
        "            \"type\": \"object\", \"required\": [\"title\"]," +
        "            \"properties\": { \"title\": { \"type\": \"string\" }, \"owner\": { \"type\": \"string\" } } } } }" +
        "        }," +
        "        \"responses\": { \"201\": { \"description\": \"created\"," +
        "          \"content\": { \"application/json\": { \"schema\": {" +
        "            \"type\": \"object\", \"properties\": { \"number\": { \"type\": \"integer\" } } } } } } }" +
        "      }" +
        "    }," +
        "    \"/repos/{owner}/{repo}\": {" +
        "      \"get\": {" +
        "        \"operationId\": \"repos/get\"," +
        "        \"parameters\": [" +
        "          { \"name\": \"owner\", \"in\": \"path\", \"required\": true, \"schema\": { \"type\": \"string\" } }," +
        "          { \"name\": \"repo\", \"in\": \"path\", \"required\": true, \"schema\": { \"type\": \"string\" } }" +
        "        ]," +
        "        \"responses\": { \"200\": { \"description\": \"ok\"," +
        "          \"content\": { \"application/json\": { \"schema\": { \"type\": \"object\" } } } } }" +
        "      }" +
        "    }" +
        "  }" +
        "}";

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
        lenient().when(catalog.resolve(eq("rest-api"), eq("latest"))).thenReturn(7);
        lenient().when(catalog.resolve(anyInt())).thenReturn(SPEC);
    }

    @Test
    public void shouldGenerateToolAndResource()
    {
        BindingConfig binding = bindingWithRoutes(
            route(0, "create_pr", null, "pulls/create"),
            route(1, null, "repo://{owner}/{repo}", "repos/get"));

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
        BindingConfig binding = bindingWithRoutes(route(0, "create_pr", null, "pulls/create"));

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

    private BindingConfig bindingWithRoutes(
        RouteConfig... routes)
    {
        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec(new McpOpenapiSpecificationConfig("openapi_github0",
                of("https://api.github.com"),
                of(new McpOpenapiCatalogConfig("catalog0", "rest-api", "latest"))))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp_openapi0")
            .type("mcp_openapi")
            .kind(PROXY)
            .options(options)
            .routes(of(routes))
            .build();
        binding.resolveId = name -> 1L;

        return binding;
    }

    private RouteConfig route(
        int order,
        String tool,
        String resource,
        String operationId)
    {
        return RouteConfig.builder()
            .order(order)
            .when(new McpOpenapiConditionConfig(tool, resource))
            .with(new McpOpenapiWithConfig("openapi_github0", operationId))
            .build();
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
