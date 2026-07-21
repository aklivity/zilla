/*
 * Copyright 2021-2026 Aklivity Inc
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
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroTransform;
import io.aklivity.zilla.runtime.common.avro.AvroType;

public final class AvroSchemaImpl implements AvroSchema
{
    private final AvroNode root;

    public AvroSchemaImpl(
        String schema)
    {
        this.root = AvroSchemaCompiler.compile(schema);
    }

    @Override
    public AvroTransform validator()
    {
        return new Validator();
    }

    @Override
    public AvroType type()
    {
        return root;
    }

    /**
     * The streaming validator stage. Avro validation is intrinsic to parsing — the driver walks the
     * schema and aborts (REJECTED) on the first malformed byte before any event reaches this stage —
     * so the validator forwards the event stream unchanged, passing {@code control} through to its
     * downstream (non-mediating). Composed before a sink it validates-then-converts; composed before a
     * discarding sink it validates only.
     */
    private static final class Validator implements AvroTransform
    {
        @Override
        public Status transform(
            AvroController control,
            AvroSource source,
            AvroEvent event,
            AvroSink sink)
        {
            return sink.transform(control, source, event);
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }
}
