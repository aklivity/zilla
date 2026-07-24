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
package io.aklivity.zilla.config.binding.openapi;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;

public final class OpenapiOptionsConfig extends OptionsConfig
{
    public final List<OpenapiSpecificationConfig> specs;

    public static OpenapiOptionsConfigBuilder<OpenapiOptionsConfig> builder()
    {
        return new OpenapiOptionsConfigBuilder<>(OpenapiOptionsConfig.class::cast);
    }

    public static <T> OpenapiOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new OpenapiOptionsConfigBuilder<>(mapper);
    }

    OpenapiOptionsConfig(
        List<OpenapiSpecificationConfig> specs)
    {
        this.specs = specs;
    }
}
