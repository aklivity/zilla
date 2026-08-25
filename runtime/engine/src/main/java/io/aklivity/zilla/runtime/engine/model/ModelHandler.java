/*
 * Copyright 2021-2026 Aklivity Inc.
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

/**
 * Per-worker factory for {@link ModelPipeline} transform sessions on the I/O hot path.
 * <p>
 * A {@code ModelHandler} is supplied by {@link ModelContext} and confined to a single I/O thread. It
 * owns the configuration-derived state shared across every stream — schema resolution and caches,
 * and padding policy — and vends a fresh {@link ModelPipeline} per stream via {@link #supplyCacheable},
 * {@link #supplyDecoder}, and {@link #supplyEncoder}.
 * </p>
 * <p>
 * {@link ModelContext} returns {@code null} when no model is configured; a caller that holds a
 * {@code null} handler forwards its bytes unchanged rather than driving a pipeline.
 * </p>
 * <p>
 * Neither the {@link ModelEnvelope} nor the {@link ModelTransform} supplied to any of these methods is
 * ever {@code null}: a caller with no metadata channel passes {@link ModelEnvelope#NONE} and a caller
 * with no per-field policy passes {@link ModelTransform#NONE}, both of which an implementation is free
 * to recognize and wire away entirely.
 * </p>
 *
 * @see ModelContext
 * @see ModelPipeline
 */
public interface ModelHandler
{
    /**
     * Supplies a new read-direction {@link ModelPipeline} for a single stream, intended for a caller that
     * persists the transformed value ahead of any specific consumer's request, binding it to the given
     * {@link ModelEnvelope} and wiring the given {@link ModelTransform} exactly as {@link #supplyDecoder}
     * does.
     * <p>
     * The distinction from {@link #supplyDecoder} is entirely about when a caller invokes the pipeline
     * relative to who will read the transformed value: a caller that stores a value for later,
     * unspecified consumers uses this method once at the time it is stored; a caller that produces a
     * value for the specific consumer requesting it right now uses {@link #supplyDecoder}. An
     * implementation with nothing consumer-specific to apply returns the identical pipeline behavior
     * from both methods.
     * </p>
     *
     * @param envelope   the metadata channel to bind the pipeline to
     * @param transform  the per-field transform to wire into the pipeline
     * @return a new per-stream pipeline suitable for transforming a value ahead of a specific consumer
     */
    ModelPipeline supplyCacheable(
        ModelEnvelope envelope,
        ModelTransform transform);

    /**
     * Supplies a new read-direction {@link ModelPipeline} for a single stream, binding it to the given
     * {@link ModelEnvelope} so the pipeline reads the metadata travelling alongside each value it
     * transforms, and wiring the given {@link ModelTransform} into it so the transform observes,
     * substitutes, or declines each field of that value.
     * <p>
     * The envelope is bound as the pipeline is built rather than supplied again per value, so an
     * implementation adapts it into its own internals once. The supplier owns its lifecycle from there.
     * </p>
     *
     * @param envelope   the metadata channel to bind the pipeline to
     * @param transform  the per-field transform to wire into the pipeline
     * @return a new per-stream decode pipeline
     */
    ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform);

    /**
     * Supplies a new write-direction {@link ModelPipeline} for a single stream, binding it to the given
     * {@link ModelEnvelope} so the pipeline writes the metadata travelling alongside each value it
     * transforms, and wiring the given {@link ModelTransform} into it so the transform observes,
     * substitutes, or declines each field of that value.
     * <p>
     * The envelope is bound as the pipeline is built rather than supplied again per value, so an
     * implementation adapts it into its own internals once. The supplier owns its lifecycle from there,
     * draining whatever accumulated into it as the scope it describes turns over.
     * </p>
     *
     * @param envelope   the metadata channel to bind the pipeline to
     * @param transform  the per-field transform to wire into the pipeline
     * @return a new per-stream encode pipeline
     */
    ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform);
}
