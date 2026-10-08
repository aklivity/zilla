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
package io.aklivity.zilla.runtime.embedding.openai.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class OpenAiConfiguration extends Configuration
{
    public static final PropertyDef<String> OPENAI_ENDPOINT;

    private static final ConfigurationDef OPENAI_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.embedding.openai");

        OPENAI_ENDPOINT = config.property("endpoint", "https://api.openai.com/v1/embeddings");

        OPENAI_CONFIG = config;
    }

    public OpenAiConfiguration()
    {
        super(OPENAI_CONFIG, new Configuration());
    }

    public OpenAiConfiguration(
        Configuration config)
    {
        super(OPENAI_CONFIG, config);
    }

    public String endpoint()
    {
        return OPENAI_ENDPOINT.get(this);
    }
}
