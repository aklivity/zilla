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

import java.net.URI;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.openapi.model.Openapi;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiServer;

public class OpenapiServerViewTest
{
    @Test
    public void shouldMergeOperationPathOntoOwnUrl() throws Exception
    {
        OpenapiServer model = new OpenapiServer();
        model.url = "http://localhost:8080/v1";

        Openapi spec = new Openapi();
        spec.servers = List.of(model);

        OpenapiServerView server = OpenapiView.of(spec).servers.get(0);

        assertEquals("/v1/pets", server.requestPath("/pets"));
    }

    @Test
    public void shouldMergeOperationPathOntoArbitraryBase() throws Exception
    {
        String path = OpenapiServerView.requestPath(URI.create("https://frontend.example.com/apis"), "/pets");

        assertEquals("/apis/pets", path);
    }

    @Test
    public void shouldDefaultPortsForKnownSchemes() throws Exception
    {
        assertEquals(URI.create("http://localhost:80/"), OpenapiServerView.resolvePorts(URI.create("http://localhost/")));
        assertEquals(URI.create("https://localhost:443/"), OpenapiServerView.resolvePorts(URI.create("https://localhost/")));
        assertEquals(URI.create("http://localhost:8080/"), OpenapiServerView.resolvePorts(URI.create("http://localhost:8080/")));
    }
}
