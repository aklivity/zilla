/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal;

import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class HttpConfiguration extends Configuration
{
    public static final boolean DEBUG_HTTP2_BUDGETS =
            EngineConfiguration.DEBUG_BUDGETS || Boolean.getBoolean("zilla.binding.http.debug.budgets");

    public static final IntPropertyDef HTTP_MAXIMUM_CONNECTIONS;
    public static final IntPropertyDef HTTP_CONCURRENT_STREAMS;
    public static final IntPropertyDef HTTP_STREAM_INITIAL_WINDOW;
    public static final LongPropertyDef HTTP_MAX_HEADER_LIST_SIZE;
    public static final IntPropertyDef HTTP_MAX_PUSH_PROMISE_LIST_SIZE;
    public static final IntPropertyDef HTTP_MAX_FRAME_SIZE;
    public static final IntPropertyDef HTTP_MAX_CONCURRENT_STREAMS_CLEANUP;
    public static final IntPropertyDef HTTP_STREAMS_CLEANUP_DELAY;
    public static final IntPropertyDef HTTP_MAX_CONCURRENT_APPLICATION_HEADERS;
    public static final PropertyDef<String> HTTP_SERVER_HEADER;
    public static final PropertyDef<String> HTTP_USER_AGENT_HEADER;

    private static final ConfigurationDef HTTP_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.http");
        HTTP_MAXIMUM_CONNECTIONS = config.property("maximum.connections", 10);
        HTTP_CONCURRENT_STREAMS = config.property("concurrent.streams", Integer.MAX_VALUE);
        HTTP_STREAM_INITIAL_WINDOW = config.property("stream.initial.window", 0);
        HTTP_MAX_HEADER_LIST_SIZE = config.property("max.header.list.size", 8_192L);
        HTTP_MAX_PUSH_PROMISE_LIST_SIZE = config.property("max.push.promise.list.size", 100);
        HTTP_MAX_FRAME_SIZE = config.property("max.frame.size", 16_384);
        HTTP_SERVER_HEADER = config.property("server.header");
        HTTP_USER_AGENT_HEADER = config.property("user.agent.header");
        HTTP_MAX_CONCURRENT_STREAMS_CLEANUP = config.property("max.concurrent.streams.cleanup", 1000);
        HTTP_STREAMS_CLEANUP_DELAY = config.property("streams.cleanup.delay", 100);
        HTTP_MAX_CONCURRENT_APPLICATION_HEADERS = config.property("max.concurrent.application.headers", 10000);
        HTTP_CONFIG = config;
    }

    private final String16FW serverHeader;
    private final String16FW userAgentHeader;

    public HttpConfiguration(
        Configuration config)
    {
        super(HTTP_CONFIG, config);
        String server = HTTP_SERVER_HEADER.get(this);
        String userAgent = HTTP_USER_AGENT_HEADER.get(this);
        serverHeader = server != null ? new String16FW(server) : null;
        userAgentHeader = userAgent != null ? new String16FW(userAgent) : null;
    }

    public int maximumConnectionsPerRoute()
    {
        return HTTP_MAXIMUM_CONNECTIONS.getAsInt(this);
    }

    public int concurrentStreams()
    {
        return HTTP_CONCURRENT_STREAMS.getAsInt(this);
    }

    public int streamInitialWindow()
    {
        return HTTP_STREAM_INITIAL_WINDOW.getAsInt(this);
    }

    public long maxHeaderListSize()
    {
        return HTTP_MAX_HEADER_LIST_SIZE.getAsLong(this);
    }

    public int maxFrameSize()
    {
        return HTTP_MAX_FRAME_SIZE.getAsInt(this);
    }

    public int maxPushPromiseListSize()
    {
        return HTTP_MAX_PUSH_PROMISE_LIST_SIZE.getAsInt(this);
    }

    public int concurrentStreamsCleanup()
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

    public String16FW serverHeader()
    {
        return serverHeader;
    }

    public String16FW userAgentHeader()
    {
        return userAgentHeader;
    }
}
