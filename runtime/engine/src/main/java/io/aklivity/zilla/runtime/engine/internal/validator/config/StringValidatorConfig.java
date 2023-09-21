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

public final class StringValidatorConfig extends ValidatorConfig
{
    public final String encoding;

    public StringValidatorConfig(
        String encoding)
    {
        super("string");
        this.encoding = encoding != null ? encoding : "utf_8";
    }

    public static <T> StringValidatorConfigBuilder<T> builder(
        Function<ValidatorConfig, T> mapper)
    {
        return new StringValidatorConfigBuilder<>(mapper);
    }

    public static StringValidatorConfigBuilder<StringValidatorConfig> builder()
    {
        return new StringValidatorConfigBuilder<>(StringValidatorConfig.class::cast);
    }
}
