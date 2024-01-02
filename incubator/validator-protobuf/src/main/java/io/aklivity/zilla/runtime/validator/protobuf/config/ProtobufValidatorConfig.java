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
package io.aklivity.zilla.runtime.validator.protobuf.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;

public final class ProtobufValidatorConfig extends ValidatorConfig
{
    public final String subject;

    ProtobufValidatorConfig(
        List<CatalogedConfig> cataloged,
        String subject)
    {
        super("protobuf", cataloged);
        this.subject = subject;
    }

    public static <T> ProtobufValidatorConfigBuilder<T> builder(
        Function<ValidatorConfig, T> mapper)
    {
        return new ProtobufValidatorConfigBuilder<>(mapper::apply);
    }

    public static ProtobufValidatorConfigBuilder<ProtobufValidatorConfig> builder()
    {
        return new ProtobufValidatorConfigBuilder<>(ProtobufValidatorConfig.class::cast);
    }
}
