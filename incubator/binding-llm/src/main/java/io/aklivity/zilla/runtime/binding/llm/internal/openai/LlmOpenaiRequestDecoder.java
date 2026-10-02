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
package io.aklivity.zilla.runtime.binding.llm.internal.openai;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

public final class LlmOpenaiRequestDecoder
{
    public enum Status
    {
        PENDING,
        BLOCKED,
        COMPLETE,
        REJECTED
    }

    public interface Sink
    {
        int available();

        void model(
            String model);

        void block(
            String type,
            int message,
            String extension);

        void data(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last);
    }

    private final Sink sink;
    private final int hold;

    private Status status;

    public LlmOpenaiRequestDecoder(
        Sink sink,
        int hold)
    {
        this.sink = sink;
        this.hold = hold;
        this.status = Status.PENDING;
    }

    public int decode(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        return 0;
    }

    public Status status()
    {
        return status;
    }
}
