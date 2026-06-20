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
 * An intermediate stage in a {@link ProtobufStream} pipeline that transforms the event stream —
 * forwarding, dropping, or substituting events — before they reach the next stage. Each
 * {@link #feed(ProtobufController, ProtobufSource, ProtobufEvent, ProtobufSink)} consumes one event and
 * forwards what it keeps to {@code sink} (the downstream, bound once at assembly). A mediating stage
 * supplies its own {@link ProtobufController} to {@code sink}; a non-mediating stage passes {@code control}
 * through. Stages compose left-to-right via {@link ProtobufStream#transform(ProtobufTransform)}.
 */
public interface ProtobufTransform
{
    ProtobufPipeline.Status feed(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event,
        ProtobufSink sink);

    /**
     * Continues after a prior {@link #feed} returned {@link ProtobufPipeline.Status#SUSPENDED}, with the
     * {@code event} that suspended supplied by the pump. The default forwards to
     * {@code sink.resume(control, source, event)}, so a stage that does not originate suspension needs no
     * awareness of it; a stage that returns {@code SUSPENDED} on its own (e.g. to emit buffered events)
     * overrides this to drive its own resumption before delegating downstream.
     */
    default ProtobufPipeline.Status resume(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event,
        ProtobufSink sink)
    {
        return sink.resume(control, source, event);
    }

    default void reset()
    {
    }
}
