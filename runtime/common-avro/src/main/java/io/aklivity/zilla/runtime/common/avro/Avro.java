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

import java.util.Map;

import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.avro.internal.AvroGeneratorImpl;
import io.aklivity.zilla.runtime.common.avro.internal.AvroParserImpl;
import io.aklivity.zilla.runtime.common.avro.internal.AvroSchemaImpl;

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
    /**
     * Config key (an {@code Integer}) bounding the bytes the parser will buffer for a single in-flight
     * datum before rejecting it with {@link AvroValidationException} — a guard against a malformed or
     * hostile length prefix growing the work buffer without bound. Defaults to 16 MiB when absent.
     */
    public static final String WORK_MAX_BYTES = "io.aklivity.zilla.runtime.common.avro.work.max.bytes";

    private static final int WORK_MAX_BYTES_DEFAULT = 16 * 1024 * 1024;

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
     * Creates a schema-bound {@link AvroParser}; call {@link AvroParser#stream()} to begin a pipeline
     * description, append {@link AvroTransform} stages, and terminate with {@link AvroStream#into(AvroSink)}.
     */
    public static AvroParser parser(
        AvroSchema schema)
    {
        return new AvroParserImpl(schema, WORK_MAX_BYTES_DEFAULT);
    }

    /**
     * Creates a schema-bound {@link AvroParser} with parser config (e.g. {@link #WORK_MAX_BYTES});
     * keys absent from {@code config} take their defaults.
     */
    public static AvroParser parser(
        AvroSchema schema,
        Map<String, ?> config)
    {
        Object raw = config.get(WORK_MAX_BYTES);
        int maxWorkBytes = raw == null ? WORK_MAX_BYTES_DEFAULT : ((Number) raw).intValue();
        return new AvroParserImpl(schema, maxWorkBytes);
    }

    /**
     * Creates an {@link AvroGenerator} that writes Avro binary into {@code buffer} starting at
     * {@code offset}; pair it with {@link AvroSink#of(AvroGenerator)} to terminate a pipeline.
     */
    public static AvroGenerator generator(
        AvroSchema schema,
        MutableDirectBuffer buffer,
        int offset)
    {
        return new AvroGeneratorImpl(schema).wrap(buffer, offset, buffer.capacity());
    }
}
