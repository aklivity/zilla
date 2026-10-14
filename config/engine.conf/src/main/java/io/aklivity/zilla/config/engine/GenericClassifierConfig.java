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
package io.aklivity.zilla.config.engine;

import java.util.function.Function;

public final class GenericClassifierConfig extends ClassifierConfig
{
    public static GenericClassifierConfigBuilder<GenericClassifierConfig> builder()
    {
        return new GenericClassifierConfigBuilder<>(GenericClassifierConfig.class::cast);
    }

    public static <T> GenericClassifierConfigBuilder<T> builder(
        Function<ClassifierConfig, T> mapper)
    {
        return new GenericClassifierConfigBuilder<>(mapper);
    }

    GenericClassifierConfig(
        String namespace,
        String name,
        String type,
        String embedding,
        String store,
        OptionsConfig options)
    {
        super(namespace, name, type, embedding, store, options);
    }
}
