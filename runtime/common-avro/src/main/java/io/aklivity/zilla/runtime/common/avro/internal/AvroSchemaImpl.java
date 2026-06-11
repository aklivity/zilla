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
package io.aklivity.zilla.runtime.common.avro.internal;

import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroParser;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroTransform;

public final class AvroSchemaImpl implements AvroSchema
{
    private final AvroNode root;

    public AvroSchemaImpl(
        String schema)
    {
        this.root = AvroSchemaCompiler.compile(schema);
    }

    @Override
    public AvroParser decoder()
    {
        return new AvroDecodeDriver(root);
    }

    @Override
    public AvroTransform validator()
    {
        return new AvroValidatorTransform();
    }

    @Override
    public AvroGenerator generator(
        MutableDirectBuffer buffer,
        int offset)
    {
        return new AvroGeneratorImpl(root, buffer, offset);
    }
}
