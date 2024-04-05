/*
 * Copyright 2021-2023 Aklivity Inc
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

import static io.aklivity.zilla.runtime.model.core.internal.FloatModel.NAME;

import java.net.URL;

import io.aklivity.zilla.runtime.common.feature.Incubating;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.model.Model;
import io.aklivity.zilla.runtime.engine.model.ModelFactorySpi;

@Incubating
public class FloatModelFactorySpi implements ModelFactorySpi
{
    @Override
    public String type()
    {
        return NAME;
    }

    @Override
    public URL schema()
    {
        return getClass().getResource("schema/float.schema.patch.json");
    }

    @Override
    public Model create(
        Configuration config)
    {
        return new FloatModel();
    }
}
