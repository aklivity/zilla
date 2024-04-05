/*
 * Copyright 2021-2023 Aklivity Inc
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
module io.aklivity.zilla.runtime.model.core
{
    requires io.aklivity.zilla.runtime.engine;

    exports io.aklivity.zilla.runtime.model.core.config;

    provides io.aklivity.zilla.runtime.engine.config.ModelConfigAdapterSpi
        with io.aklivity.zilla.runtime.model.core.internal.config.DoubleModelConfigAdapter,
            io.aklivity.zilla.runtime.model.core.internal.config.FloatModelConfigAdapter,
            io.aklivity.zilla.runtime.model.core.internal.config.Int32ModelConfigAdapter,
            io.aklivity.zilla.runtime.model.core.internal.config.Int64ModelConfigAdapter,
            io.aklivity.zilla.runtime.model.core.internal.config.StringModelConfigAdapter;

    provides io.aklivity.zilla.runtime.engine.model.ModelFactorySpi
        with io.aklivity.zilla.runtime.model.core.internal.DoubleModelFactorySpi,
            io.aklivity.zilla.runtime.model.core.internal.FloatModelFactorySpi,
            io.aklivity.zilla.runtime.model.core.internal.Int32ModelFactorySpi,
            io.aklivity.zilla.runtime.model.core.internal.Int64ModelFactorySpi,
            io.aklivity.zilla.runtime.model.core.internal.StringModelFactorySpi;

    provides io.aklivity.zilla.runtime.engine.event.EventFormatterFactorySpi
        with io.aklivity.zilla.runtime.model.core.internal.CoreModelEventFormatterFactory;
}
