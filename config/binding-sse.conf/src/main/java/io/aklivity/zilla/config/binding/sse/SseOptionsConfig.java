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
package io.aklivity.zilla.config.binding.sse;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class SseOptionsConfig extends OptionsConfig
{
    public static final int RETRY_DEFAULT = 2000;

    public final int retry;
    public final List<SseRequestConfig> requests;


    public static SseOptionsConfigBuilder<SseOptionsConfig> builder()
    {
        return new SseOptionsConfigBuilder<>(SseOptionsConfig.class::cast);
    }

    public static <T> SseOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new SseOptionsConfigBuilder<>(mapper);
    }

    SseOptionsConfig(
        int retry,
        List<SseRequestConfig> requests,
        List<NamedConfig> refs)
    {
        super(null, refs);
        this.retry = retry;
        this.requests = requests;
    }
}
