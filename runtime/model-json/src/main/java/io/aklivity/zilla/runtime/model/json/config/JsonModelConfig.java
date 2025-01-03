/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.json.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public final class JsonModelConfig extends ModelConfig
{
    public final String subject;

    public JsonModelConfig(
        List<CatalogedConfig> cataloged,
        String subject)
    {
        super("json", cataloged);
        this.subject = subject;
    }

    public static <T> JsonModelConfigBuilder<T> builder(
        Function<ModelConfig, T> mapper)
    {
        return new JsonModelConfigBuilder<>(mapper::apply);
    }

    public static JsonModelConfigBuilder<JsonModelConfig> builder()
    {
        return new JsonModelConfigBuilder<>(JsonModelConfig.class::cast);
    }
}
