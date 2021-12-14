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
package io.aklivity.zilla.manager.internal;

import com.github.rvesse.airline.annotations.Cli;
import com.github.rvesse.airline.help.Help;

import io.aklivity.zilla.manager.internal.commands.clean.ZmClean;
import io.aklivity.zilla.manager.internal.commands.encrypt.ZmEncrypt;
import io.aklivity.zilla.manager.internal.commands.install.ZmInstall;
import io.aklivity.zilla.manager.internal.commands.wrap.ZmWrap;

@Cli(name = "zm",
    description = "Zilla Management Tool",
    defaultCommand = Help.class,
    commands =
    {
        Help.class,
        ZmWrap.class,
        ZmInstall.class,
        ZmClean.class,
        ZmEncrypt.class
    })
public final class ZmCli
{
    private ZmCli()
    {
        // utility class
    }
}
