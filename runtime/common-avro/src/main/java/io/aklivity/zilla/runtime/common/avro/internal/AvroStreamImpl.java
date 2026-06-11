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

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroStream;
import io.aklivity.zilla.runtime.common.avro.AvroTransform;

final class AvroStreamImpl implements AvroStream
{
    private final AvroNode root;
    private final List<AvroTransform> transforms;

    AvroStreamImpl(
        AvroNode root)
    {
        this.root = root;
        this.transforms = new ArrayList<>();
    }

    @Override
    public AvroStream transform(
        AvroTransform transform)
    {
        transforms.add(transform);
        return this;
    }

    @Override
    public AvroPipeline into(
        AvroSink sink)
    {
        AvroSink head = sink;
        for (int i = transforms.size() - 1; i >= 0; i--)
        {
            head = new AvroSinkAdapter(transforms.get(i), head);
        }
        AvroDecodeDriver driver = new AvroDecodeDriver(root, head);
        return new AvroPipelineImpl(driver, head);
    }
}
