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
package io.aklivity.zilla.runtime.classifier.patterns.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.classifier.patterns.PatternsClassifierOptionsConfig;
import io.aklivity.zilla.config.engine.ClassifierConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.ClassifierHandler;
import io.aklivity.zilla.runtime.engine.classifier.Detector;

final class PatternsClassifierHandler implements ClassifierHandler
{
    private final EngineContext context;
    private final PatternsClassifierConfiguration configuration;
    private final double entropy;
    private final Map<String, List<Pattern>> patternsByLabel;

    PatternsClassifierHandler(
        EngineContext context,
        ClassifierConfig config,
        PatternsClassifierConfiguration configuration)
    {
        PatternsClassifierOptionsConfig options = (PatternsClassifierOptionsConfig) config.options;

        this.context = context;
        this.configuration = configuration;
        this.entropy = options.entropy;
        this.patternsByLabel = options.labels;
    }

    @Override
    public Detector initDetector(
        List<String> labels)
    {
        List<String> aliases = new ArrayList<>();
        List<Pattern> patterns = new ArrayList<>();

        for (String label : labels)
        {
            List<Pattern> labelPatterns = patternsByLabel.get(label);

            if (labelPatterns == null)
            {
                throw new IllegalArgumentException("Unrecognized label: " + label);
            }

            for (Pattern pattern : labelPatterns)
            {
                aliases.add(label);
                patterns.add(pattern);
            }
        }

        return new PatternsDetector(
            context,
            new PatternsMatcher(entropy, aliases, patterns),
            configuration.inputMaxLength(),
            configuration.matchMaxSteps());
    }
}
