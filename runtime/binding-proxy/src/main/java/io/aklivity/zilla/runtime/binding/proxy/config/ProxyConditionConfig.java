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
package io.aklivity.zilla.runtime.binding.proxy.config;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;

public final class ProxyConditionConfig extends ConditionConfig
{
    public final String transport;
    public final String family;
    public final ProxyAddressConfig source;
    public final ProxyAddressConfig destination;
    public final ProxyInfoConfig info;

    public ProxyConditionConfig(
        String transport,
        String family,
        ProxyAddressConfig source,
        ProxyAddressConfig destination,
        ProxyInfoConfig info)
    {
        this.transport = transport;
        this.family = family;
        this.source = source;
        this.destination = destination;
        this.info = info;
    }
}
