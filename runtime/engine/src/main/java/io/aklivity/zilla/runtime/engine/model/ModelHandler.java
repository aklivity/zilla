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
 * and padding policy — and vends a fresh {@link ModelPipeline} per
 * stream via {@link #supplyDecoder} and {@link #supplyEncoder}.
 * </p>
 * <p>
 * {@link ModelContext} returns {@code null} when no model is configured; a caller that holds a
 * {@code null} handler forwards its bytes unchanged rather than driving a pipeline.
 * </p>
 * <p>
 * Neither the {@link ModelEnvelope} nor the {@link ModelTransform} supplied to
 * {@link #supplyDecoder(ModelEnvelope, ModelTransform)} and
 * {@link #supplyEncoder(ModelEnvelope, ModelTransform)} is ever {@code null}: a caller with no metadata
 * channel passes {@link ModelEnvelope#NONE} and a caller with no per-field policy passes
 * {@link ModelTransform#NONE}, both of which an implementation is free to recognize and wire away
 * entirely.
 * </p>
 * <p>
 * A model whose pipeline may report {@link ModelStatus#SUSPENDED} is supplied via the 3-arg
 * {@link #supplyDecoder(ModelEnvelope, ModelTransform, Runnable)} /
 * {@link #supplyEncoder(ModelEnvelope, ModelTransform, Runnable)} overloads instead, registering a
 * {@code resumed} callback once at pipeline-creation time; the pipeline invokes it when async work
 * started during a suspended {@code transform} call completes, so the caller knows to call
 * {@code transform} again rather than poll. The default implementations of these overloads forward to
 * the 2-arg methods with a no-op callback, so a model that never suspends needs no changes.
 * </p>
 *
 * @see ModelContext
 * @see ModelPipeline
 */
public interface ModelHandler
{
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

    /**
     * Supplies a new read-direction {@link ModelPipeline} as {@link #supplyDecoder(ModelEnvelope, ModelTransform)}
     * does, additionally registering a callback the pipeline invokes when async work started during a
     * {@link ModelStatus#SUSPENDED} outcome completes.
     *
     * @param envelope   the metadata channel to bind the pipeline to
     * @param transform  the per-field transform to wire into the pipeline
     * @param resumed    invoked when a previously suspended value is ready to be resumed
     * @return a new per-stream decode pipeline
     */
    default ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        Runnable resumed)
    {
        return supplyDecoder(envelope, transform);
    }

    /**
     * Supplies a new write-direction {@link ModelPipeline} as {@link #supplyEncoder(ModelEnvelope, ModelTransform)}
     * does, additionally registering a callback the pipeline invokes when async work started during a
     * {@link ModelStatus#SUSPENDED} outcome completes.
     *
     * @param envelope   the metadata channel to bind the pipeline to
     * @param transform  the per-field transform to wire into the pipeline
     * @param resumed    invoked when a previously suspended value is ready to be resumed
     * @return a new per-stream encode pipeline
     */
    default ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        Runnable resumed)
    {
        return supplyEncoder(envelope, transform);
    }
}
