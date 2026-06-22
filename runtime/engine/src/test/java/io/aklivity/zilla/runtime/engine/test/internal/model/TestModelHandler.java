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
package io.aklivity.zilla.runtime.engine.test.internal.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class TestModelHandler implements ModelHandler
{
    private static final String PATH = "^\\$\\.([A-Za-z_][A-Za-z0-9_]*)$";
    private static final Pattern PATH_PATTERN = Pattern.compile(PATH);

    private final int length;
    private final int transformLength;
    private final List<String> paths;
    private final Matcher matcher;

    public TestModelHandler(
        TestModelConfig config)
    {
        this.length = config.length;
        this.transformLength = config.transformLength;
        this.paths = new ArrayList<>();
        this.matcher = PATH_PATTERN.matcher("");
    }

    @Override
    public void extract(
        String path)
    {
        if (matcher.reset(path).matches())
        {
            paths.add(path);
        }
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelVisitor visitor)
    {
        return new TestModelPipeline(length, transformLength, paths, visitor);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelVisitor visitor)
    {
        return new TestModelPipeline(length, transformLength, paths, visitor);
    }
}
