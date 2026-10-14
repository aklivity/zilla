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
package io.aklivity.zilla.runtime.model.classify.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public final class ClassifyModelConfiguration extends Configuration
{
    private static final ConfigurationDef CLASSIFY_MODEL_CONFIG;
    private static final int DEFAULT_MAX_LENGTH = 1024 * 1024;

    static final IntPropertyDef MAX_LENGTH;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.model.classify");

        MAX_LENGTH = config.property("max.length", DEFAULT_MAX_LENGTH);

        CLASSIFY_MODEL_CONFIG = config;
    }

    public ClassifyModelConfiguration(
        Configuration config)
    {
        super(CLASSIFY_MODEL_CONFIG, config);
    }

    public int maxLength()
    {
        return MAX_LENGTH.getAsInt(this);
    }
}
