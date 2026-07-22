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
module io.aklivity.zilla.config.model.core
{
    requires jakarta.json;
    requires jakarta.json.bind;
    requires org.agrona;
    requires io.aklivity.zilla.config.engine;

    exports io.aklivity.zilla.config.model.core;

    provides io.aklivity.zilla.config.engine.ModelConfigAdapterSpi
        with io.aklivity.zilla.config.model.core.internal.BooleanModelConfigAdapter,
            io.aklivity.zilla.config.model.core.internal.DoubleModelConfigAdapter,
            io.aklivity.zilla.config.model.core.internal.FloatModelConfigAdapter,
            io.aklivity.zilla.config.model.core.internal.Int32ModelConfigAdapter,
            io.aklivity.zilla.config.model.core.internal.Int64ModelConfigAdapter,
            io.aklivity.zilla.config.model.core.internal.StringModelConfigAdapter;

    provides io.aklivity.zilla.config.engine.ModelInfo
        with io.aklivity.zilla.config.model.core.BooleanModelInfo,
            io.aklivity.zilla.config.model.core.DoubleModelInfo,
            io.aklivity.zilla.config.model.core.FloatModelInfo,
            io.aklivity.zilla.config.model.core.Int32ModelInfo,
            io.aklivity.zilla.config.model.core.Int64ModelInfo,
            io.aklivity.zilla.config.model.core.StringModelInfo;
}
