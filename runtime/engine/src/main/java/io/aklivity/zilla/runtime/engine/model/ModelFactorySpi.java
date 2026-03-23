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

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.factory.FactorySpi;

/**
 * Service provider interface for creating {@link Model} instances.
 * <p>
 * Implementations must be registered in
 * {@code META-INF/services/io.aklivity.zilla.runtime.engine.model.ModelFactorySpi}.
 * The {@link #type()} method returns the model type name used in {@code zilla.yaml}.
 * </p>
 *
 * @see Model
 */
public interface ModelFactorySpi extends FactorySpi
{
    /**
     * Returns the model type name, e.g. {@code "avro"}.
     *
     * @return the model type name
     */
    String type();

    /**
     * Returns a URL pointing to the JSON schema for this model's options configuration,
     * used to validate model references in {@code zilla.yaml}.
     *
     * @return the options schema URL
     */
    URL schema();

    /**
     * Creates a new {@link Model} instance for the given engine configuration.
     *
     * @param config  the engine configuration
     * @return a new {@link Model}
     */
    Model create(
        Configuration config);
}
