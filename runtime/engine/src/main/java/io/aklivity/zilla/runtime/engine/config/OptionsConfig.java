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
package io.aklivity.zilla.runtime.engine.config;

import java.util.Collections;
import java.util.List;

public class OptionsConfig
{
    public final List<ModelConfig> models;
    public final List<String> resources;

    public OptionsConfig()
    {
        this(Collections.emptyList(), Collections.emptyList());
    }

    public OptionsConfig(
        List<ModelConfig> models,
        List<String> resources)
    {
        this.models = models;
        this.resources = resources;
    }
}
