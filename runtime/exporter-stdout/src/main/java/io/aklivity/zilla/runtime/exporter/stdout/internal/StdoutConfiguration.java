/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.stdout.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_VERBOSE_EVENTS;

import java.io.PrintStream;
import java.lang.reflect.Field;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.Configuration;

public class StdoutConfiguration extends Configuration
{
    private static final ConfigurationDef STDOUT_CONFIG;

    public static final PropertyDef<PrintStream> STDOUT_OUTPUT;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.exporter.stdout");
        STDOUT_OUTPUT = config.property(PrintStream.class, "output",
            StdoutConfiguration::decodeOutput, c -> System.out);
        STDOUT_CONFIG = config;
    }

    public StdoutConfiguration(
        Configuration config)
    {
        super(STDOUT_CONFIG, config);
    }

    public PrintStream output()
    {
        return STDOUT_OUTPUT.get(this);
    }

    public boolean verboseEvents()
    {
        return ENGINE_VERBOSE_EVENTS.getAsBoolean(this);
    }

    private static PrintStream decodeOutput(
        Configuration config,
        String value)
    {
        try
        {
            int fieldAt = value.lastIndexOf(".");
            Class<?> ownerClass = Class.forName(value.substring(0, fieldAt));
            String fieldName = value.substring(fieldAt + 1);
            Field field = ownerClass.getDeclaredField(fieldName);
            return (PrintStream) field.get(null);
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
        return null;
    }
}
