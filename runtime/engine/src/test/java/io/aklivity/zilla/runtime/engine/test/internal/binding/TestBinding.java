/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.test.internal.binding;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.ConfigException;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public final class TestBinding implements Binding
{
    public static final String NAME = "test";
    public static final String NAME_INVALID = "invalid";

    private final Configuration config;
    private final List<EngineContext> contexts = new CopyOnWriteArrayList<>();

    TestBinding(
        Configuration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return "test";
    }

    @Override
    public TestBindingContext supply(
        EngineContext context)
    {
        contexts.add(context);
        return new TestBindingContext(config, context);
    }

    public List<EngineContext> contexts()
    {
        return contexts;
    }

    @Override
    public void validate(
        BindingConfig binding) throws ConfigException
    {
        if (NAME_INVALID.equals(binding.name))
        {
            throw new ConfigException(String.format("%s: binding validation failed", binding.qname));
        }
    }
}
