/*
 * Copyright 2021-2023 Aklivity Inc.
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
import java.util.function.LongPredicate;
import java.util.function.LongToIntFunction;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;

public interface Guard
{
    String name();

    GuardContext supply(
        EngineContext context);

    /*
     * Returns a verifier for the specified guarded configuration.
     *
     * @param indexOf  maps session id to engine index
     * @param config   the guarded configuration
     *
     * @return  the session verifier predicate
     */
    LongPredicate verifier(
        LongToIntFunction indexOf,
        GuardedConfig config);

    /*
     * Returns an identifier for the specified guarded configuration.
     *
     * @param indexOf  maps session id to engine index
     * @param config   the guarded configuration
     *
     * @return  the session identity function
     */
    LongFunction<String> identifier(
        LongToIntFunction indexOf,
        GuardedConfig config);

    URL type();
}
