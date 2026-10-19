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

import io.aklivity.zilla.runtime.engine.EngineContext;

/**
 * One installed avro model extension, created once per {@link io.aklivity.zilla.runtime.engine.Configuration}
 * by its {@link AvroModelExtFactorySpi}. {@link #supply(EngineContext)} is called once per engine worker,
 * mirroring {@link io.aklivity.zilla.runtime.engine.model.Model#supply(EngineContext)}.
 */
public interface AvroModelExt
{
    /**
     * Supplies the per-worker context for this extension.
     *
     * @param context  the engine context for the current worker
     * @return the per-worker {@link AvroModelExtContext}
     */
    AvroModelExtContext supply(
        EngineContext context);
}
