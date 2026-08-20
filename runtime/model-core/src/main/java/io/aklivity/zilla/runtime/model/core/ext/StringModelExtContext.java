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
package io.aklivity.zilla.runtime.model.core.ext;

import io.aklivity.zilla.config.model.core.StringModelConfig;

/**
 * The per-worker context for one {@link StringModelExt}, closed over the
 * {@link io.aklivity.zilla.runtime.engine.EngineContext} it was supplied with.
 */
public interface StringModelExtContext
{
    /**
     * Supplies the handler for one string model configuration. Unlike a schema-bound model, {@code string}
     * has no schema to resolve, so this is called once per model construction rather than once per
     * resolved schema.
     *
     * @param config  the string model configuration
     * @return the {@link StringModelExtHandler} for this configuration
     */
    StringModelExtHandler supplyHandler(
        StringModelConfig config);
}
