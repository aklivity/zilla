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

import io.aklivity.zilla.config.engine.ModelConfig;

/**
 * Per-thread context for a data model.
 * <p>
 * Created once per I/O thread by {@link Model#supply(EngineContext)} and confined to that thread.
 * Supplies {@link ModelHandler} instances configured for a specific model configuration
 * (e.g., a particular Avro schema reference).
 * </p>
 *
 * @see Model
 * @see ModelHandler
 */
public interface ModelContext extends AutoCloseable
{
    /**
     * Closes this thread's context.
     * <p>
     * Called once when the engine worker closes, after all configuration has been detached and
     * streams have been torn down, so that resources held for the lifetime of this context are
     * released before the worker checks for resources that were not released. The default
     * implementation does nothing.
     * </p>
     */
    @Override
    default void close()
    {
    }

    /**
     * Returns a {@link ModelHandler} that vends per-stream decode and encode pipelines for the given
     * model configuration, or {@code null} if this model does not transform values.
     *
     * @param config  the model configuration
     * @return a {@link ModelHandler} for the configuration, or {@code null}
     */
    ModelHandler supplyHandler(
        ModelConfig config);
}
