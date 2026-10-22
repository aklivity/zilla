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
package io.aklivity.zilla.config.binding.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class LlmOptionsConfigBuilder<T> extends ConfigBuilder.Extensible<T, LlmOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private String dialect;
    private LlmAuthorizationConfig authorization;
    private LlmServerConfig server;
    private List<LlmGuardrailConfig> guardrails;
    private List<LlmModelConfig> models;
    private List<String> unsupported;
    private List<List<String>> exclusive;

    LlmOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
        this.guardrails = new ArrayList<>();
        this.models = new ArrayList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<LlmOptionsConfigBuilder<T>> thisType()
    {
        return (Class<LlmOptionsConfigBuilder<T>>) getClass();
    }

    public LlmOptionsConfigBuilder<T> dialect(
        String dialect)
    {
        this.dialect = dialect;
        return this;
    }

    public LlmOptionsConfigBuilder<T> authorization(
        LlmAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public LlmAuthorizationConfigBuilder<LlmOptionsConfigBuilder<T>> authorization()
    {
        return LlmAuthorizationConfig.builder(this::authorization);
    }

    public LlmServerConfigBuilder<LlmOptionsConfigBuilder<T>> server()
    {
        return LlmServerConfig.builder(this::server);
    }

    public LlmOptionsConfigBuilder<T> server(
        LlmServerConfig server)
    {
        this.server = server;
        return this;
    }

    public LlmOptionsConfigBuilder<T> guardrail(
        LlmGuardrailConfig guardrail)
    {
        this.guardrails.add(guardrail);
        return this;
    }

    public LlmGuardrailConfigBuilder<LlmOptionsConfigBuilder<T>> guardrail()
    {
        return LlmGuardrailConfig.builder(this::guardrail);
    }

    public LlmOptionsConfigBuilder<T> model(
        LlmModelConfig model)
    {
        this.models.add(model);
        return this;
    }

    public LlmModelConfigBuilder<LlmOptionsConfigBuilder<T>> model()
    {
        return LlmModelConfig.builder(this::model);
    }

    public LlmOptionsConfigBuilder<T> unsupported(
        List<String> unsupported)
    {
        this.unsupported = List.copyOf(unsupported);
        return this;
    }

    public LlmOptionsConfigBuilder<T> exclusive(
        List<List<String>> exclusive)
    {
        this.exclusive = exclusive.stream().map(List::copyOf).toList();
        return this;
    }

    @Override
    public T build()
    {
        List<Config.Reference> refs = Stream.concat(
                guardrails.stream()
                    .flatMap(guardrail -> Stream.concat(guardrail.request.stream(), guardrail.response.stream()))
                    .flatMap(stage -> stage.value.refs().stream()),
                refs().stream())
            .toList();

        return mapper.apply(new LlmOptionsConfig(dialect, authorization, server, List.copyOf(guardrails),
            List.copyOf(models), unsupported, exclusive, extensions(), refs));
    }
}
