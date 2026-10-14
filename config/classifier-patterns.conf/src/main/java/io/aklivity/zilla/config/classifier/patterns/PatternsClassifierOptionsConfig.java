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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class PatternsClassifierOptionsConfig extends OptionsConfig
{
    public final double entropy;
    public final Map<String, List<Pattern>> labels;

    public static PatternsClassifierOptionsConfigBuilder<PatternsClassifierOptionsConfig> builder()
    {
        return new PatternsClassifierOptionsConfigBuilder<>(PatternsClassifierOptionsConfig.class::cast);
    }

    public static <T> PatternsClassifierOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new PatternsClassifierOptionsConfigBuilder<>(mapper);
    }

    PatternsClassifierOptionsConfig(
        double entropy,
        Map<String, List<Pattern>> labels)
    {
        super(null, null);
        this.entropy = entropy;
        this.labels = labels;
    }
}
