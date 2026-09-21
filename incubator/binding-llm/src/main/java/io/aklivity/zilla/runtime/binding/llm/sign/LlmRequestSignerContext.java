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
package io.aklivity.zilla.runtime.binding.llm.sign;

import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

/**
 * Engine services a {@link LlmRequestSigner} may need beyond the request it is asked to sign, e.g. a
 * store for coordinating credential renewal across this binding's per-worker instances, or a signaler for
 * scheduling that renewal.
 */
public interface LlmRequestSignerContext
{
    /**
     * Resolves the named store, or {@code null} when no store by that name is configured.
     *
     * @param name the store name
     * @return the store, or {@code null}
     */
    StoreHandler store(
        String name);

    /**
     * The signaler for scheduling work strictly later on this binding's worker thread.
     *
     * @return the signaler
     */
    Signaler signaler();
}
