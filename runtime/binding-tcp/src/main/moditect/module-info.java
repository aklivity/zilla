/*
 * Copyright 2021-2024 Aklivity Inc.
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
module io.aklivity.zilla.runtime.binding.tcp
{
    requires io.aklivity.zilla.runtime.engine;

    exports io.aklivity.zilla.runtime.binding.tcp.config;

    provides io.aklivity.zilla.runtime.engine.binding.BindingFactorySpi
        with io.aklivity.zilla.runtime.binding.tcp.internal.TcpBindingFactorySpi;

    provides io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi
        with io.aklivity.zilla.runtime.binding.tcp.internal.config.TcpOptionsConfigAdapter;

    provides io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi
        with io.aklivity.zilla.runtime.binding.tcp.internal.config.TcpConditionConfigAdapter;

    provides io.aklivity.zilla.runtime.engine.event.EventFormatterFactorySpi
        with io.aklivity.zilla.runtime.binding.tcp.internal.TcpEventFormatterFactory;
}
