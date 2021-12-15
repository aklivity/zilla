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

import java.util.function.Function;
import java.util.function.LongSupplier;

public class Http2Counters
{
    public final LongSupplier headersFramesRead;
    public final LongSupplier dataFramesRead;
    public final LongSupplier priorityFramesRead;
    public final LongSupplier resetStreamFramesRead;
    public final LongSupplier goawayFramesRead;
    public final LongSupplier windowUpdateFramesRead;
    public final LongSupplier settingsFramesRead;
    public final LongSupplier pingFramesRead;
    public final LongSupplier pushPromiseFramesRead;

    public final LongSupplier headersFramesWritten;
    public final LongSupplier continuationFramesWritten;
    public final LongSupplier continuationFramesRead;
    public final LongSupplier dataFramesWritten;
    public final LongSupplier priorityFramesWritten;
    public final LongSupplier resetStreamFramesWritten;
    public final LongSupplier goawayFramesWritten;
    public final LongSupplier windowUpdateFramesWritten;
    public final LongSupplier settingsFramesWritten;
    public final LongSupplier pingFramesWritten;
    public final LongSupplier pushPromiseFramesWritten;
    public final LongSupplier pushPromiseFramesSkipped;
    public final LongSupplier pushHeadersFramesWritten;

    public Http2Counters(
        Function<String, LongSupplier> supplyCounter)
    {
        this.headersFramesRead = supplyCounter.apply("http2.frames.read.headers");
        this.continuationFramesRead = supplyCounter.apply("http2.frames.read.continuation");
        this.dataFramesRead = supplyCounter.apply("http2.frames.read.data");
        this.priorityFramesRead = supplyCounter.apply("http2.frames.read.priority");
        this.resetStreamFramesRead = supplyCounter.apply("http2.frames.read.reset.stream");
        this.goawayFramesRead = supplyCounter.apply("http2.frames.read.goaway");
        this.windowUpdateFramesRead = supplyCounter.apply("http2.frames.read.window.update");
        this.settingsFramesRead = supplyCounter.apply("http2.frames.read.settings");
        this.pingFramesRead = supplyCounter.apply("http2.frames.read.ping");
        this.pushPromiseFramesRead = supplyCounter.apply("http2.frames.read.push.promise");

        this.headersFramesWritten = supplyCounter.apply("http2.frames.written.headers");
        this.continuationFramesWritten = supplyCounter.apply("http2.frames.written.continuation");
        this.dataFramesWritten = supplyCounter.apply("http2.frames.written.data");
        this.priorityFramesWritten = supplyCounter.apply("http2.frames.written.priority");
        this.resetStreamFramesWritten = supplyCounter.apply("http2.frames.written.reset.stream");
        this.goawayFramesWritten = supplyCounter.apply("http2.frames.written.goaway");
        this.windowUpdateFramesWritten = supplyCounter.apply("http2.frames.written.window.update");
        this.settingsFramesWritten = supplyCounter.apply("http2.frames.written.settings");
        this.pingFramesWritten = supplyCounter.apply("http2.frames.written.ping");
        this.pushPromiseFramesWritten = supplyCounter.apply("http2.frames.written.push.promise");
        this.pushPromiseFramesSkipped = supplyCounter.apply("http2.frames.skipped.push.promise");
        this.pushHeadersFramesWritten = supplyCounter.apply("http2.frames.written.push.headers");
    }
}
