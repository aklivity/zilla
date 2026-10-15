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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public class ClassifyModelConfigBuilder<T> extends ConfigBuilder.Extensible<T, ClassifyModelConfigBuilder<T>>
{
    private final Function<ClassifyModelConfig, T> mapper;
    private final List<ClassifyRejectConfig> reject;

    ClassifyModelConfigBuilder(
        Function<ClassifyModelConfig, T> mapper)
    {
        this.mapper = mapper;
        this.reject = new LinkedList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<ClassifyModelConfigBuilder<T>> thisType()
    {
        return (Class<ClassifyModelConfigBuilder<T>>) getClass();
    }

    public ClassifyModelConfigBuilder<T> reject(
        String classifier,
        List<String> labels)
    {
        reject.add(new ClassifyRejectConfig(classifier, labels));
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new ClassifyModelConfig(reject, extensions(), refs()));
    }
}
