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
package io.aklivity.zilla.runtime.model.protobuf.ext;

import io.aklivity.zilla.config.model.protobuf.ProtobufModelConfig;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;

/**
 * The per-worker context for one {@link ProtobufModelExt}, closed over the
 * {@link io.aklivity.zilla.runtime.engine.EngineContext} it was supplied with.
 */
public interface ProtobufModelExtContext
{
    /**
     * Called once when a binding attaches this configuration, before any schema is resolved or any
     * stream begins. An extension whose behavior depends on a resource that resolves asynchronously
     * (e.g. a vault-wrapped key) can use this to kick off that resolution eagerly, from whatever the
     * configuration alone already determines, so the resource is more likely to already be resolved by
     * the time a real stream needs it. The default does nothing.
     *
     * @param config  the protobuf model configuration
     */
    default void attach(
        ProtobufModelConfig config)
    {
    }

    /**
     * Supplies the handler for one resolved schema, bound to a specific {@link ProtobufModelConfig}.
     *
     * @param schema  the resolved schema
     * @param config  the protobuf model configuration
     * @return the {@link ProtobufModelExtHandler} for this schema and configuration
     */
    ProtobufModelExtHandler supplyHandler(
        ProtobufSchema schema,
        ProtobufModelConfig config);
}
