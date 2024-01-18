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
package io.aklivity.zilla.runtime.engine.config;

import java.util.List;

public abstract class ValidatorConfig
{
    public transient long id;

    public final String type;
    public final List<CatalogedConfig> cataloged;

    public ValidatorConfig(
        String type)
    {
        this(type, null);
    }

    public ValidatorConfig(
        String type,
        List<CatalogedConfig> cataloged)
    {
        this.type = type;
        this.cataloged = cataloged;
    }
}
