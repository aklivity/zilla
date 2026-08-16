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

/**
 * Appends whatever stages this extension contributes to an in-progress string pipeline, for one
 * configuration.
 */
public interface StringModelExtHandler
{
    /**
     * Appends this extension's own stage or stages to {@code stream}, in data-flow order, returning the
     * result for the caller to continue building.
     *
     * @param stream  the in-progress stream to extend
     * @return the extended stream
     */
    StringTransformable transform(
        StringTransformable stream);

    /**
     * Returns the maximum number of additional bytes this extension's transform may add to a value,
     * beyond what the untransformed value would occupy. A caller sizing a buffer to hold the transformed
     * output adds this to its own estimate.
     *
     * @return the additional byte count (0 if this extension's transform never expands a value)
     */
    default int padding()
    {
        return 0;
    }
}
