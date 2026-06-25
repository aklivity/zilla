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
 *
 * @see ModelContext
 * @see ModelPipeline
 */
public interface ModelHandler
{
    /**
     * Supplies a new read-direction {@link ModelPipeline} for a single stream, wiring the given
     * {@link ModelVisitor} to receive any extracted field values as the pipeline transforms each value.
     *
     * @param visitor  the visitor to receive extracted field values
     * @return a new per-stream decode pipeline
     */
    ModelPipeline supplyDecoder(
        ModelVisitor visitor);

    /**
     * Supplies a new read-direction {@link ModelPipeline} for a single stream with no extraction visitor.
     *
     * @return a new per-stream decode pipeline
     */
    default ModelPipeline supplyDecoder()
    {
        return supplyDecoder(ModelVisitor.NONE);
    }

    /**
     * Supplies a new write-direction {@link ModelPipeline} for a single stream, wiring the given
     * {@link ModelVisitor} to receive any extracted field values as the pipeline transforms each value.
     *
     * @param visitor  the visitor to receive extracted field values
     * @return a new per-stream encode pipeline
     */
    ModelPipeline supplyEncoder(
        ModelVisitor visitor);

    /**
     * Supplies a new write-direction {@link ModelPipeline} for a single stream with no extraction visitor.
     *
     * @return a new per-stream encode pipeline
     */
    default ModelPipeline supplyEncoder()
    {
        return supplyEncoder(ModelVisitor.NONE);
    }
}
