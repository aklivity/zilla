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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class LlmConfiguration extends Configuration
{
    public static final IntPropertyDef LLM_SIGNED_REQUEST_MAX_BYTES;
    public static final IntPropertyDef LLM_ANTHROPIC_MAX_TOKENS;
    public static final PropertyDef<int[]> LLM_FALLBACK_STATUS;
    public static final IntPropertyDef LLM_FALLBACK_REQUEST_MAX_BYTES;
    public static final IntPropertyDef LLM_FALLBACK_REQUEST_MEMORY_MAX_BYTES;
    public static final LongPropertyDef LLM_FALLBACK_REQUEST_MEMORY_RETENTION_BYTES;
    public static final PropertyDef<Path> LLM_FALLBACK_REQUEST_DIRECTORY;
    public static final LongPropertyDef LLM_FALLBACK_REQUEST_RETENTION_BYTES;

    private static final ConfigurationDef LLM_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef(String.format("zilla.binding.%s", LlmBinding.NAME));
        LLM_SIGNED_REQUEST_MAX_BYTES = config.property("signed.request.max.bytes", 10 * 1024 * 1024);
        LLM_ANTHROPIC_MAX_TOKENS = config.property("anthropic.max.tokens", 4096);
        LLM_FALLBACK_STATUS = config.property(int[].class, "fallback.status",
            LlmConfiguration::decodeStatus, new int[] {408, 429, 500, 502, 503, 504, 529});
        LLM_FALLBACK_REQUEST_MAX_BYTES = config.property("fallback.request.max.bytes", 10 * 1024 * 1024);
        LLM_FALLBACK_REQUEST_MEMORY_MAX_BYTES = config.property("fallback.request.memory.max.bytes", 64 * 1024);
        LLM_FALLBACK_REQUEST_MEMORY_RETENTION_BYTES = config.property("fallback.request.memory.retention.bytes",
            128L * 1024 * 1024);
        LLM_FALLBACK_REQUEST_DIRECTORY = config.property(Path.class, "fallback.request.directory",
            (c, v) -> Paths.get(v), LlmConfiguration::defaultFallbackRequestDirectory);
        LLM_FALLBACK_REQUEST_RETENTION_BYTES = config.property("fallback.request.retention.bytes", 1024L * 1024 * 1024);
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

    public int anthropicMaxTokens()
    {
        return LLM_ANTHROPIC_MAX_TOKENS.get(this);
    }

    public int[] fallbackStatus()
    {
        return LLM_FALLBACK_STATUS.get(this);
    }

    public int fallbackRequestMaxBytes()
    {
        return LLM_FALLBACK_REQUEST_MAX_BYTES.get(this);
    }

    public int fallbackRequestMemoryMaxBytes()
    {
        return LLM_FALLBACK_REQUEST_MEMORY_MAX_BYTES.get(this);
    }

    public long fallbackRequestMemoryRetentionBytes()
    {
        return LLM_FALLBACK_REQUEST_MEMORY_RETENTION_BYTES.getAsLong(this);
    }

    public Path fallbackRequestDirectory()
    {
        return LLM_FALLBACK_REQUEST_DIRECTORY.get(this);
    }

    public long fallbackRequestRetentionBytes()
    {
        return LLM_FALLBACK_REQUEST_RETENTION_BYTES.getAsLong(this);
    }

    private static int[] decodeStatus(
        String value)
    {
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(status -> !status.isEmpty())
            .mapToInt(Integer::parseInt)
            .toArray();
    }

    private static Path defaultFallbackRequestDirectory(
        Configuration config)
    {
        return Paths.get(EngineConfiguration.ENGINE_DIRECTORY.get(config)).resolve("llm-fallback");
    }
}
