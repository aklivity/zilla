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
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class PresidioClassifierOptionsConfig extends OptionsConfig
{
    public final String endpoint;
    public final String authorization;
    public final String language;
    public final double threshold;
    public final Duration timeout;
    public final Map<String, String> labels;

    public static PresidioClassifierOptionsConfigBuilder<PresidioClassifierOptionsConfig> builder()
    {
        return new PresidioClassifierOptionsConfigBuilder<>(PresidioClassifierOptionsConfig.class::cast);
    }

    public static <T> PresidioClassifierOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new PresidioClassifierOptionsConfigBuilder<>(mapper);
    }

    PresidioClassifierOptionsConfig(
        String endpoint,
        String authorization,
        String language,
        double threshold,
        Duration timeout,
        Map<String, String> labels)
    {
        super(null, null);
        this.endpoint = endpoint;
        this.authorization = authorization;
        this.language = language;
        this.threshold = threshold;
        this.timeout = timeout;
        this.labels = labels;
    }
}
