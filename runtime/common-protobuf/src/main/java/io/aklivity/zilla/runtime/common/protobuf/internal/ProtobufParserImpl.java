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

import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufStream;

/**
 * The pipeline driver: a reusable, schema-bound (or schema-free, when {@code schema} is null) parser
 * whose {@link #stream()} begins a fresh {@link ProtobufStream} pipeline description.
 */
public final class ProtobufParserImpl implements ProtobufParser
{
    private final ProtobufSchema schema;
    private final String messageName;

    public ProtobufParserImpl(
        ProtobufSchema schema,
        String messageName)
    {
        this.schema = schema;
        this.messageName = messageName;
    }

    @Override
    public ProtobufStream stream()
    {
        return new ProtobufStreamImpl(schema, messageName);
    }
}
