/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.model;

import org.agrona.DirectBuffer;

/**
 * Per-worker factory for {@link ModelPipeline} transform sessions on the I/O hot path.
 * <p>
 * A {@code ModelHandler} is supplied by {@link ModelContext} and confined to a single I/O thread. It
 * owns the configuration-derived state shared across every stream — schema resolution and caches,
 * registered extraction paths, and padding policy — and vends a fresh {@link ModelPipeline} per
 * stream via {@link #supplyPipeline}.
 * </p>
 * <p>
 * {@link ModelContext} returns {@code null} when no model is configured; a caller that holds a
 * {@code null} handler forwards its bytes unchanged rather than driving a pipeline.
 * </p>
 *
 * @see ModelContext
 * @see ModelPipeline
 */
public interface ModelHandler
{
    /**
     * Supplies a new {@link ModelPipeline} for a single stream, wiring the given {@link ModelVisitor}
     * to receive any extracted field values as the pipeline transforms each value.
     *
     * @param visitor  the visitor to receive extracted field values
     * @return a new per-stream pipeline
     */
    ModelPipeline supplyPipeline(
        ModelVisitor visitor);

    /**
     * Supplies a new {@link ModelPipeline} for a single stream with no extraction visitor.
     *
     * @return a new per-stream pipeline
     */
    default ModelPipeline supplyPipeline()
    {
        return supplyPipeline(ModelVisitor.NONE);
    }

    /**
     * Registers a field extraction path so that pipelines vended by this handler will surface the
     * field value to their {@link ModelVisitor} when a value completes.
     * <p>
     * The path syntax is model-specific. Calling this method is optional; omitting it disables
     * extraction for that path.
     * </p>
     *
     * @param path  the field path to extract
     */
    default void extract(
        String path)
    {
    }

    /**
     * Returns the number of additional bytes required in the output buffer to accommodate any framing
     * overhead the transform may add (e.g., schema id prefix bytes).
     *
     * @param data   the source buffer containing the untransformed input
     * @param index  the offset of the input
     * @param length the length of the input
     * @return the padding byte count (0 for transforms that do not expand the input)
     */
    default int padding(
        DirectBuffer data,
        int index,
        int length)
    {
        return 0;
    }
}
