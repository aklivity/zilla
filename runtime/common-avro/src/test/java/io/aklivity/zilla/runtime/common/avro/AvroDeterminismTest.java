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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;

class AvroDeterminismTest
{
    private final AvroSchema schema = Avro.schema("\"string\"");

    @Test
    void shouldReportDeterministicForNoneTransform()
    {
        assertTrue(AvroTransform.NONE.deterministic());
    }

    @Test
    void shouldReportDeterministicForValidatorTransform()
    {
        assertTrue(schema.validator().deterministic());
    }

    @Test
    void shouldReportDeterministicForParser()
    {
        assertTrue(Avro.parser(schema).deterministic());
    }

    @Test
    void shouldReportDeterministicForGeneratorAndSink()
    {
        AvroGenerator generator = generator();

        assertTrue(generator.deterministic());
        assertTrue(AvroSink.of(generator).deterministic());
    }

    @Test
    void shouldReportDeterministicForPipelineWithoutStages()
    {
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(generator());

        assertTrue(pipeline.deterministic());
    }

    @Test
    void shouldReportDeterministicForPipelineWithDeterministicStages()
    {
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(schema.validator())
            .transform(forwarding(true))
            .transform(forwarding(true))
            .into(generator());

        assertTrue(pipeline.deterministic());
    }

    @Test
    void shouldReportNonDeterministicForPipelineWithNonDeterministicStage()
    {
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(schema.validator())
            .transform(forwarding(false))
            .transform(forwarding(true))
            .into(generator());

        assertFalse(pipeline.deterministic());
    }

    @Test
    void shouldReportNonDeterministicForPipelineWithOnlyNonDeterministicStage()
    {
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(forwarding(false))
            .into(generator());

        assertFalse(pipeline.deterministic());
    }

    @Test
    void shouldReportNonDeterministicForPipelineWithNonDeterministicLastStage()
    {
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(forwarding(true))
            .transform(forwarding(false))
            .into(generator());

        assertFalse(pipeline.deterministic());
    }

    @Test
    void shouldReportNonDeterministicForPipelineIntoNonDeterministicSink()
    {
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(forwarding(true))
            .into(sink(false));

        assertFalse(pipeline.deterministic());
    }

    @Test
    void shouldReportDeterministicForPipelineIntoDeterministicSink()
    {
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(forwarding(true))
            .into(sink(true));

        assertTrue(pipeline.deterministic());
    }

    @Test
    void shouldKeepDeterminismIndependentOfIdentity()
    {
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(forwarding(true, false))
            .into(generator());

        assertFalse(pipeline.identity());
        assertTrue(pipeline.deterministic());
    }

    private AvroGenerator generator()
    {
        return Avro.generator(schema, new UnsafeBufferEx(new byte[1]), 0);
    }

    private static AvroTransform forwarding(
        boolean deterministic)
    {
        return forwarding(deterministic, true);
    }

    private static AvroTransform forwarding(
        boolean deterministic,
        boolean identity)
    {
        return new AvroTransform()
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
                return identity;
            }

            @Override
            public boolean deterministic()
            {
                return deterministic;
            }
        };
    }

    private static AvroSink sink(
        boolean deterministic)
    {
        return new AvroSink()
        {
            @Override
            public Status transform(
                AvroController control,
                AvroSource source,
                AvroEvent event)
            {
                return Status.ADVANCED;
            }

            @Override
            public boolean identity()
            {
                return true;
            }

            @Override
            public boolean deterministic()
            {
                return deterministic;
            }
        };
    }
}
