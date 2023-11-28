/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.validator;

import java.net.URL;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.ValidatorFactorySpi;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;

public class TestValidatorFactory implements ValidatorFactorySpi
{
    public static final DirectBuffer SCHEMA_ID_PREFIX = new UnsafeBuffer(new byte[]{0, 0, 0, 0, 1});

    @Override
    public String type()
    {
        return "test";
    }

    @Override
    public URL schema()
    {
        return getClass().getResource("test.schema.patch.json");
    }

    @Override
    public ValueValidator createValueReader(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return createReader(config, supplyCatalog);
    }

    @Override
    public ValueValidator createValueWriter(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return createWriter(config, supplyCatalog);
    }

    @Override
    public FragmentValidator createFragmentReader(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return createReader(config, supplyCatalog);
    }

    @Override
    public FragmentValidator createFragmentWriter(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return createWriter(config, supplyCatalog);
    }

    private TestReadValidator createReader(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return new TestReadValidator();
    }

    private TestWriteValidator createWriter(
        ValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        return new TestWriteValidator();
    }
}
