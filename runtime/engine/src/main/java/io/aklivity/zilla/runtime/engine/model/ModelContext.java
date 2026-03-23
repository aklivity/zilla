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

import io.aklivity.zilla.runtime.engine.config.ModelConfig;

/**
 * Per-thread context for a data model.
 * <p>
 * Created once per I/O thread by {@link Model#supply(EngineContext)} and confined to that thread.
 * Supplies {@link ConverterHandler} and {@link ValidatorHandler} instances configured for a
 * specific model configuration (e.g., a particular Avro schema reference).
 * </p>
 *
 * @see Model
 * @see ConverterHandler
 * @see ValidatorHandler
 */
public interface ModelContext
{
    /**
     * Returns a {@link ConverterHandler} that converts inbound (read) payloads according to
     * the given model configuration.
     * <p>
     * For example, an Avro read converter might decode binary Avro into JSON for downstream
     * consumption.
     * </p>
     *
     * @param config  the model configuration
     * @return a {@link ConverterHandler} for inbound conversion
     */
    ConverterHandler supplyReadConverterHandler(
        ModelConfig config);

    /**
     * Returns a {@link ConverterHandler} that converts outbound (write) payloads according to
     * the given model configuration.
     * <p>
     * For example, an Avro write converter might encode JSON into binary Avro before forwarding
     * to Kafka.
     * </p>
     *
     * @param config  the model configuration
     * @return a {@link ConverterHandler} for outbound conversion
     */
    ConverterHandler supplyWriteConverterHandler(
        ModelConfig config);

    /**
     * Returns a {@link ValidatorHandler} that validates payloads against the given model
     * configuration, or {@code null} if this model does not support standalone validation.
     *
     * @param config  the model configuration
     * @return a {@link ValidatorHandler}, or {@code null}
     */
    default ValidatorHandler supplyValidatorHandler(
        ModelConfig config)
    {
        return null;
    }
}
