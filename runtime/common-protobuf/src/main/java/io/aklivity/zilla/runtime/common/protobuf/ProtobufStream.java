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
package io.aklivity.zilla.runtime.common.protobuf;

/**
 * A description of a {@code common-protobuf} pipeline — a descriptor-bound driver plus an ordered list
 * of {@link ProtobufTransform} stages. Append stages with {@link #transform(ProtobufTransform)}
 * (left-to-right, in data-flow order); optionally attach a {@link ProtobufReporter} with
 * {@link #reporting(ProtobufReporter)}; terminate with {@link #into(ProtobufSink)} to obtain the runnable
 * {@link ProtobufPipeline}. A {@code ProtobufStream} carries no state and is not itself runnable.
 */
public interface ProtobufStream
{
    ProtobufStream transform(
        ProtobufTransform transform);

    /**
     * Attaches the {@link ProtobufReporter} the pipeline pushes a {@link ProtobufDiagnostic} to on a terminal
     * {@link ProtobufPipeline.Status#REJECTED}. The last attached reporter wins; the default is none.
     */
    ProtobufStream reporting(
        ProtobufReporter reporter);

    ProtobufPipeline into(
        ProtobufSink sink);

    /**
     * Terminates the pipeline with a schema-free, generator-backed sink the pipeline owns and re-targets
     * per {@link ProtobufPipeline#transform} call (a lossless structural copy, as {@link ProtobufSink#of(
     * ProtobufGenerator)}). The generator need not be wrapped over the caller's destination beforehand —
     * it is re-targeted at that destination on each {@code transform}. Use this terminal (rather than
     * {@link #into(ProtobufSink)} over a caller-wrapped generator) when driving the pipeline with
     * {@link ProtobufPipeline#transform}.
     */
    ProtobufPipeline into(
        ProtobufGenerator generator);

    /**
     * Terminates the pipeline with a schema-bound, generator-backed sink the pipeline owns and re-targets
     * per {@link ProtobufPipeline#transform} call — the wire-encoding terminal of {@link ProtobufSink#of(
     * ProtobufGenerator, ProtobufSchema, String)}, encoding each event against the message named
     * {@code messageName} in {@code schema}. The generator need not be wrapped over the caller's destination
     * beforehand — it is re-targeted at that destination on each {@code transform}. Use this terminal (rather
     * than {@link #into(ProtobufSink)} over a caller-wrapped generator) when driving the pipeline with
     * {@link ProtobufPipeline#transform}.
     */
    ProtobufPipeline into(
        ProtobufGenerator generator,
        ProtobufSchema schema,
        String messageName);
}
