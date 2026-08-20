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
package io.aklivity.zilla.runtime.model.core.ext;

import io.aklivity.zilla.runtime.engine.model.ModelStatus;

/**
 * An intermediate stage in a {@code bytes} pipeline that transforms the event stream — forwarding,
 * dropping, or substituting events — before they reach the next stage.
 * <p>
 * Each {@link #transform(BytesController, BytesSource, BytesEvent, BytesSink)} consumes one event and
 * forwards what it keeps to {@code sink} (the downstream, bound once at assembly), optionally substituting
 * bytes by feeding {@code sink} a {@link BytesSource} of its own. A mediating stage supplies its own
 * {@link BytesController} to {@code sink}; a non-mediating stage passes {@code control} through. Stages
 * compose left-to-right via {@link BytesTransformable#transform(BytesTransform)}.
 * </p>
 * <p>
 * A stage sees the value as it flows, one {@link BytesEvent#SEGMENT} per fragment, so it never waits for
 * the whole value; it suspends against a bounded destination by returning {@link ModelStatus#OVERFLOW};
 * and it terminates a value through {@link BytesController#reject(String)} or
 * {@link BytesController#withhold()}.
 * </p>
 * <p>
 * A stage holds the in-flight state of exactly one value, so a fresh instance is bound per stream rather
 * than shared across the streams one handler serves.
 * </p>
 */
public interface BytesTransform
{
    /**
     * Identity stage that forwards every event unchanged.
     * {@link BytesTransformable#transform(BytesTransform)} drops it rather than binding it, so a caller
     * with nothing to insert passes this instead of branching, and the assembled pipeline carries no stage
     * at all.
     */
    BytesTransform NONE = new BytesTransform()
    {
        @Override
        public ModelStatus transform(
            BytesController control,
            BytesSource source,
            BytesEvent event,
            BytesSink sink)
        {
            return sink.transform(control, source, event);
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    };

    /**
     * Consumes one event and forwards what it keeps to {@code sink}.
     *
     * @param control  the control handle for the immediate upstream
     * @param source   the read-only view of the value bytes the event carries
     * @param event    the event
     * @param sink     the downstream stage
     * @return the outcome of consuming the event
     */
    ModelStatus transform(
        BytesController control,
        BytesSource source,
        BytesEvent event,
        BytesSink sink);

    /**
     * Resumes after a {@link ModelStatus#OVERFLOW} return, once the caller has drained the bounded
     * destination. The default forwards to {@code sink}, so a stage that merely forwards events never
     * re-sees them on resume; a stage that buffers or substitutes overrides this to continue its own
     * emission before forwarding.
     *
     * @param control  the control handle for the immediate upstream
     * @param source   the read-only view of the value bytes still to be consumed
     * @param event    the event that suspended
     * @param sink     the downstream stage
     * @return the outcome of resuming the event
     */
    default ModelStatus resume(
        BytesController control,
        BytesSource source,
        BytesEvent event,
        BytesSink sink)
    {
        return sink.resume(control, source, event);
    }

    /**
     * Discards any in-flight state so the stage is ready for the next value.
     */
    default void reset()
    {
    }

    /**
     * Whether this stage forwards every event verbatim, leaving the bytes unchanged. A validating or
     * observing stage is identity; a stage that substitutes, drops, or rewrites bytes is not.
     *
     * @return {@code true} if every value passes through unchanged; {@code false} otherwise
     */
    default boolean identity()
    {
        return false;
    }
}
