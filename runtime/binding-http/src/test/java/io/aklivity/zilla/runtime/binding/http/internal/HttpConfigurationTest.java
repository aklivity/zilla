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
package io.aklivity.zilla.runtime.binding.http.internal;

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_CLIENT_MAX_FRAME_SIZE;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_MAX_CONCURRENT_STREAMS_CLEANUP;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_SERVER_CONCURRENT_STREAMS;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_SERVER_HEADER;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_SERVER_MAX_HEADER_LIST_SIZE;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_STREAMS_CLEANUP_DELAY;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_STREAM_INITIAL_WINDOW;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HttpConfigurationTest
{
    // needed by test annotations
    public static final String HTTP_SERVER_HEADER_NAME = "zilla.binding.http.server.header";
    public static final String HTTP_SERVER_CONCURRENT_STREAMS_NAME = "zilla.binding.http.server.concurrent.streams";
    public static final String HTTP_STREAM_INITIAL_WINDOW_NAME = "zilla.binding.http.stream.initial.window";
    public static final String HTTP_SERVER_MAX_HEADER_LIST_SIZE_NAME = "zilla.binding.http.server.max.header.list.size";
    public static final String HTTP_CLIENT_MAX_FRAME_SIZE_NAME = "zilla.binding.http.client.max.frame.size";
    public static final String HTTP_MAX_CONCURRENT_STREAMS_CLEANUP_NAME = "zilla.binding.http.max.concurrent.streams.cleanup";
    public static final String HTTP_STREAMS_CLEANUP_DELAY_NAME = "zilla.binding.http.streams.cleanup.delay";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(HTTP_SERVER_HEADER.name(), HTTP_SERVER_HEADER_NAME);
        assertEquals(HTTP_SERVER_CONCURRENT_STREAMS.name(), HTTP_SERVER_CONCURRENT_STREAMS_NAME);
        assertEquals(HTTP_STREAM_INITIAL_WINDOW.name(), HTTP_STREAM_INITIAL_WINDOW_NAME);
        assertEquals(HTTP_SERVER_MAX_HEADER_LIST_SIZE.name(), HTTP_SERVER_MAX_HEADER_LIST_SIZE_NAME);
        assertEquals(HTTP_CLIENT_MAX_FRAME_SIZE.name(), HTTP_CLIENT_MAX_FRAME_SIZE_NAME);
        assertEquals(HTTP_MAX_CONCURRENT_STREAMS_CLEANUP.name(), HTTP_MAX_CONCURRENT_STREAMS_CLEANUP_NAME);
        assertEquals(HTTP_STREAMS_CLEANUP_DELAY.name(), HTTP_STREAMS_CLEANUP_DELAY_NAME);
    }
}
