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
package io.aklivity.zilla.config.engine.test.internal.classifier.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class TestClassifierOptionsConfigBuilder<T> extends ConfigBuilder<T, TestClassifierOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private boolean failure;
    private List<String> failureOn = new ArrayList<>();
    private int delay;

    TestClassifierOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TestClassifierOptionsConfigBuilder<T>> thisType()
    {
        return (Class<TestClassifierOptionsConfigBuilder<T>>) getClass();
    }

    public TestClassifierOptionsConfigBuilder<T> failure(
        boolean failure)
    {
        this.failure = failure;
        return this;
    }

    public TestClassifierOptionsConfigBuilder<T> failureOn(
        String value)
    {
        failureOn.add(value);
        return this;
    }

    public TestClassifierOptionsConfigBuilder<T> delay(
        int delay)
    {
        this.delay = delay;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TestClassifierOptionsConfig(failure, failureOn, delay));
    }
}
