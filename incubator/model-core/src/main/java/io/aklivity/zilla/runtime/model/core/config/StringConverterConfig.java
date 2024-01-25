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
package io.aklivity.zilla.runtime.model.core.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConverterConfig;

public final class StringConverterConfig extends ConverterConfig
{
    public static final String DEFAULT_ENCODING = "utf_8";

    public final String encoding;

    public StringConverterConfig(
        String encoding)
    {
        super("string");
        this.encoding = encoding != null ? encoding : DEFAULT_ENCODING;
    }

    public static <T> StringConverterConfigBuilder<T> builder(
        Function<ConverterConfig, T> mapper)
    {
        return new StringConverterConfigBuilder<>(mapper::apply);
    }

    public static StringConverterConfigBuilder<StringConverterConfig> builder()
    {
        return new StringConverterConfigBuilder<>(StringConverterConfig.class::cast);
    }
}
