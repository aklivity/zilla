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

import io.aklivity.zilla.runtime.common.avro.AvroTransformable;

/**
 * Appends whatever stages this extension contributes to an in-progress avro pipeline, for one resolved
 * schema and configuration.
 */
public interface AvroModelExtHandler
{
    /**
     * Appends this extension's own stage or stages to {@code stream}, in data-flow order, returning the
     * result for the caller to continue building.
     *
     * @param stream  the in-progress stream to extend
     * @return the extended stream
     */
    AvroTransformable transform(
        AvroTransformable stream);
}
