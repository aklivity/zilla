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

import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

/**
 * Engine services a {@link LlmDialect} may need beyond the request it is asked to encode/decode, e.g. a
 * signaler for scheduling background credential refresh behind a {@link LlmDialect#signer()}.
 */
public interface LlmDialectContext
{
    /**
     * The signaler for scheduling work strictly later on this binding's worker thread.
     *
     * @return the signaler
     */
    Signaler signaler();
}
