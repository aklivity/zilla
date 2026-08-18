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
package io.aklivity.zilla.runtime.model.json.ext;

import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.common.json.JsonSchema;

/**
 * The per-worker context for one {@link JsonModelExt}, closed over the {@link io.aklivity.zilla.runtime.engine.EngineContext}
 * it was supplied with.
 */
public interface JsonModelExtContext
{
    /**
     * Supplies the handler for one resolved schema, bound to a specific {@link JsonModelConfig}.
     *
     * @param schema  the resolved schema
     * @param config  the json model configuration
     * @return the {@link JsonModelExtHandler} for this schema and configuration
     */
    JsonModelExtHandler supplyHandler(
        JsonSchema schema,
        JsonModelConfig config);
}
