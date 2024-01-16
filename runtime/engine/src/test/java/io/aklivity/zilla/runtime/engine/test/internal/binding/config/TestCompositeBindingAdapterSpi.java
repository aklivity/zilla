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
package io.aklivity.zilla.runtime.engine.test.internal.binding.config;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public class TestCompositeBindingAdapterSpi implements CompositeBindingAdapterSpi
{
    @Override
    public String type()
    {
        return "test";
    }

    @Override
    public BindingConfig adapt(
        BindingConfig binding)
    {
        switch (binding.kind)
        {
        case PROXY:
            return BindingConfig.builder(binding)
                    .composite()
                        .name(String.format(binding.qname, "$composite"))
                        .binding()
                            .name("test0")
                            .type("test")
                            .kind(KindConfig.SERVER)
                            .build()
                        .build()
                    .build();
        default:
            return binding;
        }
    }

}
