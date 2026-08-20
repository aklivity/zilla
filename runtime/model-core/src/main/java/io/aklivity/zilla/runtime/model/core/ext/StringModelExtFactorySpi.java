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

import io.aklivity.zilla.runtime.engine.Configuration;

/**
 * Service provider interface for an installed module that contributes its own {@link StringModelExt} to
 * the string model, participating in pipeline construction alongside model-core's own stages.
 * <p>
 * Implementations must be registered in
 * {@code META-INF/services/io.aklivity.zilla.runtime.model.core.ext.StringModelExtFactorySpi}. Any number
 * of implementations may be installed at once; every one discovered contributes independently -- unlike a
 * top-level {@code FactorySpi} (e.g. {@code ModelFactorySpi}), there is no {@code type()} to select among
 * installed implementations, so this interface does not extend {@code FactorySpi}.
 * </p>
 *
 * @see StringModelExt
 */
public interface StringModelExtFactorySpi
{
    /**
     * Creates a new {@link StringModelExt} instance for the given engine configuration.
     *
     * @param config  the engine configuration
     * @return a new {@link StringModelExt}
     */
    StringModelExt create(
        Configuration config);
}
