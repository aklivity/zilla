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
package io.aklivity.zilla.config.engine.test.internal.model.config;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.CatalogedConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ValidateConfig;

public class TestModelConfigBuilder<T> extends ConfigBuilder<T, TestModelConfigBuilder<T>>
{
    private final Function<ModelConfig, T> mapper;

    private int length;
    private boolean read;
    private int transformLength = -1;
    private List<CatalogedConfig> catalogs;
    private List<String> fields;
    private ValidateConfig validate;
    private List<Long> transformAuthorizations;
    private List<String> reject;
    private boolean suspend;
    private List<Long> discloseAuthorized;
    private String discloseRedacted;
    private String envelopeDiscloseName;

    TestModelConfigBuilder(
        Function<ModelConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TestModelConfigBuilder<T>> thisType()
    {
        return (Class<TestModelConfigBuilder<T>>) getClass();
    }

    public TestModelConfigBuilder<T> length(
        int length)
    {
        this.length = length;
        return this;
    }

    public TestModelConfigBuilder<T> read(
        boolean read)
    {
        this.read = read;
        return this;
    }

    public TestModelConfigBuilder<T> transformLength(
        int transformLength)
    {
        this.transformLength = transformLength;
        return this;
    }

    public TestModelConfigBuilder<T> transformAuthorization(
        long transformAuthorization)
    {
        if (transformAuthorizations == null)
        {
            transformAuthorizations = new LinkedList<>();
        }
        transformAuthorizations.add(transformAuthorization);
        return this;
    }

    public TestModelConfigBuilder<T> discloseAuthorized(
        long discloseAuthorized)
    {
        if (this.discloseAuthorized == null)
        {
            this.discloseAuthorized = new LinkedList<>();
        }
        this.discloseAuthorized.add(discloseAuthorized);
        return this;
    }

    public TestModelConfigBuilder<T> discloseRedacted(
        String discloseRedacted)
    {
        this.discloseRedacted = discloseRedacted;
        return this;
    }

    public TestModelConfigBuilder<T> envelopeDiscloseName(
        String envelopeDiscloseName)
    {
        this.envelopeDiscloseName = envelopeDiscloseName;
        return this;
    }

    public TestModelConfigBuilder<T> field(
        String field)
    {
        if (fields == null)
        {
            fields = new LinkedList<>();
        }
        fields.add(field);
        return this;
    }

    public CatalogedConfigBuilder<TestModelConfigBuilder<T>> catalog()
    {
        return CatalogedConfig.builder(this::catalog);
    }

    public TestModelConfigBuilder<T> catalog(
        CatalogedConfig catalog)
    {
        if (catalogs == null)
        {
            catalogs = new LinkedList<>();
        }
        catalogs.add(catalog);
        return this;
    }

    public TestModelConfigBuilder<T> validate(
        ValidateConfig validate)
    {
        this.validate = validate;
        return this;
    }

    public TestModelConfigBuilder<T> reject(
        String reject)
    {
        if (this.reject == null)
        {
            this.reject = new LinkedList<>();
        }
        this.reject.add(reject);
        return this;
    }

    public TestModelConfigBuilder<T> suspend(
        boolean suspend)
    {
        this.suspend = suspend;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(
            new TestModelConfig(length, catalogs, read, transformLength, fields, validate, transformAuthorizations,
                reject, suspend, discloseAuthorized, discloseRedacted, envelopeDiscloseName));
    }
}
