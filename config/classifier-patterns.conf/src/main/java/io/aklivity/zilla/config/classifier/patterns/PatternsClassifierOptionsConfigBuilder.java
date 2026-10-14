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
package io.aklivity.zilla.config.classifier.patterns;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class PatternsClassifierOptionsConfigBuilder<T> extends ConfigBuilder<T, PatternsClassifierOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;
    private final Map<String, List<Pattern>> labels;

    private double entropy;

    PatternsClassifierOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
        this.labels = new LinkedHashMap<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<PatternsClassifierOptionsConfigBuilder<T>> thisType()
    {
        return (Class<PatternsClassifierOptionsConfigBuilder<T>>) getClass();
    }

    public PatternsClassifierOptionsConfigBuilder<T> entropy(
        double entropy)
    {
        this.entropy = entropy;
        return this;
    }

    public PatternsClassifierOptionsConfigBuilder<T> label(
        String alias,
        List<String> expressions)
    {
        labels.put(alias, expressions.stream().map(expression -> compile(alias, expression)).toList());
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new PatternsClassifierOptionsConfig(entropy, labels));
    }

    private static Pattern compile(
        String alias,
        String expression)
    {
        try
        {
            return Pattern.compile(expression);
        }
        catch (PatternSyntaxException ex)
        {
            throw new IllegalArgumentException("Invalid pattern for label \"" + alias + "\": " + ex.getMessage(), ex);
        }
    }
}
