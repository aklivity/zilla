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

import io.aklivity.zilla.runtime.engine.Configuration;

public final class PatternsClassifierConfiguration extends Configuration
{
    private static final ConfigurationDef PATTERNS_CLASSIFIER_CONFIG;

    static final IntPropertyDef INPUT_MAX_LENGTH;
    static final LongPropertyDef MATCH_MAX_STEPS;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.classifier.patterns");

        INPUT_MAX_LENGTH = config.property("input.max.length", 65536);
        MATCH_MAX_STEPS = config.property("match.max.steps", 2097152L);

        PATTERNS_CLASSIFIER_CONFIG = config;
    }

    public PatternsClassifierConfiguration(
        Configuration config)
    {
        super(PATTERNS_CLASSIFIER_CONFIG, config);
    }

    public int inputMaxLength()
    {
        return INPUT_MAX_LENGTH.getAsInt(this);
    }

    public long matchMaxSteps()
    {
        return MATCH_MAX_STEPS.getAsLong(this);
    }
}
