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

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufReporter;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufStream;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;

/**
 * The stateless {@link ProtobufStream} description: the pull cursor that pumps it plus an ordered list
 * of stages, assembled into a {@link ProtobufPipeline} by {@link #into(ProtobufSink)}.
 */
public final class ProtobufStreamImpl implements ProtobufStream
{
    private final ProtobufParser parser;
    private final List<ProtobufTransform> transforms;

    private ProtobufReporter reporter = ProtobufReporter.NONE;
    private boolean lenient;

    public ProtobufStreamImpl(
        ProtobufParser parser)
    {
        this.parser = parser;
        this.transforms = new ArrayList<>();
    }

    @Override
    public ProtobufStream transform(
        ProtobufTransform transform)
    {
        transforms.add(transform);
        return this;
    }

    @Override
    public ProtobufStream lenient(
        boolean lenient)
    {
        this.lenient = lenient;
        return this;
    }

    @Override
    public ProtobufStream reporting(
        ProtobufReporter reporter)
    {
        this.reporter = reporter != null ? reporter : ProtobufReporter.NONE;
        return this;
    }

    @Override
    public ProtobufPipeline into(
        ProtobufSink sink)
    {
        return new ProtobufPipelineImpl(parser, transforms, sink, reporter, null, lenient);
    }

    @Override
    public ProtobufPipeline into(
        ProtobufGenerator generator)
    {
        // the pipeline owns the generator and re-targets it at the caller's destination per transform call
        return new ProtobufPipelineImpl(parser, transforms, ProtobufSink.of(generator), reporter, generator, lenient);
    }

    @Override
    public ProtobufPipeline into(
        ProtobufGenerator generator,
        ProtobufSchema schema,
        String messageName)
    {
        // the pipeline owns the generator and re-targets it at the caller's destination per transform call
        return new ProtobufPipelineImpl(parser, transforms, ProtobufSink.of(generator, schema, messageName), reporter,
            generator, lenient);
    }
}
