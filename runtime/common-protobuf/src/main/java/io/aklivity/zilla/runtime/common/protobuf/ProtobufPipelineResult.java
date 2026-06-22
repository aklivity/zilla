/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.protobuf;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;

/**
 * The result of a single {@link ProtobufPipeline#transform} call: the {@link Status} plus the source bytes
 * {@link #consumed()} from the input window and the output bytes {@link #produced()} into the destination
 * buffer. Reused across every {@code transform} call of a single {@link ProtobufPipeline}; read the fields
 * immediately and do not retain the instance beyond the next call.
 */
public final class ProtobufPipelineResult
{
    private Status status;
    private int consumed;
    private int produced;

    public Status status()
    {
        return status;
    }

    /**
     * Source bytes consumed from the input window this call. Zero on {@link Status#SUSPENDED} (output
     * back-pressure consumes no further input; re-present the same window), and the window length minus
     * {@link ProtobufPipeline#remaining()} on {@link Status#STARVED} or {@link Status#COMPLETED}.
     */
    public int consumed()
    {
        return consumed;
    }

    /**
     * Output bytes written into the destination buffer this call.
     */
    public int produced()
    {
        return produced;
    }

    public ProtobufPipelineResult set(
        Status status,
        int consumed,
        int produced)
    {
        this.status = status;
        this.consumed = consumed;
        this.produced = produced;
        return this;
    }
}
