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

import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroTransform;

/**
 * Adapts an {@link AvroTransform} plus its bound downstream {@link AvroSink} into a single
 * {@code AvroSink}, so the driver can feed a uniform sink chain folded right-to-left over the terminal
 * sink at assembly time.
 */
final class AvroSinkAdapter implements AvroSink
{
    private final AvroTransform transform;
    private final AvroSink downstream;

    AvroSinkAdapter(
        AvroTransform transform,
        AvroSink downstream)
    {
        this.transform = transform;
        this.downstream = downstream;
    }

    @Override
    public Status feed(
        AvroController control,
        AvroSource source,
        AvroEvent event)
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
