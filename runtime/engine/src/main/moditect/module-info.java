/*
 * Copyright 2021-2021 Aklivity Inc.
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
module io.aklivity.zilla.runtime.engine
{
    exports io.aklivity.zilla.runtime.engine;
    exports io.aklivity.zilla.runtime.engine.cog;
    exports io.aklivity.zilla.runtime.engine.cog.budget;
    exports io.aklivity.zilla.runtime.engine.cog.buffer;
    exports io.aklivity.zilla.runtime.engine.cog.concurrent;
    exports io.aklivity.zilla.runtime.engine.cog.function;
    exports io.aklivity.zilla.runtime.engine.cog.poller;
    exports io.aklivity.zilla.runtime.engine.cog.stream;
    exports io.aklivity.zilla.runtime.engine.cog.vault;
    exports io.aklivity.zilla.runtime.engine.config;
    exports io.aklivity.zilla.runtime.engine.ext;

    requires transitive jakarta.json;
    requires transitive jakarta.json.bind;
    requires transitive org.agrona.core;
    requires org.leadpony.justify;
    requires jdk.unsupported;
    requires java.net.http;

    uses io.aklivity.zilla.runtime.engine.cog.CogFactorySpi;
    uses io.aklivity.zilla.runtime.engine.cog.vault.BindingVault;
    uses io.aklivity.zilla.runtime.engine.config.ConditionAdapterSpi;
    uses io.aklivity.zilla.runtime.engine.config.OptionsAdapterSpi;
    uses io.aklivity.zilla.runtime.engine.config.WithAdapterSpi;
    uses io.aklivity.zilla.runtime.engine.ext.EngineExtSpi;
}
