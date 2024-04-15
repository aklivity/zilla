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
package io.aklivity.zilla.runtime.catalog.filesystem.internal.config;

import java.time.Duration;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class ApicurioOptionsConfig extends OptionsConfig
{
    public final String url;
    public final String groupId;
    public final String useId;
    public final String idEncoding;
    public final Duration maxAge;

    public static ApicurioOptionsConfigBuilder<ApicurioOptionsConfig> builder()
    {
        return new ApicurioOptionsConfigBuilder<>(ApicurioOptionsConfig.class::cast);
    }

    public static <T> ApicurioOptionsConfigBuilder<T> builder(
            Function<OptionsConfig, T> mapper)
    {
        return new ApicurioOptionsConfigBuilder<>(mapper);
    }

    ApicurioOptionsConfig(
        String url,
        String groupId,
        String useId,
        String idEncoding,
        Duration maxAge)
    {
        this.url = url;
        this.groupId = groupId;
        this.useId = useId;
        this.idEncoding = idEncoding;
        this.maxAge = maxAge;
    }
}
