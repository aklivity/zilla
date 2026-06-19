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
 * A description of a {@code common-json} pipeline — a driver bound by {@link JsonEx#stream(JsonParserEx)} plus
 * an ordered list of {@link JsonTransform} stages. Append stages with {@link #transform(JsonTransform)}
 * (left-to-right, in data-flow order); optionally attach a {@link JsonReporter} with
 * {@link #reporting(JsonReporter)}; terminate with {@link #into(JsonSink)} to obtain the runnable,
 * resumable {@link JsonPipeline}. A {@code JsonStream} carries no state and is not itself runnable.
 */
public interface JsonStream
{
    JsonStream transform(
        JsonTransform transform);

    /**
     * Attaches the {@link JsonReporter} the pipeline pushes a {@link JsonDiagnostic} to on a terminal
     * {@link JsonPipeline.Status#REJECTED}. The last attached reporter wins; the default is none.
     */
    JsonStream reporting(
        JsonReporter reporter);

    JsonPipeline into(
        JsonSink sink);

    /**
     * Terminates the pipeline with a generator-backed sink the pipeline owns and re-targets per
     * {@link JsonPipeline#transform} call. The generator need not be wrapped over a buffer beforehand —
     * it is wrapped over an empty buffer at assembly and re-targeted at the caller's destination on each
     * {@code transform}. Use this terminal (rather than {@link #into(JsonSink)} over a caller-wrapped
     * generator) when driving the pipeline with {@link JsonPipeline#transform}.
     */
    JsonPipeline into(
        JsonGeneratorEx generator);
}
