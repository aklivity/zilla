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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.response;

import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.A2aTaskState;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public final class A2aEventDecoder
{
    public static final String CODE_INVALID_MESSAGE = "invalid_message";

    private static final String ERROR_TRUNCATED = "Truncated event";

    private enum State
    {
        IDLE,
        ASSEMBLING,
        STREAMING,
        TAIL,
        FAILED
    }

    private final A2aEventSink sink;
    private final int maximum;
    private final MutableDirectBufferEx eventBuffer;
    private final A2aEventHead head = new A2aEventHead();
    private final A2aEventScanner prescan;
    private final A2aEventScanner scanner;

    private State state = State.IDLE;
    private int eventLength;

    public A2aEventDecoder(
        A2aEventSink sink,
        int maximum)
    {
        this.sink = sink;
        this.maximum = maximum;
        this.eventBuffer = new UnsafeBufferEx(new byte[maximum]);
        this.prescan = new A2aEventScanner(head, maximum, false);
        this.scanner = new A2aEventScanner(new Forward(), maximum, true);
    }

    public void decode(
        int flags,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final boolean init = (flags & INIT) != 0;
        final boolean fin = (flags & FIN) != 0;

        if (state == State.IDLE && init)
        {
            state = State.ASSEMBLING;
            eventLength = 0;
        }
        else if (state == State.IDLE || init && state != State.FAILED)
        {
            fail(CODE_INVALID_MESSAGE, A2aEventScanner.ERROR_MALFORMED);
        }

        switch (state)
        {
        case ASSEMBLING:
            decodeAssembling(fin, buffer, offset, limit);
            break;
        case STREAMING:
        case TAIL:
            decodeStreaming(fin, buffer, offset, limit);
            break;
        default:
            break;
        }
    }

    public void finish()
    {
        if (state == State.ASSEMBLING || state == State.STREAMING || state == State.TAIL)
        {
            fail(CODE_INVALID_MESSAGE, ERROR_TRUNCATED);
        }
    }

    private void decodeAssembling(
        boolean fin,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final int appended = Math.min(maximum - eventLength, limit - offset);
        eventBuffer.putBytes(eventLength, buffer, offset, appended);
        eventLength += appended;

        if (appended < limit - offset)
        {
            startStreaming(fin, buffer, offset + appended, limit);
        }
        else if (fin)
        {
            decodeEvent();
        }
    }

    private void decodeEvent()
    {
        scanHead();

        if (state == State.ASSEMBLING)
        {
            if (!prescan.complete())
            {
                fail(CODE_INVALID_MESSAGE, ERROR_TRUNCATED);
            }
            else
            {
                if (head.append == null)
                {
                    head.append = Boolean.FALSE;
                }

                scanner.reset();
                scanner.scan(eventBuffer, 0, eventLength);

                if (state == State.TAIL)
                {
                    state = State.IDLE;
                }
            }
        }
    }

    private void startStreaming(
        boolean fin,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        scanHead();

        if (state == State.ASSEMBLING)
        {
            state = State.STREAMING;
            scanner.reset();
            scanner.scan(eventBuffer, 0, eventLength);
            decodeStreaming(fin, buffer, offset, limit);
        }
    }

    private void scanHead()
    {
        head.reset();
        prescan.reset();
        prescan.scan(eventBuffer, 0, eventLength);

        if (head.malformed != null)
        {
            fail(CODE_INVALID_MESSAGE, head.malformed);
        }
    }

    private void decodeStreaming(
        boolean fin,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (state == State.STREAMING || state == State.TAIL)
        {
            scanner.scan(buffer, offset, limit);
        }

        if (fin)
        {
            if (state == State.TAIL)
            {
                state = State.IDLE;
            }
            else if (state == State.STREAMING)
            {
                fail(CODE_INVALID_MESSAGE, ERROR_TRUNCATED);
            }
        }
    }

    private void fail(
        String code,
        String message)
    {
        state = State.FAILED;
        sink.failed(code, message);
    }

    private final class Forward implements A2aScanSink
    {
        @Override
        public void kind(
            A2aEventKind kind)
        {
            head.kind = kind;
            sink.eventStarted(kind, head.taskId, head.contextId, head.state, head.append);
        }

        @Override
        public void taskId(
            String taskId)
        {
            head.taskId = taskId;
        }

        @Override
        public void contextId(
            String contextId)
        {
            head.contextId = contextId;
        }

        @Override
        public void state(
            A2aTaskState state)
        {
            head.state = state;
        }

        @Override
        public void append(
            boolean append)
        {
            head.append = append;
        }

        @Override
        public void lastChunk(
            boolean lastChunk)
        {
            head.lastChunk = lastChunk;
        }

        @Override
        public void messageStarted(
            A2aMessageScope scope,
            String messageId)
        {
            sink.messageStarted(scope, messageId);
        }

        @Override
        public void artifactStarted(
            String artifactId)
        {
            sink.artifactStarted(artifactId);
        }

        @Override
        public void text(
            int flags,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            sink.text(flags, buffer, offset, limit);
        }

        @Override
        public void data(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            sink.data(buffer, offset, limit);
        }

        @Override
        public void file(
            String mediaType,
            String name,
            String url,
            String raw)
        {
            sink.file(mediaType, name, url, raw);
        }

        @Override
        public void artifactEnded()
        {
            sink.artifactEnded();
        }

        @Override
        public void messageEnded(
            A2aMessageScope scope)
        {
            sink.messageEnded(scope);
        }

        @Override
        public void ended()
        {
            state = State.TAIL;

            if (head.kind != null)
            {
                sink.eventEnded(head.lastChunk);
            }
        }

        @Override
        public void malformed(
            String message)
        {
            fail(CODE_INVALID_MESSAGE, message);
        }
    }
}
