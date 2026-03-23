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

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;

/**
 * Entry point for a data model plugin.
 * <p>
 * A {@code Model} provides payload conversion and validation for a specific data format such as
 * Avro, Protobuf, or JSON. When a binding is configured with a model, inbound and outbound
 * payloads are passed through the model's converter and validator handlers on the hot path.
 * </p>
 * <p>
 * Built-in implementations include {@code model-avro}, {@code model-protobuf}, {@code model-json},
 * and {@code model-core}.
 * </p>
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} through {@link ModelFactorySpi}.
 * </p>
 *
 * @see ModelContext
 * @see ModelFactorySpi
 */
public interface Model
{
    /**
     * Returns the unique name identifying this model type, e.g. {@code "avro"}.
     *
     * @return the model type name
     */
    String name();

    /**
     * Creates a per-thread context for this model.
     *
     * @param context  the engine context for the calling I/O thread
     * @return a new {@link ModelContext} confined to that thread
     */
    ModelContext supply(
        EngineContext context);

    /**
     * Returns a URL pointing to the JSON schema for this model's configuration options.
     *
     * @return the configuration schema URL
     */
    URL type();
}
