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

import java.io.StringReader;
import java.util.Map;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import io.aklivity.zilla.config.engine.internal.NamespaceAdapter;
import io.aklivity.zilla.runtime.common.yaml.YamlConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public final class NamespaceConfigReader
{
    private final Jsonb jsonb;

    public NamespaceConfigReader(
        BindingInfoRegistry bindingInfos,
        CatalogInfoRegistry catalogInfos,
        GuardInfoRegistry guardInfos,
        VaultInfoRegistry vaultInfos,
        ExporterInfoRegistry exporterInfos)
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new NamespaceAdapter(bindingInfos, catalogInfos, guardInfos, vaultInfos, exporterInfos));
        this.jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider(Map.of(YamlConfig.FEATURE_UNIQUE_KEYS, true)))
            .withConfig(config)
            .build();
    }

    public NamespaceConfig read(
        String text)
    {
        return jsonb.fromJson(new StringReader(text), NamespaceConfig.class);
    }
}
