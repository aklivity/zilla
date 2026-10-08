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
package io.aklivity.zilla.config.classifier.semantic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class SemanticClassifierOptionsConfigBuilder<T> extends ConfigBuilder<T, SemanticClassifierOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;
    private final Map<String, List<String>> labels;

    private double threshold;

    SemanticClassifierOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
        this.labels = new LinkedHashMap<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<SemanticClassifierOptionsConfigBuilder<T>> thisType()
    {
        return (Class<SemanticClassifierOptionsConfigBuilder<T>>) getClass();
    }

    public SemanticClassifierOptionsConfigBuilder<T> threshold(
        double threshold)
    {
        this.threshold = threshold;
        return this;
    }

    public SemanticClassifierOptionsConfigBuilder<T> label(
        String alias,
        List<String> phrases)
    {
        labels.put(alias, phrases);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new SemanticClassifierOptionsConfig(threshold, labels));
    }
}
