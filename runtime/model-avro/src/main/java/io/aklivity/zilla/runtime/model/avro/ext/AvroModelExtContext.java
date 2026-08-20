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
package io.aklivity.zilla.runtime.model.avro.ext;

import io.aklivity.zilla.config.model.avro.AvroModelConfig;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;

/**
 * The per-worker context for one {@link AvroModelExt}, closed over the {@link io.aklivity.zilla.runtime.engine.EngineContext}
 * it was supplied with.
 */
public interface AvroModelExtContext
{
    /**
     * Called once when a binding attaches this configuration, before any schema is resolved or any
     * stream begins. An extension whose behavior depends on a resource that resolves asynchronously
     * (e.g. a vault-wrapped key) can use this to kick off that resolution eagerly, from whatever the
     * configuration alone already determines, so the resource is more likely to already be resolved by
     * the time a real stream needs it. The default does nothing.
     *
     * @param config  the avro model configuration
     */
    default void attach(
        AvroModelConfig config)
    {
    }

    /**
     * Supplies the handler for one resolved schema, bound to a specific {@link AvroModelConfig}.
     *
     * @param schema  the resolved schema
     * @param config  the avro model configuration
     * @return the {@link AvroModelExtHandler} for this schema and configuration
     */
    AvroModelExtHandler supplyHandler(
        AvroSchema schema,
        AvroModelConfig config);
}
