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

/**
 * A description of a {@code common-avro} pipeline — the schema-bound parse driver (from
 * {@link AvroSchema#parser()}) plus an ordered list of {@link AvroTransform} stages. Append stages with
 * {@link #transform(AvroTransform)} (left-to-right, in data-flow order); optionally attach an
 * {@link AvroReporter} with {@link #reporting(AvroReporter)}; terminate with {@link #into(AvroSink)} to
 * obtain the runnable, resumable {@link AvroPipeline}. An {@code AvroStream} carries no state and is not
 * itself runnable.
 */
public interface AvroStream
{
    AvroStream transform(
        AvroTransform transform);

    /**
     * Relaxes the pipeline's handling of a semantic-validation failure ({@link AvroValidationException}) on a
     * structurally well-formed value. When {@code true}, such a failure is reported and then the
     * already-produced structurally-valid value passes through (the pipeline still completes); when
     * {@code false} (the default), it rejects. A parse failure ({@link AvroParsingException}) always rejects
     * regardless of this setting. Currently inert: no stage throws {@link AvroValidationException} yet, so
     * the branch this setting selects is wired but unreached until semantic validation lands.
     */
    AvroStream lenient(
        boolean lenient);

    /**
     * Attaches the {@link AvroReporter} the pipeline pushes an {@link AvroDiagnostic} to on a terminal
     * {@link AvroPipeline.Status#REJECTED} or on a reported semantic-validation failure. The last attached
     * reporter wins; the default is none.
     */
    AvroStream reporting(
        AvroReporter reporter);

    AvroPipeline into(
        AvroSink sink);

    /**
     * Terminates the pipeline with a generator-backed sink the pipeline owns and re-targets per
     * {@link AvroPipeline#transform} call. The generator need not be wrapped over the caller's destination
     * beforehand — it is re-targeted at that destination on each {@code transform}. Use this terminal
     * (rather than {@link #into(AvroSink)} over a caller-wrapped generator) when driving the pipeline with
     * {@link AvroPipeline#transform}.
     */
    AvroPipeline into(
        AvroGenerator generator);
}
