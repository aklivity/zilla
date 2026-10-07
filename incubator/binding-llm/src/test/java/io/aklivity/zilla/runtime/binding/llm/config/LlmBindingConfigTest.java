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
package io.aklivity.zilla.runtime.binding.llm.config;

import static io.aklivity.zilla.config.engine.KindConfig.PROXY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.llm.LlmOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.ConfigException;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.test.internal.model.config.TestModelConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

public class LlmBindingConfigTest
{
    private EngineContext context;
    private ModelHandler handler;
    private ModelPipeline pipeline;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        handler = mock(ModelHandler.class);
        pipeline = mock(ModelPipeline.class);

        when(context.supplyModel(any())).thenReturn(handler);
        when(handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE)).thenReturn(pipeline);
        when(pipeline.identity()).thenReturn(true);
    }

    @Test
    public void shouldSupplyGuardrailStages()
    {
        LlmBindingConfig binding = new LlmBindingConfig(binding(LlmOptionsConfig.builder()
            .guardrail()
                .name("gpt-4o")
                .request()
                    .section("user-text")
                    .section("tool-result")
                    .value(TestModelConfig.builder().length(0).build())
                    .build()
                .build()
            .build()), context);

        assertThat(binding.guardrails("gpt-4o").request, hasSize(1));
        assertThat(binding.guardrails("gpt-4o").request.get(0).handler, sameInstance(handler));
        assertThat(binding.guardrails("gpt-4o").request.get(0).sections,
            contains(LlmSectionType.USER_TEXT, LlmSectionType.TOOL_RESULT));
        assertThat(binding.guardrails("gpt-4o").response, empty());
    }

    @Test
    public void shouldSupplyNoGuardrailStagesForUnguardedModel()
    {
        LlmBindingConfig binding = new LlmBindingConfig(binding(LlmOptionsConfig.builder().build()), context);

        assertThat(binding.guardrails("gpt-4o").request, empty());
        assertThat(binding.guardrails("gpt-4o").response, empty());
    }

    @Test
    public void shouldSupplyGuardrailResponseStages()
    {
        LlmBindingConfig binding = new LlmBindingConfig(binding(LlmOptionsConfig.builder()
            .guardrail()
                .name("gpt-4o")
                .response()
                    .section("assistant-text")
                    .value(TestModelConfig.builder().length(0).build())
                    .build()
                .build()
            .build()), context);

        assertThat(binding.guardrails("gpt-4o").request, empty());
        assertThat(binding.guardrails("gpt-4o").response, hasSize(1));
        assertThat(binding.guardrails("gpt-4o").response.get(0).sections, contains(LlmSectionType.ASSISTANT_TEXT));
    }

    @Test(expected = ConfigException.class)
    public void shouldRejectGuardrailResponseStagesSharingSection()
    {
        new LlmBindingConfig(binding(LlmOptionsConfig.builder()
            .guardrail()
                .name("gpt-4o")
                .response()
                    .section("assistant-text")
                    .value(TestModelConfig.builder().length(0).build())
                    .build()
                .response()
                    .section("assistant-text")
                    .value(TestModelConfig.builder().length(0).build())
                    .build()
                .build()
            .build()), context);
    }

    @Test(expected = ConfigException.class)
    public void shouldRejectGuardrailStagesSharingSection()
    {
        new LlmBindingConfig(binding(LlmOptionsConfig.builder()
            .guardrail()
                .name("gpt-4o")
                .request()
                    .section("user-text")
                    .value(TestModelConfig.builder().length(0).build())
                    .build()
                .request()
                    .section("user-text")
                    .value(TestModelConfig.builder().length(0).build())
                    .build()
                .build()
            .build()), context);
    }

    @Test(expected = ConfigException.class)
    public void shouldRejectGuardrailUnknownModel()
    {
        when(context.supplyModel(any())).thenReturn(null);

        new LlmBindingConfig(binding(LlmOptionsConfig.builder()
            .guardrail()
                .name("gpt-4o")
                .request()
                    .section("user-text")
                    .value(TestModelConfig.builder().length(0).build())
                    .build()
                .build()
            .build()), context);
    }

    @Test(expected = ConfigException.class)
    public void shouldRejectGuardrailTransformingModel()
    {
        when(pipeline.identity()).thenReturn(false);

        new LlmBindingConfig(binding(LlmOptionsConfig.builder()
            .guardrail()
                .name("gpt-4o")
                .request()
                    .section("user-text")
                    .value(TestModelConfig.builder().length(0).build())
                    .build()
                .build()
            .build()), context);
    }

    private static BindingConfig binding(
        LlmOptionsConfig options)
    {
        final BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("net0")
            .type("llm")
            .kind(PROXY)
            .options(options)
            .build();

        binding.resolveId = name -> 1L;

        return binding;
    }
}
