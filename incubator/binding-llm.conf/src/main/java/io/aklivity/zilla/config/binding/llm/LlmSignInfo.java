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
package io.aklivity.zilla.config.binding.llm;

import jakarta.json.JsonObject;

import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.factory.FactorySpi;

/**
 * Service provider interface resolving a named request signer's own {@code options} sub-object into a
 * typed {@link OptionsConfig}, the same way this binding resolves a catalog's, guard's, or vault's own
 * {@code options} sub-object from its registered type.
 * <p>
 * Registered via {@link java.util.ServiceLoader} in
 * {@code META-INF/services/io.aklivity.zilla.config.binding.llm.LlmSignInfo}, keyed by {@link #type()}
 * matching the request signer's own name.
 * </p>
 */
public interface LlmSignInfo extends FactorySpi
{
    /**
     * Returns the adapter resolving this signer's own {@code options} sub-object.
     *
     * @return the options adapter
     */
    ConfigAdapter<OptionsConfig, JsonObject> options();
}
