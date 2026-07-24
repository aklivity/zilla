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
package io.aklivity.zilla.runtime.engine.test.internal.model;

import static java.util.Collections.emptyList;

import java.util.List;

import io.aklivity.zilla.config.engine.ValidateMode;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class TestModelHandler implements ModelHandler
{
    private final int length;
    private final int transformLength;
    private final List<String> fields;
    private final boolean decodeLenient;
    private final boolean encodeLenient;

    public TestModelHandler(
        TestModelConfig config)
    {
        this.length = config.length;
        this.transformLength = config.transformLength;
        this.fields = config.fields != null ? config.fields : emptyList();
        this.decodeLenient = config.validate.decode == ValidateMode.LENIENT;
        this.encodeLenient = config.validate.encode == ValidateMode.LENIENT;
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelVisitor visitor)
    {
        return new TestModelPipeline(length, transformLength, fields, decodeLenient, visitor);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelVisitor visitor)
    {
        return new TestModelPipeline(length, transformLength, fields, encodeLenient, visitor);
    }
}
