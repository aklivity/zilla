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
package io.aklivity.zilla.runtime.binding.llm.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class LlmConfiguration extends Configuration
{
    public static final IntPropertyDef LLM_SIGNED_REQUEST_MAX_BYTES;

    private static final ConfigurationDef LLM_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef(String.format("zilla.binding.%s", LlmBinding.NAME));
        LLM_SIGNED_REQUEST_MAX_BYTES = config.property("signed.request.max.bytes", 10 * 1024 * 1024);
        LLM_CONFIG = config;
    }

    public LlmConfiguration(
        Configuration config)
    {
        super(LLM_CONFIG, config);
    }

    public int signedRequestMaxBytes()
    {
        return LLM_SIGNED_REQUEST_MAX_BYTES.get(this);
    }
}
