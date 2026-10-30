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

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.A2aTaskState;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

final class A2aEventHead implements A2aScanSink
{
    A2aEventKind kind;
    String taskId;
    String contextId;
    A2aTaskState state;
    Boolean append;
    boolean lastChunk;
    String malformed;

    void reset()
    {
        kind = null;
        taskId = null;
        contextId = null;
        state = A2aTaskState.UNSPECIFIED;
        append = null;
        lastChunk = false;
        malformed = null;
    }

    @Override
    public void kind(
        A2aEventKind kind)
    {
        this.kind = kind;
    }

    @Override
    public void taskId(
        String taskId)
    {
        this.taskId = taskId;
    }

    @Override
    public void contextId(
        String contextId)
    {
        this.contextId = contextId;
    }

    @Override
    public void state(
        A2aTaskState state)
    {
        this.state = state;
    }

    @Override
    public void append(
        boolean append)
    {
        this.append = append;
    }

    @Override
    public void lastChunk(
        boolean lastChunk)
    {
        this.lastChunk = lastChunk;
    }

    @Override
    public void messageStarted(
        A2aMessageScope scope,
        String messageId)
    {
    }

    @Override
    public void artifactStarted(
        String artifactId)
    {
    }

    @Override
    public void text(
        int flags,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
    }

    @Override
    public void data(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
    }

    @Override
    public void file(
        String mediaType,
        String name,
        String url,
        String raw)
    {
    }

    @Override
    public void artifactEnded()
    {
    }

    @Override
    public void messageEnded(
        A2aMessageScope scope)
    {
    }

    @Override
    public void ended()
    {
    }

    @Override
    public void malformed(
        String message)
    {
        this.malformed = message;
    }
}
