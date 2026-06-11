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

import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufStream;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;

/**
 * The stateless {@link ProtobufStream} description: a descriptor-bound driver plus an ordered list of
 * stages, assembled into a {@link ProtobufPipeline} by {@link #into(ProtobufSink)}.
 */
public final class ProtobufStreamImpl implements ProtobufStream
{
    private final ProtobufSchema schema;
    private final String messageName;
    private final List<ProtobufTransform> transforms;

    public ProtobufStreamImpl(
        ProtobufSchema schema,
        String messageName)
    {
        this.schema = schema;
        this.messageName = messageName;
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
    public ProtobufPipeline into(
        ProtobufSink sink)
    {
        return new ProtobufPipelineImpl(schema, messageName, transforms, sink);
    }
}
