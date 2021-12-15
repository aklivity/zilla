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

import io.aklivity.zilla.manager.internal.commands.clean.ZpmClean;
import io.aklivity.zilla.manager.internal.commands.encrypt.ZpmEncrypt;
import io.aklivity.zilla.manager.internal.commands.install.ZpmInstall;
import io.aklivity.zilla.manager.internal.commands.wrap.ZpmWrap;

@Cli(name = "zpm",
    description = "Zilla Package Manager",
    defaultCommand = Help.class,
    commands =
    {
        Help.class,
        ZpmWrap.class,
        ZpmInstall.class,
        ZpmClean.class,
        ZpmEncrypt.class
    })
public final class ZpmCli
{
    private ZpmCli()
    {
        // utility class
    }
}
