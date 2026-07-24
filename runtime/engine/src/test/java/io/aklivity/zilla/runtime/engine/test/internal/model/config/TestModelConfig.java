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
package io.aklivity.zilla.runtime.engine.test.internal.model.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ValidateConfig;

public class TestModelConfig extends ModelConfig
{
    public final int length;
    public final boolean read;
    public final int transformLength;
    public final List<String> fields;

    public TestModelConfig(
        int length,
        List<CatalogedConfig> cataloged,
        boolean read)
    {
        this(length, cataloged, read, -1);
    }

    public TestModelConfig(
        int length,
        List<CatalogedConfig> cataloged,
        boolean read,
        int transformLength)
    {
        this(length, cataloged, read, transformLength, null);
    }

    public TestModelConfig(
        int length,
        List<CatalogedConfig> cataloged,
        boolean read,
        int transformLength,
        List<String> fields)
    {
        this(length, cataloged, read, transformLength, fields, ValidateConfig.STRICT);
    }

    public TestModelConfig(
        int length,
        List<CatalogedConfig> cataloged,
        boolean read,
        int transformLength,
        List<String> fields,
        ValidateConfig validate)
    {
        super("test", cataloged, validate);
        this.length = length;
        this.read = read;
        this.transformLength = transformLength;
        this.fields = fields;
    }

    public static <T> TestModelConfigBuilder<T> builder(
        Function<ModelConfig, T> mapper)
    {
        return new TestModelConfigBuilder<>(mapper);
    }

    public static TestModelConfigBuilder<TestModelConfig> builder()
    {
        return new TestModelConfigBuilder<>(TestModelConfig.class::cast);
    }
}
