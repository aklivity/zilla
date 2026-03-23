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
package io.aklivity.zilla.runtime.engine.binding;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.factory.FactorySpi;

/**
 * Service provider interface for creating {@link Binding} instances.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} and must be registered in
 * {@code META-INF/services/io.aklivity.zilla.runtime.engine.binding.BindingFactorySpi}.
 * The {@link #type()} method (inherited from {@link FactorySpi}) returns the binding type name
 * that matches the {@code type} field in {@code zilla.yaml}.
 * </p>
 *
 * @see Binding
 * @see FactorySpi
 */
public interface BindingFactorySpi extends FactorySpi
{
    /**
     * Creates a new {@link Binding} instance for the given engine configuration.
     *
     * @param config  the engine configuration
     * @return a new {@link Binding}
     */
    Binding create(
        Configuration config);
}
