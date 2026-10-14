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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufStream;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;

public class ProtobufDeterminismTest
{
    private final ProtobufSchema schema = Protobuf.schema()
        .message(ProtobufMessage.builder("P")
            .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
            .build())
        .build();

    @Test
    public void shouldReportParsersDeterministic()
    {
        assertTrue(Protobuf.parser().deterministic());
        assertTrue(Protobuf.parser(schema, "P").deterministic());
    }

    @Test
    public void shouldReportGeneratorDeterministic()
    {
        assertTrue(Protobuf.generator().deterministic());
    }

    @Test
    public void shouldReportSinksDeterministic()
    {
        ProtobufGenerator generator = wrappedGenerator();

        assertTrue(ProtobufSink.of(generator).deterministic());
        assertTrue(ProtobufSink.of(generator, schema, "P").deterministic());
        assertTrue(new ProtobufDiscardSinkImpl().deterministic());
    }

    @Test
    public void shouldReportValidatorDeterministic()
    {
        assertTrue(schema.validator("P").deterministic());
    }

    @Test
    public void shouldKeepPipelineDeterministicWithoutStages()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser())
            .into(ProtobufSink.of(wrappedGenerator()));

        assertTrue(pipeline.deterministic());
    }

    @Test
    public void shouldKeepPipelineDeterministicWithDeterministicStages()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P"))
            .transform(schema.validator("P"))
            .transform(passthrough(true))
            .into(ProtobufSink.of(wrappedGenerator(), schema, "P"));

        assertTrue(pipeline.deterministic());
    }

    @Test
    public void shouldKeepPipelineDeterministicIntoGenerator()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P"))
            .transform(schema.validator("P"))
            .into(wrappedGenerator());

        assertTrue(pipeline.deterministic());
    }

    @Test
    public void shouldReportPipelineNonDeterministicWithNonDeterministicFirstStage()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P"))
            .transform(passthrough(false))
            .transform(schema.validator("P"))
            .into(new ProtobufDiscardSinkImpl());

        assertFalse(pipeline.deterministic());
    }

    @Test
    public void shouldReportPipelineNonDeterministicWithNonDeterministicLastStage()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P"))
            .transform(schema.validator("P"))
            .transform(passthrough(false))
            .into(ProtobufSink.of(wrappedGenerator(), schema, "P"));

        assertFalse(pipeline.deterministic());
    }

    @Test
    public void shouldReportPipelineNonDeterministicWithNonDeterministicSink()
    {
        ProtobufStream stream = Protobuf.stream(Protobuf.parser(schema, "P"))
            .transform(schema.validator("P"));

        ProtobufPipeline pipeline = stream.into(new NonDeterministicSink());

        assertFalse(pipeline.deterministic());
    }

    @Test
    public void shouldKeepStageDescriptionReusableAcrossPipelines()
    {
        ProtobufStream stream = Protobuf.stream(Protobuf.parser(schema, "P"))
            .transform(passthrough(true));

        ProtobufPipeline deterministic = stream.into(new ProtobufDiscardSinkImpl());
        ProtobufPipeline nonDeterministic = stream.into(new NonDeterministicSink());

        assertTrue(deterministic.deterministic());
        assertFalse(nonDeterministic.deterministic());
    }

    private static ProtobufGenerator wrappedGenerator()
    {
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[64]);
        return Protobuf.generator().wrap(out, 0, out.capacity());
    }

    private static ProtobufTransform passthrough(
        boolean deterministic)
    {
        return new ProtobufTransform()
        {
            @Override
            public Status transform(
                ProtobufController control,
                ProtobufSource source,
                ProtobufEvent event,
                ProtobufSink sink)
            {
                return sink.transform(control, source, event);
            }

            @Override
            public boolean deterministic()
            {
                return deterministic;
            }
        };
    }

    private static final class NonDeterministicSink implements ProtobufSink
    {
        @Override
        public Status transform(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event)
        {
            return Status.ADVANCED;
        }

        @Override
        public boolean identity()
        {
            return false;
        }

        @Override
        public boolean deterministic()
        {
            return false;
        }
    }
}
