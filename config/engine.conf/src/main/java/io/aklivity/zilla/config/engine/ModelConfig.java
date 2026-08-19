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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ModelConfig extends Config.Extensible
{
    public final String model;
    public final List<CatalogedConfig> cataloged;
    public final ValidateConfig validate;

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
        this(model, cataloged, validate, extensions, null);
    }

    protected ModelConfig(
        String model,
        List<CatalogedConfig> cataloged,
        ValidateConfig validate,
        Map<String, Config> extensions,
        List<NamedConfig> refs)
    {
        super(extensions, withCataloged(cataloged, refs));
        this.model = model;
        this.cataloged = cataloged;
        this.validate = validate != null ? validate : ValidateConfig.STRICT;
    }

    // cataloged and each of its schemas' overlay are themselves NamedConfig, so folding them into refs
    // lets the engine resolve every name this model carries -- cataloged, overlay, and whatever an
    // installed extension contributed -- with one generic walk, rather than a schema-specific walk plus
    // a separate generic one
    private static List<NamedConfig> withCataloged(
        List<CatalogedConfig> cataloged,
        List<NamedConfig> refs)
    {
        List<NamedConfig> all = new ArrayList<>();
        if (cataloged != null)
        {
            all.addAll(cataloged);
            for (CatalogedConfig catalog : cataloged)
            {
                for (SchemaConfig schema : catalog.schemas)
                {
                    if (schema.overlay != null)
                    {
                        all.add(schema.overlay);
                    }
                }
            }
        }
        if (refs != null)
        {
            all.addAll(refs);
        }
        return all;
    }
}
