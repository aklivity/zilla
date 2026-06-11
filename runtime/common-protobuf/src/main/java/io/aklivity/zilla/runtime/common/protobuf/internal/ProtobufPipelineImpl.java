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

import java.util.List;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;

/**
 * The runnable pipeline: a thin pump that re-targets the {@link ProtobufParserImpl} cursor at the frame
 * buffer and feeds each pulled {@link ProtobufEvent} through the stage chain to the terminal sink. The
 * parser is both the {@link ProtobufSource} read by stages and the {@link ProtobufController} they
 * steer, so it is passed as both ends of each {@link ProtobufSink#feed} call.
 */
public final class ProtobufPipelineImpl implements ProtobufPipeline
{
    private final ProtobufParserImpl parser;
    private final ProtobufSink head;

    public ProtobufPipelineImpl(
        ProtobufParserImpl parser,
        List<ProtobufTransform> transforms,
        ProtobufSink sink)
    {
        this.parser = parser;

        ProtobufSink chain = sink;
        for (int i = transforms.size() - 1; i >= 0; i--)
        {
            chain = new StageSink(transforms.get(i), chain);
        }
        this.head = chain;
    }

    @Override
    public void reset()
    {
        head.reset();
    }

    @Override
    public Status feed(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        Status status = Status.PENDING;
        try
        {
            parser.wrap(buffer, offset, length);
            while (status == Status.PENDING && parser.hasNext())
            {
                status = head.feed(parser, parser, parser.nextEvent());
            }
        }
        catch (ProtobufException ex)
        {
            status = Status.REJECTED;
        }
        return status;
    }

    private final class StageSink implements ProtobufSink
    {
        private final ProtobufTransform transform;
        private final ProtobufSink downstream;

        private StageSink(
            ProtobufTransform transform,
            ProtobufSink downstream)
        {
            this.transform = transform;
            this.downstream = downstream;
        }

        @Override
        public Status feed(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event)
        {
            return transform.feed(control, source, event, downstream);
        }

        @Override
        public void reset()
        {
            transform.reset();
            downstream.reset();
        }
    }
}
