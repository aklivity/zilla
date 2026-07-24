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
package io.aklivity.zilla.config.binding.tcp;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;

public final class TcpConditionConfig extends ConditionConfig
{
    public final String cidr;
    public final String authority;
    public final int[] ports;

    public static TcpConditionConfigBuilder<TcpConditionConfig> builder()
    {
        return new TcpConditionConfigBuilder<>(TcpConditionConfig.class::cast);
    }

    public static <T> TcpConditionConfigBuilder<T> builder(
        Function<ConditionConfig, T> mapper)
    {
        return new TcpConditionConfigBuilder<>(mapper);
    }

    TcpConditionConfig(
        String cidr,
        String authority,
        int[] ports)
    {
        this.cidr = cidr;
        this.authority = authority;
        this.ports = ports;
    }
}
