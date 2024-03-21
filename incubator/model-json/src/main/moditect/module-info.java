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
module io.aklivity.zilla.runtime.model.json
{
    requires io.aklivity.zilla.runtime.engine;

    requires org.leadpony.justify;

    exports io.aklivity.zilla.runtime.model.json.config;

    provides io.aklivity.zilla.runtime.engine.config.ModelConfigAdapterSpi
        with io.aklivity.zilla.runtime.model.json.internal.config.JsonModelConfigAdapter;

    provides io.aklivity.zilla.runtime.engine.model.ModelFactorySpi
        with io.aklivity.zilla.runtime.model.json.internal.JsonModelFactorySpi;

    provides io.aklivity.zilla.runtime.engine.event.EventFormatterFactorySpi
        with io.aklivity.zilla.runtime.model.json.internal.JsonModelEventFormatterFactory;
}
