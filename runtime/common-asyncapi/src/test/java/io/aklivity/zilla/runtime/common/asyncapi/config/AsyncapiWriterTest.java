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
package io.aklivity.zilla.runtime.common.asyncapi.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.asyncapi.model.Asyncapi;
import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiChannel;
import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiInfo;
import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiServer;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiView;

public class AsyncapiWriterTest
{
    public static final class SampleBinding
    {
        public String key;
    }

    public static final class SampleExtension
    {
        public String key;
    }

    @Test
    public void shouldRoundTripAsyncapiVersionAndInfo()
    {
        Asyncapi asyncapi = new Asyncapi();
        asyncapi.asyncapi = "3.0.0";
        asyncapi.info = new AsyncapiInfo();
        asyncapi.info.title = "Test API";
        asyncapi.info.version = "1.0.0";
        asyncapi.info.description = "A test API";

        String yaml = new AsyncapiWriter().write(asyncapi);

        Asyncapi reparsed = new AsyncapiParser().parse(yaml);

        assertEquals("3.0.0", reparsed.asyncapi);
        assertEquals("Test API", reparsed.info.title);
        assertEquals("1.0.0", reparsed.info.version);
        assertEquals("A test API", reparsed.info.description);
    }

    @Test
    public void shouldWriteGenericMapBindingWithoutBespokeClass()
    {
        Asyncapi asyncapi = new Asyncapi();
        asyncapi.asyncapi = "3.0.0";
        asyncapi.info = new AsyncapiInfo();
        asyncapi.info.title = "Test API";
        asyncapi.info.version = "1.0.0";

        Map<String, Object> serverBinding = new LinkedHashMap<>();
        serverBinding.put("key", "server-value");

        AsyncapiServer server = new AsyncapiServer();
        server.host = "localhost:9092";
        server.protocol = "kafka";
        server.bindings = new LinkedHashMap<>();
        server.bindings.put("x-zilla-sample", serverBinding);

        asyncapi.servers = new LinkedHashMap<>();
        asyncapi.servers.put("local", server);

        String yaml = new AsyncapiWriter().write(asyncapi);

        AsyncapiParser parser = new AsyncapiParserFactory()
            .withServerBinding("x-zilla-sample", SampleBinding.class)
            .createParser();
        Asyncapi reparsed = parser.parse(yaml);
        AsyncapiView view = AsyncapiView.of(reparsed);
        AsyncapiServerView reparsedServer = view.servers.get(0);

        assertTrue(reparsedServer.hasBinding("x-zilla-sample"));
        assertEquals("server-value", reparsedServer.binding("x-zilla-sample", SampleBinding.class).get().key);
    }

    @Test
    public void shouldRoundTripChannelsAndOperations()
    {
        Asyncapi asyncapi = new Asyncapi();
        asyncapi.asyncapi = "3.0.0";
        asyncapi.info = new AsyncapiInfo();
        asyncapi.info.title = "Test API";
        asyncapi.info.version = "1.0.0";

        AsyncapiChannel channel = new AsyncapiChannel();
        channel.address = "events";

        asyncapi.channels = new LinkedHashMap<>();
        asyncapi.channels.put("events", channel);

        String yaml = new AsyncapiWriter().write(asyncapi);

        Asyncapi reparsed = new AsyncapiParser().parse(yaml);

        assertEquals(1, reparsed.channels.size());
        assertEquals("events", reparsed.channels.get("events").address);
    }

    @Test
    public void shouldFlattenExtensionsToSiblingXKeysNotWrapperField()
    {
        Asyncapi asyncapi = new Asyncapi();
        asyncapi.asyncapi = "3.0.0";
        asyncapi.info = new AsyncapiInfo();
        asyncapi.info.title = "Test API";
        asyncapi.info.version = "1.0.0";

        Map<String, Object> extensionValue = new LinkedHashMap<>();
        extensionValue.put("key", "server-value");

        AsyncapiServer server = new AsyncapiServer();
        server.host = "localhost:9092";
        server.protocol = "kafka";
        server.extensions = new LinkedHashMap<>();
        server.extensions.put("x-zilla-sample", extensionValue);

        asyncapi.servers = new LinkedHashMap<>();
        asyncapi.servers.put("local", server);

        String yaml = new AsyncapiWriter().write(asyncapi);

        assertTrue(yaml.contains("x-zilla-sample"));
        assertTrue(!yaml.contains("extensions:"));

        AsyncapiParser parser = new AsyncapiParserFactory()
            .withExtension(AsyncapiExtension.of(AsyncapiExtension.Scope.SERVER, "x-zilla-sample", SampleExtension.class))
            .createParser();
        Asyncapi reparsed = parser.parse(yaml);
        AsyncapiView view = AsyncapiView.of(reparsed);
        AsyncapiServerView reparsedServer = view.servers.get(0);

        assertTrue(reparsedServer.hasExtension("x-zilla-sample"));
        assertEquals("server-value", reparsedServer.extension("x-zilla-sample", SampleExtension.class).get().key);
    }
}
