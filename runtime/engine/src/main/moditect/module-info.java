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
module io.aklivity.zilla.engine.drive
{
    exports io.aklivity.zilla.engine.drive;
    exports io.aklivity.zilla.engine.drive.config;
    exports io.aklivity.zilla.engine.drive.ext;
    exports io.aklivity.zilla.engine.drive.cog;
    exports io.aklivity.zilla.engine.drive.cog.budget;
    exports io.aklivity.zilla.engine.drive.cog.buffer;
    exports io.aklivity.zilla.engine.drive.cog.concurrent;
    exports io.aklivity.zilla.engine.drive.cog.function;
    exports io.aklivity.zilla.engine.drive.cog.poller;
    exports io.aklivity.zilla.engine.drive.cog.stream;
    exports io.aklivity.zilla.engine.drive.cog.vault;

    requires transitive java.json;
    requires transitive java.json.bind;
    requires transitive org.agrona.core;
    requires jdk.unsupported;
    requires java.net.http;

    uses io.aklivity.zilla.engine.drive.config.ConditionAdapterSpi;
    uses io.aklivity.zilla.engine.drive.config.OptionsAdapterSpi;
    uses io.aklivity.zilla.engine.drive.config.WithAdapterSpi;
    uses io.aklivity.zilla.engine.drive.ext.DriveExtSpi;
    uses io.aklivity.zilla.engine.drive.cog.CogFactorySpi;
    uses io.aklivity.zilla.engine.drive.cog.vault.BindingVault;
}
