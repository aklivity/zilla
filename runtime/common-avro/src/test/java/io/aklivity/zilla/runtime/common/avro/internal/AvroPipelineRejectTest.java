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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.avro.Avro;
import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroDiagnostic.Category;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroException;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroReporter;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroTransform;
import io.aklivity.zilla.runtime.common.avro.AvroValidationException;

class AvroPipelineRejectTest
{
    // captures the call-scoped diagnostic the pipeline pushes on a terminal REJECTED, copying it out
    private final String[] reason = new String[1];
    private final Category[] category = new Category[1];
    private final AvroReporter reporter = d ->
    {
        reason[0] = d.message();
        category[0] = d.category();
    };

    // An unterminated variable-length integer (continuation bit set, no terminating byte) is malformed
    // binary the parser cannot decode at all -- a parse failure, not a schema violation.
    @Test
    void shouldReportParsingFailure()
    {
        AvroSchema schema = Avro.schema("\"int\"");
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .reporting(reporter)
            .into(generatorFor(schema));
        pipeline.reset();

        byte[] in = { (byte) 0xFF };
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[128]);
        AvroPipeline.Status status = pipeline.transform(srcOf(in), 0, in.length, true, dst, 0, dst.capacity()).status();

        assertEquals(Status.REJECTED, status);
        assertNotNull(reason[0]);
        assertEquals(Category.PARSING, category[0]);
    }

    // A stage's own AvroException (not a parsing nor a validation exception) stands in for an extension's
    // internal failure during its own transform logic -- distinct from the value itself being invalid.
    @Test
    void shouldReportTransformFailure()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(failingWith(new AvroException("extension failure")))
            .reporting(reporter)
            .into(generatorFor(schema));
        pipeline.reset();

        // "foo": length 3 (zigzag varint 0x06) followed by the three bytes
        byte[] in = { 0x06, 0x66, 0x6f, 0x6f };
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[128]);
        AvroPipeline.Status status = pipeline.transform(srcOf(in), 0, in.length, true, dst, 0, dst.capacity()).status();

        assertEquals(Status.REJECTED, status);
        assertEquals("extension failure", reason[0]);
        assertEquals(Category.TRANSFORM, category[0]);
    }

    // AvroValidationException has no built-in stage that throws it yet (reserved for semantic validation
    // beyond structure), but the pipeline still categorizes it correctly wherever a stage does throw one.
    @Test
    void shouldReportValidationFailure()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(failingWith(new AvroValidationException("semantic violation")))
            .reporting(reporter)
            .into(generatorFor(schema));
        pipeline.reset();

        byte[] in = { 0x06, 0x66, 0x6f, 0x6f };
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[128]);
        AvroPipeline.Status status = pipeline.transform(srcOf(in), 0, in.length, true, dst, 0, dst.capacity()).status();

        assertEquals(Status.REJECTED, status);
        assertEquals("semantic violation", reason[0]);
        assertEquals(Category.VALIDATION, category[0]);
    }

    @Test
    void shouldNotReportOnValidAvro()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .reporting(reporter)
            .into(generatorFor(schema));
        pipeline.reset();

        byte[] in = { 0x06, 0x66, 0x6f, 0x6f };
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[128]);
        AvroPipeline.Status status = pipeline.transform(srcOf(in), 0, in.length, true, dst, 0, dst.capacity()).status();

        assertEquals(Status.COMPLETED, status);
        assertNull(reason[0]);
        assertNull(category[0]);
    }

    private static AvroGenerator generatorFor(
        AvroSchema schema)
    {
        return Avro.generator(schema, new UnsafeBufferEx(new byte[1]), 0);
    }

    private static MutableDirectBufferEx srcOf(
        byte[] bytes)
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[bytes.length]);
        buffer.putBytes(0, bytes);
        return buffer;
    }

    private static AvroTransform failingWith(
        RuntimeException failure)
    {
        return new AvroTransform()
        {
            @Override
            public AvroPipeline.Status transform(
                AvroController control,
                AvroSource source,
                AvroEvent event,
                AvroSink sink)
            {
                throw failure;
            }
        };
    }
}
