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
package io.aklivity.zilla.config.classifier.presidio;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class PresidioClassifierOptionsConfigBuilder<T> extends ConfigBuilder<T, PresidioClassifierOptionsConfigBuilder<T>>
{
    public static final String LANGUAGE_DEFAULT = "en";
    public static final Duration TIMEOUT_DEFAULT = Duration.ofSeconds(5);

    private final Function<OptionsConfig, T> mapper;
    private final Map<String, String> labels;

    private String endpoint;
    private String authorization;
    private String language;
    private double threshold;
    private Duration timeout;

    PresidioClassifierOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
        this.labels = new LinkedHashMap<>();
        this.language = LANGUAGE_DEFAULT;
        this.timeout = TIMEOUT_DEFAULT;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<PresidioClassifierOptionsConfigBuilder<T>> thisType()
    {
        return (Class<PresidioClassifierOptionsConfigBuilder<T>>) getClass();
    }

    public PresidioClassifierOptionsConfigBuilder<T> endpoint(
        String endpoint)
    {
        this.endpoint = endpoint;
        return this;
    }

    public PresidioClassifierOptionsConfigBuilder<T> authorization(
        String authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public PresidioClassifierOptionsConfigBuilder<T> language(
        String language)
    {
        this.language = language;
        return this;
    }

    public PresidioClassifierOptionsConfigBuilder<T> threshold(
        double threshold)
    {
        this.threshold = threshold;
        return this;
    }

    public PresidioClassifierOptionsConfigBuilder<T> timeout(
        Duration timeout)
    {
        this.timeout = timeout;
        return this;
    }

    public PresidioClassifierOptionsConfigBuilder<T> label(
        String alias,
        String entity)
    {
        labels.put(alias, entity);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new PresidioClassifierOptionsConfig(endpoint, authorization, language, threshold, timeout, labels));
    }
}
