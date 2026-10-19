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

import static io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration.LLM_GUARDRAIL_OVERLAP;
import static io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration.LLM_SIGNED_REQUEST_MAX_BYTES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class LlmConfigurationTest
{
    public static final String LLM_GUARDRAIL_OVERLAP_NAME = "zilla.binding.llm.guardrail.overlap";

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
        assertThat(LLM_GUARDRAIL_OVERLAP.name(), equalTo(LLM_GUARDRAIL_OVERLAP_NAME));
    }

    @Test
    public void shouldDefaultGuardrailOverlap()
    {
        final LlmConfiguration config = new LlmConfiguration(new Configuration());

        assertThat(config.guardrailOverlap(), equalTo(0));
    }

    @Test
    public void shouldDefaultSignedRequestMaxBytes()
    {
        final LlmConfiguration config = new LlmConfiguration(new Configuration());

        assertThat(config.signedRequestMaxBytes(), equalTo(10 * 1024 * 1024));
    }
}
