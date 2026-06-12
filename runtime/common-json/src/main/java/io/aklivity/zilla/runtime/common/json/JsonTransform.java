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
 * An intermediate stage in a {@link JsonStream} pipeline that transforms the event stream — forwarding,
 * dropping, or substituting events — before they reach the next stage. Each
 * {@link #feed(JsonController, JsonSource, JsonEvent, JsonSink)} consumes one event and forwards what it
 * keeps to {@code sink} (the downstream, bound once at assembly), optionally substituting a value by
 * feeding {@code sink} a different {@link JsonSource}. A mediating stage supplies its own
 * {@link JsonController} to {@code sink}; a non-mediating stage passes {@code control} through. Stages
 * compose left-to-right via {@link JsonStream#transform(JsonTransform)}. Third parties may implement this
 * contract (e.g. field masking or encryption).
 */
public interface JsonTransform
{
    JsonPipeline.Status feed(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink);

    /**
     * Continues output this stage left in flight by a prior {@link JsonPipeline.Status#SUSPENDED} —
     * a value it was emitting to {@code sink} across chunks — before the next event is fed. Returns
     * {@link JsonPipeline.Status#SUSPENDED} if the bounded output filled again, or {@link
     * JsonPipeline.Status#RESUMABLE} when this stage has nothing more to emit. The {@code resume()}
     * cascade drains the downstream first, so a stage that only forwards events keeps the default.
     */
    default JsonPipeline.Status resume(
        JsonSink sink)
    {
        return JsonPipeline.Status.RESUMABLE;
    }

    default void reset()
    {
    }
}
