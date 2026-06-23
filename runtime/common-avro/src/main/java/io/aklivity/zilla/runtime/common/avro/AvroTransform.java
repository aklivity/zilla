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
 * An intermediate stage in an {@link AvroStream} pipeline that transforms the event stream —
 * forwarding, dropping, or substituting events — before they reach the next stage. Each
 * {@link #transform(AvroController, AvroSource, AvroEvent, AvroSink)} consumes one event and forwards what
 * it keeps to {@code sink} (the downstream, bound once at assembly). A mediating stage supplies its own
 * {@link AvroController} to {@code sink}; a non-mediating stage passes {@code control} through. Stages
 * compose left-to-right via {@link AvroStream#transform(AvroTransform)}. {@link AvroSchema#validator()}
 * returns a streaming validator stage.
 */
public interface AvroTransform
{
    AvroPipeline.Status transform(
        AvroController control,
        AvroSource source,
        AvroEvent event,
        AvroSink sink);

    /**
     * Resumes the downstream after a {@link AvroPipeline.Status#SUSPENDED} return, once the caller has
     * drained the bounded output. The default forwards to {@code sink}, so a stage that merely forwards
     * events never re-sees them on resume; a stage that fans out or buffers overrides this to continue
     * its own emission before forwarding.
     */
    default AvroPipeline.Status resume(
        AvroController control,
        AvroSource source,
        AvroEvent event,
        AvroSink sink)
    {
        return sink.resume(control, source, event);
    }

    default void reset()
    {
    }

    /**
     * Whether this stage forwards every event verbatim, leaving the bytes unchanged. A validating or
     * observing stage is identity; a stage that substitutes, drops, or rewrites events is not.
     */
    default boolean identity()
    {
        return true;
    }
}
