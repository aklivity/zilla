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
package io.aklivity.zilla.config.binding.a2a.openapi;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;

public final class A2aOpenapiAgentConfig extends Config
{
    public final String name;
    public final String description;
    public final String version;

    public static A2aOpenapiAgentConfigBuilder<A2aOpenapiAgentConfig> builder()
    {
        return new A2aOpenapiAgentConfigBuilder<>(A2aOpenapiAgentConfig.class::cast);
    }

    public static <T> A2aOpenapiAgentConfigBuilder<T> builder(
        Function<A2aOpenapiAgentConfig, T> mapper)
    {
        return new A2aOpenapiAgentConfigBuilder<>(mapper);
    }

    A2aOpenapiAgentConfig(
        String name,
        String description,
        String version)
    {
        this.name = name;
        this.description = description;
        this.version = version;
    }
}
