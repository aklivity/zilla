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

import io.aklivity.zilla.config.engine.OptionsConfig;

/**
 * Service provider interface for a pluggable {@link LlmRequestSigner} implementation.
 * <p>
 * Each supported signing scheme provides an implementation, registered via {@link java.util.ServiceLoader} in
 * {@code META-INF/services/io.aklivity.zilla.runtime.binding.llm.sign.LlmRequestSignerFactorySpi}.
 * </p>
 */
public interface LlmRequestSignerFactorySpi
{
    /**
     * Returns this factory's signer name, used to select it explicitly via configuration.
     *
     * @return the signer name
     */
    String name();

    /**
     * Creates a new {@link LlmRequestSigner} instance.
     *
     * @param context  the context giving access to engine services this signer may need, e.g. a store for
     *                 cross-worker credential coordination
     * @param options  this signer's own configuration, resolved from the {@code options} sub-object paired
     *                 with its name in the binding's {@code sign} configuration, or {@code null} when the
     *                 binding selected this signer by its bare name with no options
     * @return a new signer
     */
    LlmRequestSigner create(
        LlmRequestSignerContext context,
        OptionsConfig options);
}
