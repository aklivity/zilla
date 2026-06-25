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
package io.aklivity.zilla.runtime.common.json;

/**
 * Receives a {@link JsonDiagnostic} the pipeline pushes immediately before returning a terminal
 * {@link JsonPipeline.Status#REJECTED} — and only then, never on {@link JsonPipeline.Status#STARVED} or
 * {@link JsonPipeline.Status#SUSPENDED} back-pressure. Wire one at pipeline construction with
 * {@link JsonStream#reporting(JsonReporter)}.
 * <p>
 * The callback runs on the engine worker thread on the cold failure path. The supplied diagnostic is a
 * reused, call-scoped view valid only for the duration of the call; copy out anything that must outlive it
 * before returning. Do not block or perform I/O.
 */
@FunctionalInterface
public interface JsonReporter
{
    JsonReporter NONE = diagnostic -> {};

    void rejected(
        JsonDiagnostic diagnostic);
}
