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
package io.aklivity.zilla.runtime.binding.openapi.internal.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiMediaType;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiOperation;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiPath;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiRequestBody;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiSchema;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiServer;

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
}
