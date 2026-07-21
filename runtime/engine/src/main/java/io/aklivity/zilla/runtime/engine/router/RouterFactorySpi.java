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
package io.aklivity.zilla.runtime.engine.router;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.factory.FactorySpi;

/**
 * Service provider interface for creating {@link Router} instances.
 * <p>
 * Implementations must be registered in
 * {@code META-INF/services/io.aklivity.zilla.runtime.engine.router.RouterFactorySpi}.
 * </p>
 *
 * @see Router
 */
public interface RouterFactorySpi extends FactorySpi
{
    /**
     * Creates a new {@link Router} instance for the given engine configuration.
     *
     * @param config  the engine configuration
     * @return a new {@link Router}
     */
    Router create(
        Configuration config);
}
