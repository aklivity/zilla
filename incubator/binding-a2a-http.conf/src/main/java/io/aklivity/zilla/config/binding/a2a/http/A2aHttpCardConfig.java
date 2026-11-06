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
package io.aklivity.zilla.config.binding.a2a.http;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;

public final class A2aHttpCardConfig extends Config
{
    public final String name;
    public final String description;
    public final String version;
    public final boolean streaming;
    public final List<String> defaultInputModes;
    public final List<String> defaultOutputModes;

    public static A2aHttpCardConfigBuilder<A2aHttpCardConfig> builder()
    {
        return new A2aHttpCardConfigBuilder<>(A2aHttpCardConfig.class::cast);
    }

    public static <T> A2aHttpCardConfigBuilder<T> builder(
        Function<A2aHttpCardConfig, T> mapper)
    {
        return new A2aHttpCardConfigBuilder<>(mapper);
    }

    A2aHttpCardConfig(
        String name,
        String description,
        String version,
        boolean streaming,
        List<String> defaultInputModes,
        List<String> defaultOutputModes)
    {
        this.name = name;
        this.description = description;
        this.version = version;
        this.streaming = streaming;
        this.defaultInputModes = defaultInputModes;
        this.defaultOutputModes = defaultOutputModes;
    }
}
