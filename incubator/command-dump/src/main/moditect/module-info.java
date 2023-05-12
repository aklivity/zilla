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
module io.aklivity.zilla.runtime.command.dump
{
    requires io.aklivity.zilla.runtime.command;
    requires io.aklivity.zilla.runtime.engine;

    opens io.aklivity.zilla.runtime.command.dump.internal.airline
        to com.github.rvesse.airline;

    provides io.aklivity.zilla.runtime.command.ZillaCommandSpi
        with io.aklivity.zilla.runtime.command.dump.internal.ZillaDumpCommandSpi;
}