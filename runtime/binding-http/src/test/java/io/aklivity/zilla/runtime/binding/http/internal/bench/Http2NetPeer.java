/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal.bench;

import static io.aklivity.zilla.runtime.binding.http.internal.bench.Http2Wire.DATA;
import static io.aklivity.zilla.runtime.binding.http.internal.bench.Http2Wire.FLAG_ACK;
import static io.aklivity.zilla.runtime.binding.http.internal.bench.Http2Wire.FLAG_END_STREAM;
import static io.aklivity.zilla.runtime.binding.http.internal.bench.Http2Wire.HEADERS;
import static io.aklivity.zilla.runtime.binding.http.internal.bench.Http2Wire.SETTINGS;
import static io.aklivity.zilla.runtime.binding.http.internal.bench.Http2Wire.WINDOW_UPDATE;

import org.agrona.collections.IntHashSet;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

/**
 * The network side of an http client binding speaking HTTP/2 over one connection: it answers every request
 * stream with the same response, sending the body only once the binding credits the stream, unless the
 * response is withheld.
 */
final class Http2NetPeer extends NetPeer
{
    private static final int CONNECTION_WINDOW_STEP = 64 * 1024;

    private final UnsafeBufferEx settings = new UnsafeBufferEx(
        Http2Wire.settings(Http2Wire.MAX_CONCURRENT_STREAMS, 65_535));
    private final UnsafeBufferEx settingsAck = new UnsafeBufferEx(Http2Wire.settingsAck());
    private final UnsafeBufferEx connectionWindow = new UnsafeBufferEx(
        Http2Wire.windowUpdate(0, Http2Wire.CONNECTION_WINDOW_INCREMENT));
    private final UnsafeBufferEx connectionWindowStep = new UnsafeBufferEx(
        Http2Wire.windowUpdate(0, CONNECTION_WINDOW_STEP));
    private final byte[] responseFirst;
    private final byte[] responseNext;
    private final byte[][] responseBody;
    private final UnsafeBufferEx responseFirstBuffer;
    private final UnsafeBufferEx responseNextBuffer;
    private final UnsafeBufferEx[] responseBodyBuffers;
    private final boolean respond;

    Http2NetPeer(
        int headerCount,
        int bodyLength,
        boolean streaming,
        boolean respond,
        int writerCapacity)
    {
        super(writerCapacity);

        final byte[] body = BenchMessages.body(bodyLength);
        final int fragmentLength = streaming ? BenchMessages.STREAMING_FRAGMENT_LENGTH : Math.max(bodyLength, 1);
        final int fragments = (bodyLength + fragmentLength - 1) / fragmentLength;

        this.responseFirst = Http2Wire.responseHeaders(true, headerCount, bodyLength, !streaming);
        this.responseNext = Http2Wire.responseHeaders(false, headerCount, bodyLength, !streaming);
        this.responseBody = new byte[fragments][];
        this.responseBodyBuffers = new UnsafeBufferEx[fragments];
        for (int fragment = 0; fragment < fragments; fragment++)
        {
            final int offset = fragment * fragmentLength;
            final int length = Math.min(fragmentLength, bodyLength - offset);
            responseBody[fragment] = Http2Wire.data(body, offset, length, fragment == fragments - 1);
            responseBodyBuffers[fragment] = new UnsafeBufferEx(responseBody[fragment]);
        }
        this.responseFirstBuffer = new UnsafeBufferEx(responseFirst);
        this.responseNextBuffer = new UnsafeBufferEx(responseNext);
        this.respond = respond;
    }

    @Override
    Session newSession()
    {
        return new Http2Session();
    }

    private final class Http2Session implements Session, Http2Scanner.FrameListener
    {
        private final Http2Scanner scanner = new Http2Scanner(this, true);
        private final IntHashSet awaitingCredit = new IntHashSet();

        private NetStream stream;
        private int responses;
        private int consumed;

        @Override
        public void onOpen(
            NetStream stream)
        {
            this.stream = stream;
            this.responses = 0;
            this.consumed = 0;
            this.awaitingCredit.clear();

            stream.write(settings);
            stream.write(connectionWindow);
        }

        @Override
        public void onBytes(
            NetStream stream,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            this.stream = stream;
            scanner.scan(buffer, offset, limit);
        }

        @Override
        public void onFrame(
            int type,
            int flags,
            int streamId,
            int length,
            int firstWord)
        {
            switch (type)
            {
            case SETTINGS:
                if ((flags & FLAG_ACK) == 0)
                {
                    stream.write(settingsAck);
                }
                break;
            case DATA:
                consumed += length;
                if (consumed >= CONNECTION_WINDOW_STEP)
                {
                    consumed -= CONNECTION_WINDOW_STEP;
                    stream.write(connectionWindowStep);
                }
                if ((flags & FLAG_END_STREAM) != 0)
                {
                    onRequest(streamId);
                }
                break;
            case HEADERS:
                if ((flags & FLAG_END_STREAM) != 0)
                {
                    onRequest(streamId);
                }
                break;
            case WINDOW_UPDATE:
                if (streamId != 0 && awaitingCredit.remove(streamId))
                {
                    onCredit(streamId);
                }
                break;
            default:
                break;
            }
        }

        private void onRequest(
            int streamId)
        {
            countRequest();

            if (respond)
            {
                final boolean first = responses++ == 0;
                Http2Wire.streamId(first ? responseFirst : responseNext, streamId);
                stream.write(first ? responseFirstBuffer : responseNextBuffer);

                if (responseBody.length != 0)
                {
                    awaitingCredit.add(streamId);
                }
            }
        }

        private void onCredit(
            int streamId)
        {
            for (int fragment = 0; fragment < responseBody.length; fragment++)
            {
                Http2Wire.streamId(responseBody[fragment], streamId);
                stream.write(responseBodyBuffers[fragment]);
            }
        }
    }
}
