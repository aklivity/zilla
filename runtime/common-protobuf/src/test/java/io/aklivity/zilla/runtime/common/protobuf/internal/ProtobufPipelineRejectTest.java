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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufDiagnostic.Category;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufReporter;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufValidationException;

class ProtobufPipelineRejectTest
{
    // captures the call-scoped diagnostic the pipeline pushes on a terminal REJECTED, copying it out
    private final String[] reason = new String[1];
    private final Category[] category = new Category[1];
    private final ProtobufReporter reporter = d ->
    {
        reason[0] = d.message();
        category[0] = d.category();
    };

    // An unterminated variable-length integer (continuation bit set, no terminating byte) is malformed
    // wire bytes that cannot be decoded at all -- a parse failure, not a schema violation.
    @Test
    void shouldReportParsingFailure()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser())
            .reporting(reporter)
            .into(Protobuf.generator());
        pipeline.reset();

        byte[] in = { (byte) 0xFF };
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[128]);
        Status status = pipeline.transform(srcOf(in), 0, in.length, true, dst, 0, dst.capacity()).status();

        assertEquals(Status.REJECTED, status);
        assertNotNull(reason[0]);
        assertEquals(Category.PARSING, category[0]);
    }

    // A stage's own ProtobufException (not a parsing nor a validation exception) stands in for an
    // extension's internal failure during its own transform logic.
    @Test
    void shouldReportTransformFailure()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser())
            .transform(failingWith(new ProtobufException("extension failure")))
            .reporting(reporter)
            .into(Protobuf.generator());
        pipeline.reset();

        byte[] in = { 0x08, 0x05 };
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[128]);
        Status status = pipeline.transform(srcOf(in), 0, in.length, true, dst, 0, dst.capacity()).status();

        assertEquals(Status.REJECTED, status);
        assertEquals("extension failure", reason[0]);
        assertEquals(Category.TRANSFORM, category[0]);
    }

    // ProtobufValidationException has no built-in stage that throws it yet (reserved for semantic
    // validation beyond structure), but the pipeline still categorizes it correctly wherever a stage does.
    @Test
    void shouldReportValidationFailure()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser())
            .transform(failingWith(new ProtobufValidationException("semantic violation")))
            .reporting(reporter)
            .into(Protobuf.generator());
        pipeline.reset();

        byte[] in = { 0x08, 0x05 };
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[128]);
        Status status = pipeline.transform(srcOf(in), 0, in.length, true, dst, 0, dst.capacity()).status();

        assertEquals(Status.REJECTED, status);
        assertEquals("semantic violation", reason[0]);
        assertEquals(Category.VALIDATION, category[0]);
    }

    @Test
    void shouldNotReportOnValidProtobuf()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser())
            .reporting(reporter)
            .into(Protobuf.generator());
        pipeline.reset();

        byte[] in = { 0x08, 0x05 };
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[128]);
        Status status = pipeline.transform(srcOf(in), 0, in.length, true, dst, 0, dst.capacity()).status();

        assertEquals(Status.COMPLETED, status);
        assertNull(reason[0]);
        assertNull(category[0]);
    }

    private static MutableDirectBufferEx srcOf(
        byte[] bytes)
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[bytes.length]);
        buffer.putBytes(0, bytes);
        return buffer;
    }

    private static ProtobufTransform failingWith(
        RuntimeException failure)
    {
        return new ProtobufTransform()
        {
            @Override
            public ProtobufPipeline.Status transform(
                ProtobufController control,
                ProtobufSource source,
                ProtobufEvent event,
                ProtobufSink sink)
            {
                throw failure;
            }
        };
    }
}
