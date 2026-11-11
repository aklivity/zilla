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
package io.aklivity.zilla.runtime.binding.tls.internal.bench;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
import static javax.net.ssl.SSLEngineResult.Status.BUFFER_UNDERFLOW;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

/**
 * A real TLS endpoint driven in memory: encrypted bytes go in through {@link #receive}, plaintext comes out as a
 * running count, and plaintext queued with {@link #send} comes out as encrypted bytes in {@link #output}.
 * <p>
 * The peer owns fixed buffers so that a long-lived or reused peer does not allocate outside the JDK's
 * {@link SSLEngine}. When {@code echo} is set, plaintext that is received is queued straight back for sending.
 */
final class TlsPeer
{
    static final class Scratch
    {
        private final ByteBuffer application;
        private final ByteBuffer outgoing;
        private final ByteBuffer packet;
        private final byte[] output;
        private final UnsafeBufferEx outputBuffer;
        private final byte[] transfer;

        Scratch(
            int capacity)
        {
            this.application = ByteBuffer.allocate(capacity);
            this.outgoing = ByteBuffer.allocate(capacity);
            this.packet = ByteBuffer.allocate(capacity);
            this.output = new byte[capacity];
            this.outputBuffer = new UnsafeBufferEx(output);
            this.transfer = new byte[capacity];
        }
    }

    private static final int INBOUND_CAPACITY = 80 * 1024;

    private final ByteBuffer application;
    private final ByteBuffer outgoing;
    private final ByteBuffer packet;
    private final byte[] output;
    private final UnsafeBufferEx outputBuffer;
    private final byte[] transfer;
    private final boolean echo;

    private ByteBuffer inbound;
    private SSLEngine engine;
    private int outputLength;
    private long receivedPlaintext;
    private boolean established;

    TlsPeer(
        Scratch scratch,
        boolean echo)
    {
        this.application = scratch.application;
        this.outgoing = scratch.outgoing;
        this.packet = scratch.packet;
        this.output = scratch.output;
        this.outputBuffer = scratch.outputBuffer;
        this.transfer = scratch.transfer;
        this.echo = echo;
    }

    TlsPeer engine(
        SSLEngine engine)
    {
        this.engine = engine;
        if (inbound == null)
        {
            inbound = ByteBuffer.allocate(INBOUND_CAPACITY);
        }
        this.inbound.clear();
        this.inbound.flip();
        this.application.clear();
        this.outgoing.clear();
        this.outgoing.flip();
        this.outputLength = 0;
        this.receivedPlaintext = 0L;
        this.established = false;
        return this;
    }

    void release()
    {
        this.engine = null;
        this.inbound = null;
    }

    boolean established()
    {
        return established;
    }

    long receivedPlaintext()
    {
        return receivedPlaintext;
    }

    DirectBufferEx output()
    {
        return outputBuffer;
    }

    int outputLength()
    {
        return outputLength;
    }

    void clearOutput()
    {
        outputLength = 0;
    }

    boolean inboundDone()
    {
        return engine.isInboundDone();
    }

    void receive(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        buffer.getBytes(offset, transfer, 0, length);

        inbound.compact();
        inbound.put(transfer, 0, length);
        inbound.flip();
    }

    void send(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        buffer.getBytes(offset, transfer, 0, length);
        queue(transfer, length);
    }

    void begin() throws SSLException
    {
        engine.beginHandshake();
    }

    void closeOutbound()
    {
        engine.closeOutbound();
    }

    void process() throws SSLException
    {
        boolean progress = true;

        while (progress)
        {
            progress = false;

            switch (engine.getHandshakeStatus())
            {
            case NEED_TASK:
                Runnable task;
                while ((task = engine.getDelegatedTask()) != null)
                {
                    task.run();
                }
                progress = true;
                break;
            case NEED_WRAP:
                progress = wrap();
                break;
            case NEED_UNWRAP:
            case NEED_UNWRAP_AGAIN:
                progress = unwrap();
                break;
            default:
                progress = unwrap();
                break;
            }
        }
    }

    private void queue(
        byte[] bytes,
        int length)
    {
        outgoing.compact();
        outgoing.put(bytes, 0, length);
        outgoing.flip();
    }

    private boolean wrap() throws SSLException
    {
        packet.clear();

        final SSLEngineResult result = engine.wrap(outgoing, packet);

        packet.flip();
        final int produced = packet.remaining();
        packet.get(output, outputLength, produced);
        outputLength += produced;

        established |= result.getHandshakeStatus() == FINISHED;

        return produced > 0 || result.bytesConsumed() > 0;
    }

    private boolean unwrap() throws SSLException
    {
        boolean progress = false;

        if (inbound.hasRemaining())
        {
            application.clear();

            final SSLEngineResult result = engine.unwrap(inbound, application);

            established |= result.getHandshakeStatus() == FINISHED;

            if (result.getStatus() != BUFFER_UNDERFLOW)
            {
                final int produced = result.bytesProduced();
                if (produced > 0)
                {
                    receivedPlaintext += produced;

                    if (echo)
                    {
                        application.flip();
                        application.get(transfer, 0, produced);
                        queue(transfer, produced);
                    }
                }

                progress = produced > 0 || result.bytesConsumed() > 0;
            }
        }

        if (outgoing.hasRemaining() && engine.getHandshakeStatus() != NEED_UNWRAP)
        {
            progress |= wrap();
        }

        return progress;
    }
}
