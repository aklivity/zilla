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
package io.aklivity.zilla.config.model.avro;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ValidateConfig;
import io.aklivity.zilla.config.engine.VaultedConfig;

public final class AvroModelConfig extends ModelConfig
{
    public final String subject;
    public final String view;

    AvroModelConfig(
        List<CatalogedConfig> cataloged,
        String subject,
        String view,
        ValidateConfig validate,
        List<VaultedConfig> vaulted,
        Map<String, Config> extensions)
    {
        super("avro", cataloged, validate, vaulted, extensions);
        this.subject = subject;
        this.view = view;
    }

    public static <T> AvroModelConfigBuilder<T> builder(
        Function<ModelConfig, T> mapper)
    {
        return new AvroModelConfigBuilder<>(mapper::apply);
    }

    public static AvroModelConfigBuilder<AvroModelConfig> builder()
    {
        return new AvroModelConfigBuilder<>(AvroModelConfig.class::cast);
    }
}
