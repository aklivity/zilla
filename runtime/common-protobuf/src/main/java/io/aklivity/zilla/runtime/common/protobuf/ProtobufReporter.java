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
 * Receives a {@link ProtobufDiagnostic} the pipeline pushes immediately before returning a terminal
 * {@link ProtobufPipeline.Status#REJECTED} — and only then, never on {@link ProtobufPipeline.Status#STARVED}
 * or {@link ProtobufPipeline.Status#SUSPENDED} back-pressure. Wire one at pipeline construction with
 * {@link ProtobufStream#reporting(ProtobufReporter)}.
 * <p>
 * The callback runs on the engine worker thread on the cold failure path. The supplied diagnostic is a
 * reused, call-scoped view valid only for the duration of the call; copy out anything that must outlive it
 * before returning. Do not block or perform I/O.
 */
@FunctionalInterface
public interface ProtobufReporter
{
    ProtobufReporter NONE = diagnostic -> {};

    void rejected(
        ProtobufDiagnostic diagnostic);
}
