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
package io.aklivity.zilla.runtime.binding.http.internal.stream;

import io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2ErrorCode;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public class Http2Settings
{
    private static final int DEFAULT_HEADER_TABLE_SIZE = 4096;
    private static final int DEFAULT_ENABLE_PUSH = 1;
    private static final int DEFAULT_MAX_CONCURRENT_STREAMS = Integer.MAX_VALUE;
    private static final int DEFAULT_INITIAL_WINDOW_SIZE = 65_535;
    private static final int DEFAULT_MAX_FRAME_SIZE = 16_384;
    private static final long DEFAULT_MAX_HEADER_LIST_SIZE = Long.MAX_VALUE;

    public int headerTableSize = DEFAULT_HEADER_TABLE_SIZE;
    public int enablePush = DEFAULT_ENABLE_PUSH;
    public int maxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
    public int initialWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;
    public int maxFrameSize = DEFAULT_MAX_FRAME_SIZE;
    public long maxHeaderListSize = DEFAULT_MAX_HEADER_LIST_SIZE;

    public Http2Settings(
        HttpConfiguration config,
        BufferPool bufferPool)
    {
        this.maxConcurrentStreams = config.remoteConcurrentStreams();
        this.initialWindowSize = config.streamInitialWindow();
        this.maxFrameSize = Math.min(config.maxFrameSize(), bufferPool.slotCapacity() >> 1);
        this.maxHeaderListSize = Math.min(config.maxHeaderListSize(), bufferPool.slotCapacity() >> 1);
    }

    public Http2Settings()
    {
    }

    public void apply(
        Http2Settings settings)
    {
        this.maxConcurrentStreams = settings.maxConcurrentStreams;
        this.initialWindowSize = settings.initialWindowSize;
        this.maxHeaderListSize = settings.maxHeaderListSize;
        this.maxFrameSize = settings.maxFrameSize;
    }

    public Http2ErrorCode error()
    {
        Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

        if (!enablePushIsValid() ||
            !maxFrameSizeIsValid())
        {
            error = Http2ErrorCode.PROTOCOL_ERROR;
        }
        else if (!initialWindowSizeIsValid())
        {
            error = Http2ErrorCode.FLOW_CONTROL_ERROR;
        }

        return error;
    }

    private boolean enablePushIsValid()
    {
        return enablePush == 0 || enablePush == 1;
    }

    private boolean maxFrameSizeIsValid()
    {
        return 0x4000 <= maxFrameSize && maxFrameSize <= 0xffffff;
    }

    private boolean initialWindowSizeIsValid()
    {
        return initialWindowSize >= 0;
    }
}
