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
package io.aklivity.zilla.runtime.common.avro;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.avro.internal.AvroGeneratorImpl;
import io.aklivity.zilla.runtime.common.avro.internal.AvroParserImpl;
import io.aklivity.zilla.runtime.common.avro.internal.AvroSchemaImpl;
import io.aklivity.zilla.runtime.common.avro.internal.AvroStreamImpl;

/**
 * Entry point for {@code common-avro}'s streaming Avro parse and generate over Agrona buffers.
 * <p>
 * {@link #schema(String)} compiles an Avro schema document once (off the hot path) into an immutable
 * {@link AvroSchema} that callers cache per their own schema identifier. {@link #parser(AvroSchema)} and
 * {@link #generator(AvroSchema, MutableDirectBuffer, int)} then create resumable,
 * zero-per-message-allocation pipelines against it. It requires no Avro library on the classpath;
 * {@code common-avro} ships its own format-native codec.
 */
public final class Avro
{
    private Avro()
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

    /**
     * Creates a schema-bound {@link AvroParser}; pass it to {@link #stream(AvroParser)} to begin a
     * pipeline description, append {@link AvroTransform} stages, and terminate with
     * {@link AvroStream#into(AvroSink)}.
     */
    public static AvroParser parser(
        AvroSchema schema)
    {
        return new AvroParserImpl(schema);
    }

    /**
     * Begins a push pipeline pumped by {@code parser}: append stages with
     * {@link AvroStream#transform(AvroTransform)} and terminate with {@link AvroStream#into(AvroSink)}.
     */
    public static AvroStream stream(
        AvroParser parser)
    {
        return new AvroStreamImpl(parser);
    }

    /**
     * Creates an {@link AvroGenerator} that writes Avro binary into {@code buffer} starting at
     * {@code offset}; pair it with {@link AvroSink#of(AvroGenerator)} to terminate a pipeline.
     */
    public static AvroGenerator generator(
        AvroSchema schema,
        MutableDirectBufferEx buffer,
        int offset)
    {
        return new AvroGeneratorImpl(schema).wrap(buffer, offset, buffer.capacity());
    }
}
