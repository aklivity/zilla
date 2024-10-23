/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.exporter.config;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class TestExporterOptionsConfigBuilder<T> extends ConfigBuilder<T, TestExporterOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private String mode;
    private List<TestExporterOptionsConfig.Event> events;

    TestExporterOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TestExporterOptionsConfigBuilder<T>> thisType()
    {
        return (Class<TestExporterOptionsConfigBuilder<T>>) getClass();
    }

    public TestExporterOptionsConfigBuilder<T> mode(
        String mode)
    {
        this.mode = mode;
        return this;
    }

    public TestExporterOptionsConfigBuilder<T> event(
        String qName,
        String id,
        String name,
        String message)
    {
        if (this.events == null)
        {
            this.events = new LinkedList<>();
        }
        this.events.add(new TestExporterOptionsConfig.Event(qName, id, name, message));
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TestExporterOptionsConfig(mode, events));
    }
}
