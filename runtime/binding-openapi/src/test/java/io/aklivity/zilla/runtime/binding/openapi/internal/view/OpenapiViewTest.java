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

import java.net.URI;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiServer;

public class OpenapiViewTest
{
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
