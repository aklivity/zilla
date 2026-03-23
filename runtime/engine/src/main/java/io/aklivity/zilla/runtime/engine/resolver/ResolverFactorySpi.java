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
package io.aklivity.zilla.runtime.engine.resolver;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.factory.FactorySpi;

/**
 * Service provider interface for creating {@link ResolverSpi} instances.
 * <p>
 * The engine discovers resolver factories via {@link java.util.ServiceLoader}. Each factory's
 * {@link #type()} name becomes the context prefix used in YAML expression substitution —
 * for example, a factory with type {@code "env"} handles all expressions of the form
 * {@code ${{env.VAR_NAME}}}.
 * </p>
 * <p>
 * Implementations must be registered in
 * {@code META-INF/services/io.aklivity.zilla.runtime.engine.resolver.ResolverFactorySpi}.
 * </p>
 *
 * @see ResolverSpi
 */
public interface ResolverFactorySpi extends FactorySpi
{
    /**
     * Creates a new {@link ResolverSpi} instance for the given engine configuration.
     * <p>
     * The factory's {@link #type()} name (inherited from {@link FactorySpi}) identifies
     * the expression context prefix this resolver handles in {@code zilla.yaml}.
     * </p>
     *
     * @param config  the engine configuration
     * @return a new {@link ResolverSpi}
     */
    ResolverSpi create(
        Configuration config);
}
