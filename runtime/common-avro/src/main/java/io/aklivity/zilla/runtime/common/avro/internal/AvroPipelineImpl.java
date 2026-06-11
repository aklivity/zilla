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

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroSink;

final class AvroPipelineImpl implements AvroPipeline
{
    private final AvroDecodeDriver driver;
    private final AvroSink root;

    AvroPipelineImpl(
        AvroDecodeDriver driver,
        AvroSink root)
    {
        this.driver = driver;
        this.root = root;
    }

    @Override
    public void reset()
    {
        driver.reset();
        root.reset();
    }

    @Override
    public Status feed(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        return driver.feed(buffer, offset, length);
    }
}
