/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.config;

import java.util.function.Function;

public class AsyncapiChannelsConfig
{
    public final String sessions;
    public final String messages;
    public final String retained;

    public static AsyncapiChannelsConfigBuilder<AsyncapiChannelsConfig> builder()
    {
        return new AsyncapiChannelsConfigBuilder<>(AsyncapiChannelsConfig.class::cast);
    }

    public static <T> AsyncapiChannelsConfigBuilder<T> builder(
        Function<AsyncapiChannelsConfig, T> mapper)
    {
        return new AsyncapiChannelsConfigBuilder<>(mapper);
    }

    public AsyncapiChannelsConfig(
        String sessions,
        String messages,
        String retained)
    {
        this.sessions = sessions;
        this.messages = messages;
        this.retained = retained;
    }
}

