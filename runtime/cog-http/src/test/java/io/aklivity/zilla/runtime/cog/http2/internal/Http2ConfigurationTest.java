/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.http2.internal;

import static io.aklivity.zilla.runtime.cog.http2.internal.Http2Configuration.HTTP2_ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.aklivity.zilla.runtime.cog.http2.internal.Http2Configuration.HTTP2_MAX_CONCURRENT_STREAMS_CLEANUP;
import static io.aklivity.zilla.runtime.cog.http2.internal.Http2Configuration.HTTP2_SERVER_CONCURRENT_STREAMS;
import static io.aklivity.zilla.runtime.cog.http2.internal.Http2Configuration.HTTP2_SERVER_HEADER;
import static io.aklivity.zilla.runtime.cog.http2.internal.Http2Configuration.HTTP2_SERVER_MAX_HEADER_LIST_SIZE;
import static io.aklivity.zilla.runtime.cog.http2.internal.Http2Configuration.HTTP2_STREAMS_CLEANUP_DELAY;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Http2ConfigurationTest
{
    // needed by test annotations
    public static final String HTTP2_ACCESS_CONTROL_ALLOW_ORIGIN_NAME = "zilla.cog.http2.server.access.control.allow.origin";
    public static final String HTTP2_SERVER_HEADER_NAME = "zilla.cog.http2.server.header";
    public static final String HTTP2_SERVER_CONCURRENT_STREAMS_NAME = "zilla.cog.http2.server.concurrent.streams";
    public static final String HTTP2_SERVER_MAX_HEADER_LIST_SIZE_NAME = "zilla.cog.http2.server.max.header.list.size";
    public static final String HTTP2_MAX_CONCURRENT_STREAMS_CLEANUP_NAME = "zilla.cog.http2.max.concurrent.streams.cleanup";
    public static final String HTTP2_STREAMS_CLEANUP_DELAY_NAME = "zilla.cog.http2.streams.cleanup.delay";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(HTTP2_ACCESS_CONTROL_ALLOW_ORIGIN.name(), HTTP2_ACCESS_CONTROL_ALLOW_ORIGIN_NAME);
        assertEquals(HTTP2_SERVER_HEADER.name(), HTTP2_SERVER_HEADER_NAME);
        assertEquals(HTTP2_SERVER_CONCURRENT_STREAMS.name(), HTTP2_SERVER_CONCURRENT_STREAMS_NAME);
        assertEquals(HTTP2_SERVER_MAX_HEADER_LIST_SIZE.name(), HTTP2_SERVER_MAX_HEADER_LIST_SIZE_NAME);
        assertEquals(HTTP2_MAX_CONCURRENT_STREAMS_CLEANUP.name(), HTTP2_MAX_CONCURRENT_STREAMS_CLEANUP_NAME);
        assertEquals(HTTP2_STREAMS_CLEANUP_DELAY.name(), HTTP2_STREAMS_CLEANUP_DELAY_NAME);
    }
}
