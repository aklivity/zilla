/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.command.internal;

import static io.aklivity.zilla.runtime.common.feature.FeatureFilter.filter;
import static java.util.ServiceLoader.load;

import com.github.rvesse.airline.Cli;
import com.github.rvesse.airline.builder.CliBuilder;
import com.github.rvesse.airline.help.Help;

import io.aklivity.zilla.runtime.command.ZillaCommandSpi;

public final class ZillaMain
{
    private static final String EXECUTABLE_NAME = "zilla";

    public static void main(
        String[] args)
    {
        System.exit(invoke(args));
    }

    static int invoke(
        String[] args)
    {
        return Invoker.invoke(args);
    }

    private ZillaMain()
    {
    }

    private static final class Invoker
    {
        private static int invoke(
            String[] args)
        {
            final CliBuilder<Runnable> builder = Cli.<Runnable>builder(EXECUTABLE_NAME)
                    .withDefaultCommand(Help.class)
                    .withCommand(Help.class);

            ClassLoader loader = Thread.currentThread().getContextClassLoader();

            for (ZillaCommandSpi service : filter(load(ZillaCommandSpi.class, loader)))
            {
                service.mixin(builder);
            }

            final Cli<Runnable> parser = builder.build();
            final Runnable command = parser.parse(args);

            int status = 0;
            try
            {
                command.run();
            }
            catch (Throwable ex)
            {
                status = 1;
            }

            return status;
        }
    }
}

