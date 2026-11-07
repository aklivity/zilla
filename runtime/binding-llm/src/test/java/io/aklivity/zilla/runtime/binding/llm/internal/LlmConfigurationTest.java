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

import static io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration.LLM_FALLBACK_REQUEST_DIRECTORY;
import static io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration.LLM_FALLBACK_REQUEST_MAX_BYTES;
import static io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration.LLM_FALLBACK_REQUEST_MEMORY_MAX_BYTES;
import static io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration.LLM_FALLBACK_REQUEST_MEMORY_RETENTION_BYTES;
import static io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration.LLM_FALLBACK_REQUEST_RETENTION_BYTES;
import static io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration.LLM_FALLBACK_STATUS;
import static io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration.LLM_SIGNED_REQUEST_MAX_BYTES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class LlmConfigurationTest
{
    @Test
    public void shouldCreateConfiguration()
    {
        final LlmConfiguration config = new LlmConfiguration(new Configuration());

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldVerifyConstants()
    {
        assertThat(LLM_SIGNED_REQUEST_MAX_BYTES.name(), equalTo("zilla.binding.llm.signed.request.max.bytes"));
    }

    @Test
    public void shouldDefaultSignedRequestMaxBytes()
    {
        final LlmConfiguration config = new LlmConfiguration(new Configuration());

        assertThat(config.signedRequestMaxBytes(), equalTo(10 * 1024 * 1024));
    }

    @Test
    public void shouldVerifyFallbackConstants()
    {
        assertThat(LLM_FALLBACK_STATUS.name(), equalTo("zilla.binding.llm.fallback.status"));
        assertThat(LLM_FALLBACK_REQUEST_MAX_BYTES.name(), equalTo("zilla.binding.llm.fallback.request.max.bytes"));
        assertThat(LLM_FALLBACK_REQUEST_MEMORY_MAX_BYTES.name(),
            equalTo("zilla.binding.llm.fallback.request.memory.max.bytes"));
        assertThat(LLM_FALLBACK_REQUEST_MEMORY_RETENTION_BYTES.name(),
            equalTo("zilla.binding.llm.fallback.request.memory.retention.bytes"));
        assertThat(LLM_FALLBACK_REQUEST_DIRECTORY.name(), equalTo("zilla.binding.llm.fallback.request.directory"));
        assertThat(LLM_FALLBACK_REQUEST_RETENTION_BYTES.name(),
            equalTo("zilla.binding.llm.fallback.request.retention.bytes"));
    }

    @Test
    public void shouldDefaultFallbackProperties()
    {
        final LlmConfiguration config = new LlmConfiguration(new Configuration());

        assertThat(config.fallbackStatus(), equalTo(new int[] {408, 429, 500, 502, 503, 504, 529}));
        assertThat(config.fallbackRequestMaxBytes(), equalTo(10 * 1024 * 1024));
        assertThat(config.fallbackRequestMemoryMaxBytes(), equalTo(64 * 1024));
        assertThat(config.fallbackRequestMemoryRetentionBytes(), equalTo(128L * 1024 * 1024));
        assertThat(config.fallbackRequestRetentionBytes(), equalTo(1024L * 1024 * 1024));
    }

    @Test
    public void shouldDefaultFallbackRequestDirectoryUnderEngineDirectory()
    {
        final Properties properties = new Properties();
        properties.setProperty("zilla.engine.directory", "/var/zilla");
        final LlmConfiguration config = new LlmConfiguration(new Configuration(properties));

        assertThat(config.fallbackRequestDirectory(), equalTo(Paths.get("/var/zilla").resolve("llm-fallback")));
    }

    @Test
    public void shouldConfigureFallbackProperties()
    {
        final Properties properties = new Properties();
        properties.setProperty("zilla.binding.llm.fallback.status", "503, 529");
        properties.setProperty("zilla.binding.llm.fallback.request.max.bytes", "1024");
        properties.setProperty("zilla.binding.llm.fallback.request.memory.max.bytes", "0");
        properties.setProperty("zilla.binding.llm.fallback.request.memory.retention.bytes", "2048");
        properties.setProperty("zilla.binding.llm.fallback.request.directory", "/tmp/spill");
        properties.setProperty("zilla.binding.llm.fallback.request.retention.bytes", "-1");
        final LlmConfiguration config = new LlmConfiguration(new Configuration(properties));

        final Path directory = Paths.get("/tmp/spill");
        assertThat(config.fallbackStatus(), equalTo(new int[] {503, 529}));
        assertThat(config.fallbackRequestMaxBytes(), equalTo(1024));
        assertThat(config.fallbackRequestMemoryMaxBytes(), equalTo(0));
        assertThat(config.fallbackRequestMemoryRetentionBytes(), equalTo(2048L));
        assertThat(config.fallbackRequestDirectory(), equalTo(directory));
        assertThat(config.fallbackRequestRetentionBytes(), equalTo(-1L));
    }
}
