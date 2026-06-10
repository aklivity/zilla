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
package io.aklivity.zilla.runtime.common.avro;

import org.agrona.MutableDirectBuffer;

/**
 * A compiled, immutable Avro schema model. Compiling parses the Avro schema document once (off the
 * hot path) into a structure that drives streaming decode and encode; callers cache one instance
 * per schema, keyed by their own schema identifier. A single instance may back many concurrent
 * pipelines on the same worker thread, but the pipelines it produces are not thread-safe.
 */
public interface AvroSchema
{
    /**
     * Creates a decode pipeline that reads Avro binary against this schema and emits the decoded
     * {@link AvroEvent} stream to {@code sink}.
     */
    AvroDecodePipeline decoder(
        AvroSink sink);

    /**
     * Creates an encode pipeline that consumes an {@link AvroEvent} stream and writes Avro binary
     * into {@code buffer} starting at {@code offset}.
     */
    AvroEncodePipeline encoder(
        MutableDirectBuffer buffer,
        int offset);
}
