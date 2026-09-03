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
package io.aklivity.zilla.config.model.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.EmbeddedConfig;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.StoredConfig;

public final class VectorModelConfig extends ModelConfig
{
    public final EmbeddedConfig embedding;
    public final List<String> reject;
    public final double threshold;
    public final StoredConfig store;

    VectorModelConfig(
        EmbeddedConfig embedding,
        List<String> reject,
        double threshold,
        StoredConfig store,
        Map<String, Config> extensions,
        List<NamedConfig> refs)
    {
        super("vector", null, null, extensions, withRefs(embedding, store, refs));
        this.embedding = embedding;
        this.reject = reject;
        this.threshold = threshold;
        this.store = store;
    }

    public static <T> VectorModelConfigBuilder<T> builder(
        Function<ModelConfig, T> mapper)
    {
        return new VectorModelConfigBuilder<>(mapper::apply);
    }

    public static VectorModelConfigBuilder<VectorModelConfig> builder()
    {
        return new VectorModelConfigBuilder<>(VectorModelConfig.class::cast);
    }

    private static List<NamedConfig> withRefs(
        EmbeddedConfig embedding,
        StoredConfig store,
        List<NamedConfig> refs)
    {
        List<NamedConfig> all = new ArrayList<>();
        if (embedding != null)
        {
            all.add(embedding);
        }
        if (store != null)
        {
            all.add(store);
        }
        if (refs != null)
        {
            all.addAll(refs);
        }
        return all;
    }
}
