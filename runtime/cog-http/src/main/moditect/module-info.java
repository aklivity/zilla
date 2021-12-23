/*
 * Copyright 2021-2021 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
module io.aklivity.zilla.runtime.cog.http
{
    requires io.aklivity.zilla.runtime.engine;

    provides io.aklivity.zilla.runtime.engine.cog.CogFactorySpi
        with io.aklivity.zilla.runtime.cog.http.internal.HttpCogFactorySpi,
             io.aklivity.zilla.runtime.cog.http.internal.Http2CogFactorySpi;

    provides io.aklivity.zilla.runtime.engine.config.OptionsAdapterSpi
        with io.aklivity.zilla.runtime.cog.http.internal.config.HttpOptionsAdapter,
             io.aklivity.zilla.runtime.cog.http.internal.config.Http2OptionsAdapter;

    provides io.aklivity.zilla.runtime.engine.config.ConditionAdapterSpi
        with io.aklivity.zilla.runtime.cog.http.internal.config.HttpConditionAdapter,
             io.aklivity.zilla.runtime.cog.http.internal.config.Http2ConditionAdapter;
}
