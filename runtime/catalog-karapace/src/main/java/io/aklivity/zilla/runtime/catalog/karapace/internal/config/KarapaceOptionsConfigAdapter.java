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
package io.aklivity.zilla.runtime.catalog.karapace.internal.config;

import io.aklivity.zilla.runtime.catalog.karapace.config.KarapaceOptionsConfig;
import io.aklivity.zilla.runtime.catalog.karapace.internal.KarapaceCatalogFactorySpi;
import io.aklivity.zilla.runtime.catalog.schema.registry.AbstractSchemaRegistryOptionsConfigAdapter;

public final class KarapaceOptionsConfigAdapter extends AbstractSchemaRegistryOptionsConfigAdapter<KarapaceOptionsConfig>
{
    public KarapaceOptionsConfigAdapter()
    {
        super(KarapaceCatalogFactorySpi.TYPE, KarapaceCatalogFactorySpi.TYPE_ALIASES,
            KarapaceOptionsConfig.class, KarapaceOptionsConfig::builder);
    }
}
