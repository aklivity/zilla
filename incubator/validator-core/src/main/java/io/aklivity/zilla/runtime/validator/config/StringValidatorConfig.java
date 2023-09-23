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
package io.aklivity.zilla.runtime.validator.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;

public final class StringValidatorConfig extends ValidatorConfig
{
    public static final String DEFAULT_ENCODING = "utf_8";

    public final String encoding;

    public StringValidatorConfig(
        String encoding)
    {
        super("string");
        this.encoding = encoding != null ? encoding : DEFAULT_ENCODING;
    }

    public static <T> StringValidatorConfigBuilder<T> builder(
        Function<ValidatorConfig, T> mapper)
    {
        return new StringValidatorConfigBuilder<>(mapper::apply);
    }

    public static StringValidatorConfigBuilder<StringValidatorConfig> builder()
    {
        return new StringValidatorConfigBuilder<>(StringValidatorConfig.class::cast);
    }
}
