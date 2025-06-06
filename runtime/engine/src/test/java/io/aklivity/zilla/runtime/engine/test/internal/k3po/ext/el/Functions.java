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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.el;

import static java.lang.ThreadLocal.withInitial;

import java.nio.file.Path;

import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.ZillaExtConfiguration;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.LabelManager;

public final class Functions
{
    private static final ThreadLocal<LabelManager> LABELS = withInitial(Functions::newLabelManager);

    public static final class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(Functions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "zilla";
        }
    }

    @Function
    public static int id(
        String label)
    {
        final LabelManager labels = LABELS.get();
        return labels.supplyLabelId(label);
    }

    private Functions()
    {
        // utility
    }

    private static LabelManager newLabelManager()
    {
        final ZillaExtConfiguration config = new ZillaExtConfiguration();
        final Path directory = config.directory();
        return new LabelManager(directory);
    }
}
