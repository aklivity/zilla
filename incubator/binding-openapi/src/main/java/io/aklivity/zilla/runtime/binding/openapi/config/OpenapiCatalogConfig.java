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
package io.aklivity.zilla.runtime.binding.openapi.config;

import java.util.function.Function;

public class OpenapiCatalogConfig
{
    public final String name;
    public final String subject;
    public final String version;

    public OpenapiCatalogConfig(
        String name,
        String subject,
        String version)
    {
        this.name = name;
        this.subject = subject;
        this.version = version;
    }

    public static OpenpaiCatalogConfigBuilder<OpenapiCatalogConfig> builder()
    {
        return new OpenpaiCatalogConfigBuilder<>(OpenapiCatalogConfig.class::cast);
    }

    public static <T> OpenpaiCatalogConfigBuilder<T> builder(
        Function<OpenapiCatalogConfig, T> mapper)
    {
        return new OpenpaiCatalogConfigBuilder<>(mapper);
    }
}
