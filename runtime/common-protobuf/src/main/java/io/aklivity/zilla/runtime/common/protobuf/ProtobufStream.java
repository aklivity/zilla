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
}
