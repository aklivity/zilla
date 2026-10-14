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
package io.aklivity.zilla.config.model.classify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.ModelConfig;

public final class ClassifyModelConfig extends ModelConfig
{
    public final List<ClassifyRejectConfig> reject;

    ClassifyModelConfig(
        List<ClassifyRejectConfig> reject,
        Map<String, Config> extensions,
        List<Config.Reference> refs)
    {
        super("classify", null, null, extensions, withRefs(reject, refs));
        this.reject = reject;
    }

    public static <T> ClassifyModelConfigBuilder<T> builder(
        Function<ModelConfig, T> mapper)
    {
        return new ClassifyModelConfigBuilder<>(mapper::apply);
    }

    public static ClassifyModelConfigBuilder<ClassifyModelConfig> builder()
    {
        return new ClassifyModelConfigBuilder<>(ClassifyModelConfig.class::cast);
    }

    private static List<Config.Reference> withRefs(
        List<ClassifyRejectConfig> reject,
        List<Config.Reference> refs)
    {
        final List<Config.Reference> all = new ArrayList<>(reject.size() + refs.size());
        all.addAll(reject);
        all.addAll(refs);
        return all;
    }
}
