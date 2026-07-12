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

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.common.openapi.model.Openapi;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiServer;

public class OpenapiServerViewTest
{
    @Test
    public void shouldComputeZeroPrefixLengthWhenServerPathIsRoot() throws Exception
    {
        OpenapiServer server = new OpenapiServer();
        server.url = "http://localhost:8080/";

        Openapi model = new Openapi();
        model.servers = List.of(server);

        OpenapiView view = OpenapiView.of(model, List.of(OpenapiServerConfig.builder().build()));

        assertEquals(0, view.servers.get(0).effectivePrefixLength());
        assertEquals(0, view.servers.get(0).canonicalPrefixLength());
    }

    @Test
    public void shouldComputePrefixLengthWithoutTrailingSlash() throws Exception
    {
        OpenapiServer server = new OpenapiServer();
        server.url = "http://localhost:8080/v1";

        Openapi model = new Openapi();
        model.servers = List.of(server);

        OpenapiView view = OpenapiView.of(model, List.of(OpenapiServerConfig.builder().build()));

        assertEquals("/v1".length(), view.servers.get(0).effectivePrefixLength());
        assertEquals("/v1".length(), view.servers.get(0).canonicalPrefixLength());
    }

    @Test
    public void shouldComputePrefixLengthWithTrailingSlash() throws Exception
    {
        OpenapiServer server = new OpenapiServer();
        server.url = "http://localhost:8080/v1/";

        Openapi model = new Openapi();
        model.servers = List.of(server);

        OpenapiView view = OpenapiView.of(model, List.of(OpenapiServerConfig.builder().build()));

        assertEquals("/v1".length(), view.servers.get(0).effectivePrefixLength());
        assertEquals("/v1".length(), view.servers.get(0).canonicalPrefixLength());
    }

    @Test
    public void shouldComputeDistinctEffectiveAndCanonicalPrefixLengthsWhenOverridden() throws Exception
    {
        OpenapiServer server = new OpenapiServer();
        server.url = "http://localhost:8080/v1";

        Openapi model = new Openapi();
        model.servers = List.of(server);

        OpenapiServerConfig config = OpenapiServerConfig.builder()
            .url("https://frontend.example.com/apis")
            .build();
        OpenapiView view = OpenapiView.of(model, List.of(config));

        assertEquals("/apis".length(), view.servers.get(0).effectivePrefixLength());
        assertEquals("/v1".length(), view.servers.get(0).canonicalPrefixLength());
    }

    @Test
    public void shouldStripEffectivePrefixToRecoverCanonicalPath() throws Exception
    {
        OpenapiServer server = new OpenapiServer();
        server.url = "http://localhost:8080/v1";

        Openapi model = new Openapi();
        model.servers = List.of(server);

        OpenapiServerConfig config = OpenapiServerConfig.builder()
            .url("https://frontend.example.com/apis")
            .build();
        OpenapiView view = OpenapiView.of(model, List.of(config));
        OpenapiServerView serverView = view.servers.get(0);

        String effectivePath = serverView.requestPath("/pets");
        String recoveredCanonicalPath = effectivePath.substring(serverView.effectivePrefixLength());

        assertEquals("/apis/pets", effectivePath);
        assertEquals("/pets", recoveredCanonicalPath);
    }
}
