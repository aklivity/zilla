/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.llm.dialect;

import java.net.URL;

/**
 * Service provider interface for a pluggable {@link LlmDialect} implementation.
 * <p>
 * Each supported dialect provides an implementation, registered via {@link java.util.ServiceLoader} in
 * {@code META-INF/services/io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectFactorySpi}.
 * </p>
 */
public interface LlmDialectFactorySpi
{
    /**
     * Returns this factory's dialect name, matching {@link LlmDialect#name()} of the instance it creates.
     *
     * @return the dialect name
     */
    String name();

    /**
     * Creates a new {@link LlmDialect} instance.
     *
     * @return a new dialect
     */
    LlmDialect create();

    /**
     * Returns a URL to this dialect's own JSON schema for the given direction, so a caller can enforce it
     * without the dialect needing to know anything about how or where that enforcement happens.
     * <p>
     * Resolved once, from the factory, so every registered dialect's schema is discoverable without
     * constructing an {@link LlmDialect} instance. A dialect with nothing to contribute for a direction
     * (e.g. a fixed request shape needs no schema of its own) returns {@code null} for it.
     * </p>
     *
     * @param kind  the request or response direction
     * @return the schema URL, or {@code null} if this dialect contributes none for that direction
     */
    default URL schema(
        LlmDialect.Kind kind)
    {
        return null;
    }
}
