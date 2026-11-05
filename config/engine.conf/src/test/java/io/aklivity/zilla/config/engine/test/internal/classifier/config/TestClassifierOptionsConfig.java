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

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class TestClassifierOptionsConfig extends OptionsConfig
{
    public final boolean failure;
    public final List<String> failureOn;
    public final int delay;

    public static TestClassifierOptionsConfigBuilder<TestClassifierOptionsConfig> builder()
    {
        return new TestClassifierOptionsConfigBuilder<>(TestClassifierOptionsConfig.class::cast);
    }

    public static <T> TestClassifierOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new TestClassifierOptionsConfigBuilder<>(mapper);
    }

    TestClassifierOptionsConfig(
        boolean failure,
        List<String> failureOn,
        int delay)
    {
        super(null, null);
        this.failure = failure;
        this.failureOn = failureOn;
        this.delay = delay;
    }
}
