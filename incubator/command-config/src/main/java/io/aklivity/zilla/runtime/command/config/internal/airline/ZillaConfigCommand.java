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

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Function;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.restrictions.AllowedValues;
import com.github.rvesse.airline.annotations.restrictions.Required;

import io.aklivity.zilla.runtime.command.ZillaCommand;
import io.aklivity.zilla.runtime.command.config.internal.asyncapi.http.proxy.AsyncApiHttpProxyConfigGenerator;
import io.aklivity.zilla.runtime.command.config.internal.asyncapi.mqtt.proxy.AsyncApiMqttProxyConfigGenerator;
import io.aklivity.zilla.runtime.command.config.internal.openapi.http.proxy.OpenApiHttpProxyConfigGenerator;

@Command(name = "generate", description = "Generate configuration file")
public final class ZillaConfigCommand extends ZillaCommand
{
    private static final Map<String, Function<InputStream, ConfigGenerator>> GENERATORS = Map.of(
        "openapi.http.proxy", OpenApiHttpProxyConfigGenerator::new,
        "asyncapi.http.proxy", AsyncApiHttpProxyConfigGenerator::new,
        "asyncapi.mqtt.proxy", AsyncApiMqttProxyConfigGenerator::new
    );

    @Option(name = {"-t", "--template"},
        description = "Template name")
    @Required
    @AllowedValues(allowedValues = {
        "openapi.http.proxy",
        "asyncapi.http.proxy",
        "asyncapi.mqtt.proxy"
    })
    public String template;

    @Option(name = {"-i", "--input"},
        description = "Input filename",
        typeConverterProvider = ZillaConfigCommandPathConverterProvider.class)
    public Path input;

    @Option(name = {"-o", "--output"},
        description = "Output filename",
        typeConverterProvider = ZillaConfigCommandPathConverterProvider.class)
    public Path output = Paths.get("zilla.yaml");

    @Override
    public void run()
    {
        try (InputStream inputStream = new FileInputStream(input.toFile()))
        {
            ConfigGenerator generator = GENERATORS.get(template).apply(inputStream);
            Files.writeString(output, generator.generate());
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            rethrowUnchecked(ex);
        }
    }
}
