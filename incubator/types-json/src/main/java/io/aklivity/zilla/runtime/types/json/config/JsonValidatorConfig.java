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
package io.aklivity.zilla.runtime.types.json.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;

public final class JsonValidatorConfig extends ValidatorConfig
{
    public final String subject;

    JsonValidatorConfig(
        List<CatalogedConfig> cataloged,
        String subject)
    {
        super("json", cataloged);
        this.subject = subject;
    }

    public static <T> JsonValidatorConfigBuilder<T> builder(
        Function<ValidatorConfig, T> mapper)
    {
        return new JsonValidatorConfigBuilder<>(mapper::apply);
    }

    public static JsonValidatorConfigBuilder<JsonValidatorConfig> builder()
    {
        return new JsonValidatorConfigBuilder<>(JsonValidatorConfig.class::cast);
    }
}
