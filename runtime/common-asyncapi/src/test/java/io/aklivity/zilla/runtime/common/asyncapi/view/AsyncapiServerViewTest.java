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
package io.aklivity.zilla.runtime.common.asyncapi.view;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.net.URI;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiParser;
import io.aklivity.zilla.runtime.common.asyncapi.model.Asyncapi;
import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiServer;

public class AsyncapiServerViewTest
{
    @Test
    public void shouldComposeUrlFromLegacyUrlStyleServer() throws Exception
    {
        Asyncapi model = new AsyncapiParser().parse("""
            asyncapi: 2.6.0
            info:
              title: Test API
              version: 0.1.0
            servers:
              production:
                url: https://api.example.com/v1
                protocol: https
            channels: {}
            """);

        AsyncapiServerView server = AsyncapiView.of(model).servers.get(0);

        assertThat(server.url, is(URI.create("https://api.example.com/v1")));
    }

    @Test
    public void shouldComposeUrlFromHostAndProtocolWithNoPathname() throws Exception
    {
        Asyncapi model = new AsyncapiParser().parse("""
            asyncapi: 3.0.0
            info:
              title: Test API
              version: 0.1.0
            servers:
              production:
                host: broker.example.com:9092
                protocol: kafka
            channels: {}
            """);

        AsyncapiServerView server = AsyncapiView.of(model).servers.get(0);

        assertThat(server.url, is(URI.create("kafka://broker.example.com:9092")));
    }

    @Test
    public void shouldComposeUrlFromHostProtocolAndPathname() throws Exception
    {
        Asyncapi model = new AsyncapiParser().parse("""
            asyncapi: 3.0.0
            info:
              title: Test API
              version: 0.1.0
            servers:
              production:
                host: api.example.com
                protocol: https
                pathname: /v1
            channels: {}
            """);

        AsyncapiServerView server = AsyncapiView.of(model).servers.get(0);

        assertThat(server.url, is(URI.create("https://api.example.com/v1")));
    }

    @Test
    public void shouldLeaveUrlNullWhenNeitherUrlNorHostDeclared() throws Exception
    {
        AsyncapiServer model = new AsyncapiServer();
        model.protocol = "kafka";

        Asyncapi spec = new Asyncapi();
        spec.servers = Map.of("broken", model);

        AsyncapiServerView server = AsyncapiView.of(spec).servers.get(0);

        assertThat(server.url, nullValue());
    }
}
