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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

/**
 * The network side of an http client binding speaking HTTP/1.1 over a persistent connection: every request
 * received is answered with the same response, delivered fragment by fragment, unless it is withheld.
 */
final class Http1NetPeer extends NetPeer
{
    private static final int WRITER_CAPACITY = 16 * 1024;

    private final UnsafeBufferEx[] response;
    private final boolean respond;

    Http1NetPeer(
        int headerCount,
        int bodyLength,
        boolean streaming,
        boolean respond)
    {
        super(WRITER_CAPACITY);

        final int chunkLength = streaming ? BenchMessages.STREAMING_FRAGMENT_LENGTH : 0;
        final byte[][] fragments = Http1Wire.response(headerCount, BenchMessages.body(bodyLength), chunkLength);
        this.response = new UnsafeBufferEx[fragments.length];
        for (int index = 0; index < fragments.length; index++)
        {
            response[index] = new UnsafeBufferEx(fragments[index]);
        }
        this.respond = respond;
    }

    @Override
    Session newSession()
    {
        return new Http1Session();
    }

    private final class Http1Session implements Session
    {
        private final Http1Scanner scanner = new Http1Scanner();

        @Override
        public void onOpen(
            NetStream stream)
        {
        }

        @Override
        public void onBytes(
            NetStream stream,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int requests = scanner.scan(buffer, offset, limit);

            for (int request = 0; request < requests; request++)
            {
                countRequest();
            }

            for (int request = 0; respond && request < requests; request++)
            {
                for (int fragment = 0; fragment < response.length; fragment++)
                {
                    stream.write(response[fragment]);
                }
            }
        }
    }
}
