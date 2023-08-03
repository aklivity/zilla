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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.runtime.command.ZillaCommand;
import io.aklivity.zilla.runtime.command.config.internal.model.openapi.OpenApi;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.ConfigWriter;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtKeyConfig;
import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;
import io.aklivity.zilla.runtime.guard.jwt.internal.JwtGuard;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemOptionsConfig;
import io.aklivity.zilla.runtime.vault.filesystem.config.FileSystemStoreConfig;

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
        System.out.println("Hello World!");
        if (verbose)
        {
            System.out.println("engine directory: " + engineDirectory());
            System.out.println("output: " + output);
        }

        OpenApi openApi = parseOpenApi(input);
        NamespaceConfig namespace = createConfig(openApi);
        writeConfig(namespace);
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

    private OpenApi parseOpenApi(
        Path input)
    {
        OpenApi openApi = null;
        try (InputStream inputStream = new FileInputStream(input.toFile()))
        {
            Jsonb jsonb = JsonbBuilder.create();
            openApi = jsonb.fromJson(inputStream, OpenApi.class);
            // TODO: Ati
            System.out.println(openApi.openapi);
            System.out.println(openApi.components.securitySchemes.bearerAuth.bearerFormat);
            jsonb.close();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            rethrowUnchecked(ex);
        }
        return openApi;
    }

    private NamespaceConfig createConfig(
        OpenApi openApi)
    {
        // guards
        String guardType = openApi.components.securitySchemes.bearerAuth.bearerFormat;
        OptionsConfig guardOptions = null;
        if (JwtGuard.NAME.equals(guardType))
        {
            String n = "qqEu50hX+43Bx4W1UYWnAVKwFm+vDbP0kuIOSLVNa+HKQdHTf+3Sei5UCnkskn796izA29D0DdCy3ET9oaKRHIJyKbqFl0rv6f516Q" +
                "zOoXKC6N01sXBHBE/ovs0wwDvlaW+gFGPgkzdcfUlyrWLDnLV7LcuQymhTND2uH0oR3wJnNENN/OFgM1KGPPDOe19YsIKdLqARgxrhZVsh06O" +
                "urEviZTXOBFI5r+yac7haDwOQhLHXNv+Y9MNvxs5QLWPFIM3bNUWfYrJnLrs4hGJS+y/KDM9Si+HL30QAFXy4YNO33J8DHjZ7ddG5n8/FqplO" +
                "KvRtUgjcKWlxoGY4VdVaDQ==";
            JwtKeyConfig key = new JwtKeyConfig("RSA", "example", null, n, "AQAB", "RS256", null, null, null);
            guardOptions = new JwtOptionsConfig("https://auth.example.com", "https://api.example.com", List.of(key), null);
        }
        GuardConfig guard = new GuardConfig("jwt0", guardType, guardOptions);
        List<GuardConfig> guards = List.of(guard);

        // vaults
        FileSystemStoreConfig trust = new FileSystemStoreConfig("tls/truststore.p12", "pkcs12", "${{env.KEYSTORE_PASSWORD}}");
        FileSystemOptionsConfig options = new FileSystemOptionsConfig(null, trust, null);
        VaultConfig clientVault = new VaultConfig("client", "filesystem", options);
        VaultConfig serverVault = new VaultConfig("server", "filesystem", options);
        List<VaultConfig> vaults = List.of(clientVault, serverVault);

        // namespace
        return new NamespaceConfig("example", List.of(), null, List.of(), guards, vaults);
    }

    private void writeConfig(
        NamespaceConfig namespace)
    {
        ConfigAdapterContext context = location -> "hello"; // TODO: Ati - ?
        ConfigWriter configWriter = new ConfigWriter(null);
        System.out.println(configWriter.write(namespace)); // TODO: Ati - write to output file
    }
}
