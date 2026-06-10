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

import io.aklivity.zilla.runtime.common.avro.internal.AvroSchemaImpl;

/**
 * Entry point for {@code common-avro}'s streaming Avro decode and encode over Agrona buffers.
 * <p>
 * {@link #schema(String)} compiles an Avro schema document once (off the hot path) into an
 * immutable {@link AvroSchema} that callers cache per their own schema identifier. From a schema,
 * {@link AvroSchema#decoder(AvroSink)} and {@link AvroSchema#encoder(org.agrona.MutableDirectBuffer,
 * int)} create resumable, zero-per-message-allocation pipelines. It requires no Avro library on the
 * classpath; {@code common-avro} ships its own format-native codec.
 */
public final class StreamingAvro
{
    private StreamingAvro()
    {
    }

    /**
     * Compiles the given Avro schema document (JSON) into an immutable, cacheable model.
     *
     * @param schema the Avro schema document, as JSON text
     * @return the compiled schema
     */
    public static AvroSchema schema(
        String schema)
    {
        return new AvroSchemaImpl(schema);
    }
}
