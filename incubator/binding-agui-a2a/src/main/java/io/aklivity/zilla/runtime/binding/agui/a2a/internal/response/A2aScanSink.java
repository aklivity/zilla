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

interface A2aScanSink
{
    void kind(
        A2aEventKind kind);

    void taskId(
        String taskId);

    void contextId(
        String contextId);

    void state(
        A2aTaskState state);

    void append(
        boolean append);

    void lastChunk(
        boolean lastChunk);

    void messageStarted(
        A2aMessageScope scope,
        String messageId);

    void artifactStarted(
        String artifactId);

    void text(
        int flags,
        DirectBufferEx buffer,
        int offset,
        int limit);

    void data(
        DirectBufferEx buffer,
        int offset,
        int limit);

    void file(
        String mediaType,
        String name,
        String url,
        String raw);

    void artifactEnded();

    void messageEnded(
        A2aMessageScope scope);

    void ended();

    void malformed(
        String message);
}
