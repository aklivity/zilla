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
package io.aklivity.zilla.config.engine;

import java.util.List;
import java.util.Map;

public abstract class ModelConfig extends Config.Extensible
{
    public final String model;
    public final List<CatalogedConfig> cataloged;
    public final ValidateConfig validate;
    public final String vault;

    public transient long vaultId;

    protected ModelConfig(
        String model,
        List<CatalogedConfig> cataloged,
        ValidateConfig validate)
    {
        this(model, cataloged, validate, null, null);
    }

    protected ModelConfig(
        String model,
        List<CatalogedConfig> cataloged,
        ValidateConfig validate,
        Map<String, Config> extensions)
    {
        this(model, cataloged, validate, null, extensions);
    }

    protected ModelConfig(
        String model,
        List<CatalogedConfig> cataloged,
        ValidateConfig validate,
        String vault,
        Map<String, Config> extensions)
    {
        super(extensions);
        this.model = model;
        this.cataloged = cataloged;
        this.validate = validate != null ? validate : ValidateConfig.STRICT;
        this.vault = vault;
    }
}
