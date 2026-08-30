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
package io.aklivity.zilla.config.engine.test.internal.binding;

import java.util.function.BiFunction;

import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.ConfigExtBuilder;

public final class TestBindingExtConfigBuilder<B> extends ConfigExtBuilder<B>
{
    private String value;

    public TestBindingExtConfigBuilder(
        BiFunction<String, Config, B> mapper)
    {
        super(mapper);
    }

    public TestBindingExtConfigBuilder<B> value(
        String value)
    {
        this.value = value;
        return this;
    }

    @Override
    public B build()
    {
        return mapper.apply(TestBindingExtConfig.NAME, new TestBindingExtConfig(value));
    }
}
