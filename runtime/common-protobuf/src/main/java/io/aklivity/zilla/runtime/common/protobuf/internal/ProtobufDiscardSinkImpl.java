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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;

/**
 * A terminal sink that consumes and discards the event stream, reaching
 * {@link ProtobufPipeline.Status#COMPLETE} when the root message ends. The verdict of a pure
 * validation pipeline is the returned {@link ProtobufPipeline.Status}.
 */
public final class ProtobufDiscardSinkImpl implements ProtobufSink
{
    private int depth;

    @Override
    public ProtobufPipeline.Status feed(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event)
    {
        ProtobufPipeline.Status status = ProtobufPipeline.Status.RESUMABLE;
        if (event == ProtobufEvent.START_MESSAGE || event == ProtobufEvent.START_GROUP)
        {
            depth++;
        }
        else if (event == ProtobufEvent.END_MESSAGE || event == ProtobufEvent.END_GROUP)
        {
            depth--;
            if (depth == 0)
            {
                status = ProtobufPipeline.Status.COMPLETE;
            }
        }
        return status;
    }

    @Override
    public void reset()
    {
        depth = 0;
    }
}
