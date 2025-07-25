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
package io.aklivity.zilla.runtime.engine.binding;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.EngineController;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public interface Binding
{
    String name();

    BindingContext supply(
        EngineContext context);

    default BindingController supply(
        EngineController controller)
    {
        return null;
    }

    default URL type()
    {
        return null;
    }

    default String originType(
        KindConfig kind)
    {
        return null;
    }

    default String routedType(
        KindConfig kind)
    {
        return null;
    }

    default int workers(
        KindConfig kind)
    {
        return Integer.MAX_VALUE;
    }
}
