/*
 * Copyright 2021-2022 Aklivity Inc.
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
module io.aklivity.zilla.runtime.command.metrics
{
    requires io.aklivity.zilla.runtime.command;

    requires transitive org.agrona.core;

    opens io.aklivity.zilla.runtime.command.metrics.internal.airline
       to com.github.rvesse.airline;

    provides io.aklivity.zilla.runtime.command.ZillaCommandSpi
        with io.aklivity.zilla.runtime.command.metrics.internal.ZillaMetricsCommandSpi;
}
