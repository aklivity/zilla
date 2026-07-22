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
package io.aklivity.zilla.config.model.protobuf;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.CatalogedConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.ValidateConfig;

public class ProtobufModelConfigBuilder<T> extends ConfigBuilder<T, ProtobufModelConfigBuilder<T>>
{
    private final Function<ProtobufModelConfig, T> mapper;

    private List<CatalogedConfig> catalogs;
    private String subject;
    private String view;
    private ValidateConfig validate;

    ProtobufModelConfigBuilder(
        Function<ProtobufModelConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<ProtobufModelConfigBuilder<T>> thisType()
    {
        return (Class<ProtobufModelConfigBuilder<T>>) getClass();
    }

    public CatalogedConfigBuilder<ProtobufModelConfigBuilder<T>> catalog()
    {
        return CatalogedConfig.builder(this::catalog);
    }

    public ProtobufModelConfigBuilder<T> subject(
        String subject)
    {
        this.subject = subject;
        return this;
    }

    public ProtobufModelConfigBuilder<T> catalog(
        CatalogedConfig catalog)
    {
        if (catalogs == null)
        {
            catalogs = new LinkedList<>();
        }
        catalogs.add(catalog);
        return this;
    }

    public ProtobufModelConfigBuilder<T> view(
        String view)
    {
        this.view = view;
        return this;
    }

    public ProtobufModelConfigBuilder<T> validate(
        ValidateConfig validate)
    {
        this.validate = validate;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new ProtobufModelConfig(catalogs, subject, view, validate));
    }
}
