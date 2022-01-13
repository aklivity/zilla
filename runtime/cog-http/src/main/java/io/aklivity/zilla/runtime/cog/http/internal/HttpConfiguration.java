/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.cog.http.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class HttpConfiguration extends Configuration
{
    public static final boolean DEBUG_HTTP2_BUDGETS =
            EngineConfiguration.DEBUG_BUDGETS || Boolean.getBoolean("zilla.binding.http.debug.budgets");

    public static final IntPropertyDef HTTP_MAXIMUM_CONNECTIONS;
    public static final IntPropertyDef HTTP_MAXIMUM_QUEUED_REQUESTS;
    public static final IntPropertyDef HTTP_SERVER_CONCURRENT_STREAMS;
    public static final LongPropertyDef HTTP_SERVER_MAX_HEADER_LIST_SIZE;
    public static final IntPropertyDef HTTP_MAX_CONCURRENT_STREAMS_CLEANUP;
    public static final IntPropertyDef HTTP_STREAMS_CLEANUP_DELAY;
    public static final IntPropertyDef HTTP_MAX_CONCURRENT_APPLICATION_HEADERS;
    public static final BooleanPropertyDef HTTP_ACCESS_CONTROL_ALLOW_ORIGIN;
    public static final PropertyDef<String> HTTP_SERVER_HEADER;

    private static final ConfigurationDef HTTP_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.http");
        HTTP_MAXIMUM_CONNECTIONS = config.property("maximum.connections", 10);
        HTTP_MAXIMUM_QUEUED_REQUESTS = config.property("maximum.requests.queued", 10000);
        HTTP_SERVER_CONCURRENT_STREAMS = config.property("server.concurrent.streams", Integer.MAX_VALUE);
        HTTP_SERVER_MAX_HEADER_LIST_SIZE = config.property("server.max.header.list.size", 8_192L);
        HTTP_ACCESS_CONTROL_ALLOW_ORIGIN = config.property("server.access.control.allow.origin", false);
        HTTP_SERVER_HEADER = config.property("server.header");
        HTTP_MAX_CONCURRENT_STREAMS_CLEANUP = config.property("max.concurrent.streams.cleanup", 1000);
        HTTP_STREAMS_CLEANUP_DELAY = config.property("streams.cleanup.delay", 100);
        HTTP_MAX_CONCURRENT_APPLICATION_HEADERS = config.property("max.concurrent.application.headers", 10000);
        HTTP_CONFIG = config;
    }

    private final DirectBuffer serverHeader;

    public HttpConfiguration(
        Configuration config)
    {
        super(HTTP_CONFIG, config);
        String server = HTTP_SERVER_HEADER.get(this);
        serverHeader = server != null ? new UnsafeBuffer(server.getBytes(UTF_8)) : null;
    }

    public int maximumConnectionsPerRoute()
    {
        return HTTP_MAXIMUM_CONNECTIONS.getAsInt(this);
    }

    public int maximumRequestsQueuedPerRoute()
    {
        return HTTP_MAXIMUM_QUEUED_REQUESTS.getAsInt(this);
    }

    public int serverConcurrentStreams()
    {
        return HTTP_SERVER_CONCURRENT_STREAMS.getAsInt(this);
    }

    public long serverMaxHeaderListSize()
    {
        return HTTP_SERVER_MAX_HEADER_LIST_SIZE.getAsLong(this);
    }

    public int maxConcurrentStreamsCleanup()
    {
        return HTTP_MAX_CONCURRENT_STREAMS_CLEANUP.getAsInt(this);
    }

    public int streamsCleanupDelay()
    {
        return HTTP_STREAMS_CLEANUP_DELAY.getAsInt(this);
    }

    public int maxConcurrentApplicationHeaders()
    {
        return HTTP_MAX_CONCURRENT_APPLICATION_HEADERS.getAsInt(this);
    }

    public boolean accessControlAllowOrigin()
    {
        return HTTP_ACCESS_CONTROL_ALLOW_ORIGIN.get(this);
    }

    public DirectBuffer serverHeader()
    {
        return serverHeader;
    }
}
