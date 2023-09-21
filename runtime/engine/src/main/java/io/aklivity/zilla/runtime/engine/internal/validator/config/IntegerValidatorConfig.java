/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.validator.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;

public class IntegerValidatorConfig extends ValidatorConfig
{
    public IntegerValidatorConfig()
    {
        super("integer");
    }

    public static <T> IntegerValidatorConfigBuilder<T> builder(
        Function<ValidatorConfig, T> mapper)
    {
        return new IntegerValidatorConfigBuilder<>(mapper::apply);
    }

    public static IntegerValidatorConfigBuilder<IntegerValidatorConfig> builder()
    {
        return new IntegerValidatorConfigBuilder<>(IntegerValidatorConfig.class::cast);
    }
}
