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
package io.aklivity.zilla.runtime.model.core.internal;

import java.util.List;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.Model;
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExt;

public class BytesModel implements Model
{
    public static final String NAME = "bytes";

    private final List<BytesModelExt> exts;

    public BytesModel(
        List<BytesModelExt> exts)
    {
        this.exts = exts;
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public ModelContext supply(
        EngineContext context)
    {
        return new BytesModelContext(context, exts.stream().map(ext -> ext.supply(context)).toList());
    }
}
