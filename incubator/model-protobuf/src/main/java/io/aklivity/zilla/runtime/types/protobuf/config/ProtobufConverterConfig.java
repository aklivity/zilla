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
package io.aklivity.zilla.runtime.types.protobuf.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ConverterConfig;

public final class ProtobufConverterConfig extends ConverterConfig
{
    public final String subject;
    public final String format;

    public ProtobufConverterConfig(
        List<CatalogedConfig> cataloged,
        String subject,
        String format)
    {
        super("protobuf", cataloged);
        this.subject = subject;
        this.format = format;
    }

    public static <T> ProtobufConverterConfigBuilder<T> builder(
        Function<ConverterConfig, T> mapper)
    {
        return new ProtobufConverterConfigBuilder<>(mapper::apply);
    }

    public static ProtobufConverterConfigBuilder<ProtobufConverterConfig> builder()
    {
        return new ProtobufConverterConfigBuilder<>(ProtobufConverterConfig.class::cast);
    }
}
