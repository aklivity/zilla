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
package io.aklivity.zilla.runtime.common.openapi.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.common.openapi.model.Openapi;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiComponents;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiMediaType;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiOAuthFlow;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiOAuthFlows;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiOperation;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiPath;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiRequestBody;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiSchema;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiSecurityScheme;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiServer;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiServerVariable;

public class OpenapiViewTest
{
    @Test
    public void shouldAdaptSchemaExclusiveMinMax() throws Exception
    {

        OpenapiServer server = new OpenapiServer();
        server.url = "http://localhost/path";

        OpenapiSchema schema = new OpenapiSchema();
        schema.minimum = 0;
        schema.exclusiveMinimum = false;
        schema.maximum = 100;
        schema.exclusiveMaximum = true;

        OpenapiMediaType mediaType = new OpenapiMediaType();
        mediaType.schema = schema;

        OpenapiRequestBody requestBody = new OpenapiRequestBody();
        requestBody.content = Map.of("text/plain", mediaType);

        OpenapiOperation operation = new OpenapiOperation();
        operation.operationId = "ReadItems";
        operation.requestBody = requestBody;

        OpenapiPath path = new OpenapiPath();
        path.get = operation;

        Openapi model = new Openapi();
        model.servers = List.of(server);
        model.paths = Map.of("/", path);
        model.security = List.of(Map.of("OAuth2", List.of("read", "write")));

        OpenapiServerConfig config = OpenapiServerConfig.builder()
            .url("http://localhost/path")
            .build();

        OpenapiView view = OpenapiView.of(model, List.of(config));
        OpenapiServerView serverView = view.servers.get(0);
        List<OpenapiSecurityRequirementView> securityView = view.security.get(0);
        OpenapiPathView pathView = view.paths.get("/");
        OpenapiOperationView operationView = pathView.methods.get("GET");
        OpenapiRequestBodyView requestBodyView = operationView.requestBody;
        OpenapiMediaTypeView mediaTypeView = requestBodyView.content.get("text/plain");
        OpenapiSchemaView schemaView = mediaTypeView.schema;

        assertEquals(URI.create("http://localhost:80/path"), serverView.url);
        assertEquals(1, securityView.size());
        assertEquals("OAuth2", securityView.get(0).name);
        assertEquals(List.of("read", "write"), securityView.get(0).scopes);
        assertNull(schemaView.model.exclusiveMinimum);
        assertEquals(0, schemaView.model.minimum.intValue());
        assertEquals(100, schemaView.model.exclusiveMaximum.intValue());
        assertNull(schemaView.model.maximum);
    }

    @Test
    public void shouldDefaultHttpPort() throws Exception
    {
        Openapi model = new Openapi();
        OpenapiServer server = new OpenapiServer();
        server.url = "http://localhost/path";
        model.servers = List.of(server);

        OpenapiServerConfig config = OpenapiServerConfig.builder()
            .url("http://localhost/path")
            .build();
        OpenapiView view = OpenapiView.of(model, List.of(config));
        OpenapiServerView serverView = view.servers.get(0);

        assertEquals(URI.create("http://localhost:80/path"), serverView.url);
    }

    @Test
    public void shouldDefaultHttpsPort() throws Exception
    {
        Openapi model = new Openapi();
        OpenapiServer server = new OpenapiServer();
        server.url = "https://localhost/path";
        model.servers = List.of(server);

        OpenapiServerConfig config = OpenapiServerConfig.builder()
            .url("http://localhost/path")
            .build();
        OpenapiView view = OpenapiView.of(model, List.of(config));
        OpenapiServerView serverView = view.servers.get(0);

        assertEquals(URI.create("https://localhost:443/path"), serverView.url);
    }

    @Test
    public void shouldNotDefaultHttpPort() throws Exception
    {
        Openapi model = new Openapi();
        OpenapiServer server = new OpenapiServer();
        server.url = "http://localhost:8080/path";
        model.servers = List.of(server);

        OpenapiServerConfig config = OpenapiServerConfig.builder()
            .url("http://localhost/path")
            .build();
        OpenapiView view = OpenapiView.of(model, List.of(config));
        OpenapiServerView serverView = view.servers.get(0);

        assertEquals(URI.create("http://localhost:8080/path"), serverView.url);
    }

    @Test
    public void shouldNotDefaultHttpsPort() throws Exception
    {
        Openapi model = new Openapi();
        OpenapiServer server = new OpenapiServer();
        server.url = "https://localhost:9090/path";
        model.servers = List.of(server);

        OpenapiServerConfig config = OpenapiServerConfig.builder()
            .url("http://localhost/path")
            .build();
        OpenapiView view = OpenapiView.of(model, List.of(config));
        OpenapiServerView serverView = view.servers.get(0);

        assertEquals(URI.create("https://localhost:9090/path"), serverView.url);
    }

    @Test
    public void shouldDetectAndBindOperationExtension() throws Exception
    {
        SampleExtension modelExtension = new SampleExtension();
        modelExtension.key = "value";

        OpenapiOperation operation = new OpenapiOperation();
        operation.operationId = "ReadItems";
        operation.extensions = Map.of("x-zilla-sample", modelExtension);

        OpenapiPath path = new OpenapiPath();
        path.get = operation;

        Openapi model = new Openapi();
        model.paths = Map.of("/items", path);

        OpenapiView view = OpenapiView.of(model);
        OpenapiOperationView operationView = view.paths.get("/items").methods.get("GET");

        assertTrue(operationView.hasExtension("x-zilla-sample"));
        Optional<SampleExtension> extension = operationView.extension("x-zilla-sample", SampleExtension.class);
        assertTrue(extension.isPresent());
        assertEquals("value", extension.get().key);
    }

    @Test
    public void shouldNotDetectAbsentOperationExtension() throws Exception
    {
        OpenapiOperation operation = new OpenapiOperation();
        operation.operationId = "ReadItems";

        OpenapiPath path = new OpenapiPath();
        path.get = operation;

        Openapi model = new Openapi();
        model.paths = Map.of("/items", path);

        OpenapiView view = OpenapiView.of(model);
        OpenapiOperationView operationView = view.paths.get("/items").methods.get("GET");

        assertFalse(operationView.hasExtension("x-zilla-sample"));
        assertEquals(Optional.empty(), operationView.extension("x-zilla-sample", SampleExtension.class));
    }

    @Test
    public void shouldDetectAndBindSpecificationExtension() throws Exception
    {
        SampleExtension extension = new SampleExtension();
        extension.key = "root-value";

        Openapi model = new Openapi();
        model.extensions = Map.of("x-zilla-sample", extension);

        OpenapiView view = OpenapiView.of(model);

        assertTrue(view.hasExtension("x-zilla-sample"));
        assertEquals("root-value", view.extension("x-zilla-sample", SampleExtension.class).get().key);
    }

    @Test
    public void shouldDetectAndBindSecuritySchemeExtension() throws Exception
    {
        SampleExtension extension = new SampleExtension();
        extension.key = "scheme-value";

        OpenapiSecurityScheme scheme = new OpenapiSecurityScheme();
        scheme.type = "http";
        scheme.scheme = "bearer";
        scheme.bearerFormat = "jwt";
        scheme.extensions = Map.of("x-zilla-sample", extension);

        OpenapiComponents components = new OpenapiComponents();
        components.securitySchemes = Map.of("bearerAuth", scheme);

        Openapi model = new Openapi();
        model.components = components;

        OpenapiView view = OpenapiView.of(model);
        OpenapiSecuritySchemeView schemeView = view.components.securitySchemes.get("bearerAuth");

        assertTrue(schemeView.hasExtension("x-zilla-sample"));
        assertEquals("scheme-value", schemeView.extension("x-zilla-sample", SampleExtension.class).get().key);
    }

    @Test
    public void shouldResolveSecuritySchemeOAuthFlowsAndScopes() throws Exception
    {
        OpenapiOAuthFlow implicit = new OpenapiOAuthFlow();
        implicit.authorizationUrl = "https://example.com/authorize";
        implicit.refreshUrl = "https://example.com/refresh";
        implicit.scopes = Map.of("read", "Read access");

        OpenapiOAuthFlow clientCredentials = new OpenapiOAuthFlow();
        clientCredentials.tokenUrl = "https://example.com/token";
        clientCredentials.scopes = Map.of("read", "Read access", "write", "Write access");

        OpenapiOAuthFlows flows = new OpenapiOAuthFlows();
        flows.implicit = implicit;
        flows.clientCredentials = clientCredentials;

        OpenapiSecurityScheme scheme = new OpenapiSecurityScheme();
        scheme.type = "oauth2";
        scheme.flows = flows;

        OpenapiComponents components = new OpenapiComponents();
        components.securitySchemes = Map.of("oauth2Auth", scheme);

        Openapi model = new Openapi();
        model.components = components;

        OpenapiView view = OpenapiView.of(model);
        OpenapiSecuritySchemeView schemeView = view.components.securitySchemes.get("oauth2Auth");

        assertEquals("https://example.com/authorize", schemeView.flows.implicit.authorizationUrl);
        assertEquals("https://example.com/refresh", schemeView.flows.implicit.refreshUrl);
        assertEquals("https://example.com/token", schemeView.flows.clientCredentials.tokenUrl);
        assertNull(schemeView.flows.password);
        assertNull(schemeView.flows.authorizationCode);
        assertEquals(Set.of("read", "write"), Set.copyOf(schemeView.scopes));
    }

    @Test
    public void shouldNotResolveScopesWithoutFlows() throws Exception
    {
        OpenapiSecurityScheme scheme = new OpenapiSecurityScheme();
        scheme.type = "http";
        scheme.scheme = "bearer";

        OpenapiComponents components = new OpenapiComponents();
        components.securitySchemes = Map.of("bearerAuth", scheme);

        Openapi model = new Openapi();
        model.components = components;

        OpenapiView view = OpenapiView.of(model);
        OpenapiSecuritySchemeView schemeView = view.components.securitySchemes.get("bearerAuth");

        assertNull(schemeView.flows);
        assertEquals(List.of(), schemeView.scopes);
    }

    @Test
    public void shouldResolveOperationLevelServersOverPathAndRoot() throws Exception
    {
        OpenapiServer rootServer = new OpenapiServer();
        rootServer.url = "http://root.example.com";

        OpenapiServer pathServer = new OpenapiServer();
        pathServer.url = "http://path.example.com";

        OpenapiServer operationServer = new OpenapiServer();
        operationServer.url = "http://operation.example.com";

        OpenapiOperation operation = new OpenapiOperation();
        operation.operationId = "ReadItems";
        operation.servers = List.of(operationServer);

        OpenapiPath path = new OpenapiPath();
        path.get = operation;
        path.servers = List.of(pathServer);

        Openapi model = new Openapi();
        model.servers = List.of(rootServer);
        model.paths = Map.of("/items", path);

        OpenapiServerConfig config = OpenapiServerConfig.builder().build();
        OpenapiView view = OpenapiView.of(model, List.of(config));
        OpenapiOperationView operationView = view.paths.get("/items").methods.get("GET");

        assertEquals(1, operationView.servers.size());
        assertEquals(URI.create("http://operation.example.com:80"), operationView.servers.get(0).url);
    }

    @Test
    public void shouldResolvePathLevelServersOverRootWhenOperationHasNone() throws Exception
    {
        OpenapiServer rootServer = new OpenapiServer();
        rootServer.url = "http://root.example.com";

        OpenapiServer pathServer = new OpenapiServer();
        pathServer.url = "http://path.example.com";

        OpenapiOperation operation = new OpenapiOperation();
        operation.operationId = "ReadItems";

        OpenapiPath path = new OpenapiPath();
        path.get = operation;
        path.servers = List.of(pathServer);

        Openapi model = new Openapi();
        model.servers = List.of(rootServer);
        model.paths = Map.of("/items", path);

        OpenapiServerConfig config = OpenapiServerConfig.builder().build();
        OpenapiView view = OpenapiView.of(model, List.of(config));
        OpenapiOperationView operationView = view.paths.get("/items").methods.get("GET");

        assertEquals(1, operationView.servers.size());
        assertEquals(URI.create("http://path.example.com:80"), operationView.servers.get(0).url);
    }

    @Test
    public void shouldResolveRootLevelServersWhenNoOperationOrPathServers() throws Exception
    {
        OpenapiServer rootServer = new OpenapiServer();
        rootServer.url = "http://root.example.com";

        OpenapiOperation operation = new OpenapiOperation();
        operation.operationId = "ReadItems";

        OpenapiPath path = new OpenapiPath();
        path.get = operation;

        Openapi model = new Openapi();
        model.servers = List.of(rootServer);
        model.paths = Map.of("/items", path);

        OpenapiServerConfig config = OpenapiServerConfig.builder().build();
        OpenapiView view = OpenapiView.of(model, List.of(config));
        OpenapiOperationView operationView = view.paths.get("/items").methods.get("GET");

        assertEquals(1, operationView.servers.size());
        assertEquals(URI.create("http://root.example.com:80"), operationView.servers.get(0).url);
    }

    @Test
    public void shouldResolveServerUrlVariableDefaultWhenNoOverride() throws Exception
    {
        OpenapiServerVariable variable = new OpenapiServerVariable();
        variable.values = List.of("prod", "staging");
        variable.defaultValue = "prod";

        OpenapiServer server = new OpenapiServer();
        server.url = "http://{env}.example.com";
        server.variables = Map.of("env", variable);

        Openapi model = new Openapi();
        model.servers = List.of(server);

        OpenapiServerConfig config = OpenapiServerConfig.builder().build();
        OpenapiView view = OpenapiView.of(model, List.of(config));

        assertEquals(URI.create("http://prod.example.com:80"), view.servers.get(0).url);
    }

    @Test
    public void shouldResolveServerUrlVariableOverrideMatchingPattern() throws Exception
    {
        OpenapiServerVariable variable = new OpenapiServerVariable();
        variable.values = List.of("prod", "staging");
        variable.defaultValue = "prod";

        OpenapiServer server = new OpenapiServer();
        server.url = "http://{env}.example.com";
        server.variables = Map.of("env", variable);

        Openapi model = new Openapi();
        model.servers = List.of(server);

        OpenapiServerConfig config = OpenapiServerConfig.builder()
            .url("http://staging.example.com")
            .build();
        OpenapiView view = OpenapiView.of(model, List.of(config));

        assertEquals(URI.create("http://staging.example.com:80"), view.servers.get(0).url);
    }

    @Test
    public void shouldDefaultServerUrlVariableWhenOverrideDoesNotMatchPattern() throws Exception
    {
        OpenapiServerVariable variable = new OpenapiServerVariable();
        variable.values = List.of("prod", "staging");
        variable.defaultValue = "prod";

        OpenapiServer server = new OpenapiServer();
        server.url = "http://{env}.example.com";
        server.variables = Map.of("env", variable);

        Openapi model = new Openapi();
        model.servers = List.of(server);

        OpenapiServerConfig config = OpenapiServerConfig.builder()
            .url("http://dev.example.com")
            .build();
        OpenapiView view = OpenapiView.of(model, List.of(config));

        assertEquals(URI.create("http://prod.example.com:80"), view.servers.get(0).url);
    }

    @Test
    public void shouldMarkServerOverriddenOnlyWhenOverrideMatchesItsOwnUrl() throws Exception
    {
        OpenapiServer prod = new OpenapiServer();
        prod.url = "http://localhost:9090/prod";

        OpenapiServer qa = new OpenapiServer();
        qa.url = "http://localhost:8080/qa";

        Openapi model = new Openapi();
        model.servers = List.of(prod, qa);

        OpenapiServerConfig config = OpenapiServerConfig.builder()
            .url("http://localhost:9090/prod")
            .build();
        OpenapiView view = OpenapiView.of(model, List.of(config));

        assertTrue(view.servers.get(0).overridden);
        assertFalse(view.servers.get(1).overridden);
    }

    @Test
    public void shouldKeepLiteralUrlAtSpecDefaultRegardlessOfOverride() throws Exception
    {
        OpenapiServerVariable variable = new OpenapiServerVariable();
        variable.values = List.of("prod", "staging");
        variable.defaultValue = "prod";

        OpenapiServer server = new OpenapiServer();
        server.url = "http://{env}.example.com";
        server.variables = Map.of("env", variable);

        Openapi model = new Openapi();
        model.servers = List.of(server);

        OpenapiServerConfig config = OpenapiServerConfig.builder()
            .url("http://staging.example.com")
            .build();
        OpenapiView view = OpenapiView.of(model, List.of(config));

        assertEquals(URI.create("http://staging.example.com:80"), view.servers.get(0).url);
        assertEquals(URI.create("http://prod.example.com:80"), view.servers.get(0).literalUrl);
    }

    public static final class SampleExtension
    {
        public String key;
    }
}
