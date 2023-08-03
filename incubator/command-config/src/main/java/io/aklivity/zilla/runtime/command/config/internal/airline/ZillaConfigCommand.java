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
package io.aklivity.zilla.runtime.command.config.internal.airline;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.runtime.command.ZillaCommand;
import io.aklivity.zilla.runtime.command.config.internal.openapi.OpenApiConfigGenerator;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

@Command(name = "config", description = "Generate configuration file")
public final class ZillaConfigCommand extends ZillaCommand
{
    private static final String OPTION_PROPERTIES_PATH_DEFAULT = ".zilla/zilla.properties";

    @Option(name = {"-v", "--verbose"},
        description = "Show verbose output")
    public boolean verbose;

    @Option(name = {"-i", "--input"},
        description = "Input filename",
        typeConverterProvider = ZillaConfigCommandPathConverterProvider.class)
    public Path input;

    @Option(name = {"-o", "--output"},
        description = "Output filename",
        typeConverterProvider = ZillaConfigCommandPathConverterProvider.class)
    public Path output = Paths.get("zilla.yaml");

    @Option(name = {"-p", "--properties"},
        description = "Path to properties",
        hidden = true)
    public String propertiesPath;

    @Override
    public void run()
    {
        System.out.println("Hello World!"); // TODO: Ati
        if (verbose)
        {
            System.out.println("engine directory: " + engineDirectory());
            System.out.println("output: " + output);
        }

        OpenApiConfigGenerator openApi = new OpenApiConfigGenerator(input);
        System.out.println(openApi.generateConfig()); // TODO: Ati - write to output file
    }

    private Path engineDirectory()
    {
        Properties props = new Properties();
        props.setProperty(ENGINE_DIRECTORY.name(), ".zilla/engine");

        Path path = Paths.get(propertiesPath != null ? propertiesPath : OPTION_PROPERTIES_PATH_DEFAULT);
        if (Files.exists(path) || propertiesPath != null)
        {
            try
            {
                props.load(Files.newInputStream(path));
            }
            catch (IOException ex)
            {
                System.out.println("Failed to load properties: " + path);
                rethrowUnchecked(ex);
            }
        }
        EngineConfiguration config = new EngineConfiguration(props);
        return config.directory();
    }
}
