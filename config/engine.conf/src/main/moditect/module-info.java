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
module io.aklivity.zilla.config.engine
{
    requires transitive jakarta.json;
    requires transitive jakarta.json.bind;
    requires transitive io.aklivity.zilla.runtime.common.lang;
    requires org.agrona;
    requires io.aklivity.zilla.runtime.common.feature;
    requires io.aklivity.zilla.runtime.common.yaml;

    exports io.aklivity.zilla.config.engine;
    exports io.aklivity.zilla.config.engine.factory;

    uses io.aklivity.zilla.config.engine.BindingInfo;
    uses io.aklivity.zilla.config.engine.CatalogInfo;
    uses io.aklivity.zilla.config.engine.GuardInfo;
    uses io.aklivity.zilla.config.engine.VaultInfo;
    uses io.aklivity.zilla.config.engine.ExporterInfo;
    uses io.aklivity.zilla.config.engine.ModelInfo;
    uses io.aklivity.zilla.config.engine.StoreInfo;
    uses io.aklivity.zilla.config.engine.OptionsConfigAdapterSpi;
    uses io.aklivity.zilla.config.engine.ConditionConfigAdapterSpi;
    uses io.aklivity.zilla.config.engine.WithConfigAdapterSpi;
    uses io.aklivity.zilla.config.engine.ModelConfigAdapterSpi;
}
