/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.common.internal;

import static io.aklivity.zilla.runtime.common.internal.ZillaTestCommandSpi.TEST_ARGUMENT;

import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Command;

@Command(name = "test", hidden = true)
public final class ZillaTestCommand implements Runnable
{
    @Arguments(description = "argument")
    public String argument = "arg";

    @Override
    public void run()
    {
        if ("throw".equals(argument))
        {
            throw new RuntimeException();
        }

        TEST_ARGUMENT.set(argument);
    }
}

