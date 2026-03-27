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
package io.aklivity.zilla.runtime.engine.guard;

import java.net.URL;
import java.util.function.LongFunction;
import java.util.function.LongToIntFunction;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

/**
 * Entry point for an authorization guard plugin.
 * <p>
 * A {@code Guard} validates and manages session credentials for streams passing through a binding.
 * The canonical implementation is {@code guard-jwt}, which validates JSON Web Tokens and tracks
 * session expiry and challenge windows. The guard is queried synchronously on the I/O thread via
 * the {@link GuardHandler} returned from its {@link GuardContext}.
 * </p>
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} through {@link GuardFactorySpi}.
 * </p>
 *
 * @see GuardContext
 * @see GuardHandler
 * @see GuardFactorySpi
 */
public interface Guard
{
    /**
     * Returns the unique name identifying this guard type, e.g. {@code "jwt"}.
     *
     * @return the guard type name
     */
    String name();

    /**
     * Creates a per-thread context for this guard.
     * <p>
     * Called once per I/O thread. The returned {@link GuardContext} is confined to that thread
     * and may hold thread-local state without synchronization.
     * </p>
     *
     * @param context  the engine context for the calling I/O thread
     * @return a new {@link GuardContext}
     */
    GuardContext supply(
        EngineContext context);

    /**
     * Returns a predicate that verifies whether a session identified by its session id is
     * currently authorized, optionally applying a credential transformation.
     * <p>
     * The returned predicate accepts a session id (long) and a {@code UnaryOperator<String>}
     * for transforming the raw credentials before verification (e.g., extracting a sub-claim).
     * </p>
     *
     * @param indexOf  function mapping a session id to the index of the engine thread that owns it
     * @param config   the guarded configuration specifying roles and other constraints
     * @return a predicate that returns {@code true} if the session is currently authorized
     */
    LongObjectPredicate<UnaryOperator<String>> verifier(
        LongToIntFunction indexOf,
        GuardedConfig config);

    /**
     * Returns a function that resolves the identity string for an authorized session.
     *
     * @param indexOf  function mapping a session id to the index of the engine thread that owns it
     * @param config   the guarded configuration
     * @return a function from session id to identity string (e.g., a JWT {@code sub} claim)
     */
    LongFunction<String> identifier(
        LongToIntFunction indexOf,
        GuardedConfig config);

    /**
     * Returns a function that resolves a named attribute value for an authorized session.
     * <p>
     * Attributes are arbitrary string values extracted from credentials (e.g., custom JWT claims).
     * </p>
     *
     * @param indexOf  function mapping a session id to the index of the engine thread that owns it
     * @param config   the guarded configuration
     * @return a bi-function from (session id, attribute name) to attribute value string
     */
    LongObjectBiFunction<String, String> attributor(
        LongToIntFunction indexOf,
        GuardedConfig config);

    /**
     * Returns a URL pointing to the JSON schema for this guard's configuration options.
     *
     * @return the configuration schema URL
     */
    URL type();
}
