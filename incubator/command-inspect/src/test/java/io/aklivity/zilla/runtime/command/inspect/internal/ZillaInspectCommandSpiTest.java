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
package io.aklivity.zilla.runtime.command.inspect.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

import org.junit.Test;

import com.github.rvesse.airline.Cli;
import com.github.rvesse.airline.builder.CliBuilder;

import io.aklivity.zilla.runtime.command.inspect.internal.airline.ZillaInspectSchemaCommand;

public class ZillaInspectCommandSpiTest
{
    @Test
    public void shouldRegisterSchemaCommandUnderInspectGroup()
    {
        CliBuilder<Runnable> builder = Cli.<Runnable>builder("zilla");

        new ZillaInspectCommandSpi().mixin(builder);

        Cli<Runnable> parser = builder.build();
        Runnable command = parser.parse("inspect", "schema");

        assertThat(command, instanceOf(ZillaInspectSchemaCommand.class));
    }
}
