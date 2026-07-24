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
package io.aklivity.zilla.config.binding.openapi.asyncapi;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.WithConfig;

public class OpenapiAsyncapiWithConfig extends WithConfig
{
    public final String spec;
    public final String operation;
    public final String tag;

    public static OpenapiAsyncapiWithConfigBuilder<OpenapiAsyncapiWithConfig> builder()
    {
        return new OpenapiAsyncapiWithConfigBuilder<>(OpenapiAsyncapiWithConfig.class::cast);
    }

    public static <T> OpenapiAsyncapiWithConfigBuilder<T> builder(
        Function<WithConfig, T> mapper)
    {
        return new OpenapiAsyncapiWithConfigBuilder<>(mapper);
    }

    OpenapiAsyncapiWithConfig(
        String spec,
        String operation,
        String tag)
    {
        this.spec = spec;
        this.operation = operation;
        this.tag = tag;
    }
}
