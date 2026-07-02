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
package io.aklivity.zilla.manager.internal.commands.install;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.Test;

import com.github.rvesse.airline.Cli;

import io.aklivity.zilla.manager.internal.ZpmCli;

public class ZpmInstallTest
{
    @Test
    public void shouldInstallEngine() throws Exception
    {
        Properties versions = new Properties();
        try (InputStream resource = getClass().getResourceAsStream("/conf/install/version.properties"))
        {
            versions.load(resource);
        }
        String version = versions.getProperty("project.version");
        String agronaVersion = versions.getProperty("agrona.version");

        Path configDir = Paths.get(getClass().getResource("/conf/install/zpm.json").toURI()).getParent();

        String[] args =
        {
            "install",
            "--config-directory", configDir.toString(),
            "--lock-directory", "target/test-locks/install",
            "--output-directory", "target/zpm",
            "--launcher-directory", "target",
            "--silent"
        };

        Cli<Runnable> parser = new Cli<>(ZpmCli.class);
        Runnable install = parser.parse(args);

        install.run();

        assertThat(install, instanceOf(ZpmInstall.class));
        assertThat(configDir.resolve("zpm.json").toFile(), anExistingFile());
        assertThat(new File("target/test-locks/install/zpm-lock.json"), anExistingFile());
        assertThat(new File(String.format("target/zpm/cache/io/aklivity/zilla/engine/%1$s/engine-%1$s.jar", version)),
            anExistingFile());
        assertThat(new File(String.format("target/zpm/cache/io/aklivity/zilla/binding-tcp/%1$s/binding-tcp-%1$s.jar", version)),
            anExistingFile());
        assertThat(new File(String.format("target/zpm/cache/io/aklivity/zilla/binding-tls/%1$s/binding-tls-%1$s.jar", version)),
            anExistingFile());
        assertThat(new File(String.format("target/zpm/cache/org/agrona/agrona/%1$s/agrona-%1$s.jar", agronaVersion)),
            anExistingFile());
    }

    @Test
    public void shouldInstallEngineExcludingRemoteRepositories() throws Exception
    {
        Properties versions = new Properties();
        try (InputStream resource = getClass().getResourceAsStream("/conf/install/version.properties"))
        {
            versions.load(resource);
        }
        String version = versions.getProperty("project.version");
        String agronaVersion = versions.getProperty("agrona.version");

        Path configDir = Paths.get(getClass().getResource("/conf/install/zpm.json").toURI()).getParent();

        String[] args =
        {
            "install",
            "--config-directory", configDir.toString(),
            "--lock-directory", "target/test-locks/install-offline",
            "--output-directory", "target/zpm-offline",
            "--launcher-directory", "target",
            "--exclude-remote-repositories",
            "--silent"
        };

        Cli<Runnable> parser = new Cli<>(ZpmCli.class);
        Runnable install = parser.parse(args);

        install.run();

        assertThat(install, instanceOf(ZpmInstall.class));
        assertThat(new File("target/test-locks/install-offline/zpm-lock.json"), anExistingFile());
        assertThat(new File(String.format("target/zpm-offline/cache/io/aklivity/zilla/engine/%1$s/engine-%1$s.jar", version)),
            anExistingFile());
        assertThat(new File(String.format("target/zpm-offline/cache/org/agrona/agrona/%1$s/agrona-%1$s.jar", agronaVersion)),
            anExistingFile());
    }

    @Test
    public void shouldGenerateLauncherWithIncubatorModule() throws Exception
    {
        Path configDir = Paths.get(getClass().getResource("/conf/install/zpm.json").toURI()).getParent();
        Path launcherDir = Paths.get("target/launcher-incubator");
        Files.createDirectories(launcherDir);

        String[] args =
        {
            "install",
            "--config-directory", configDir.toString(),
            "--lock-directory", "target/test-locks/install-incubator",
            "--output-directory", "target/zpm-incubator",
            "--launcher-directory", launcherDir.toString(),
            "--exclude-remote-repositories",
            "--incubator", "vector",
            "--silent"
        };

        Cli<Runnable> parser = new Cli<>(ZpmCli.class);
        Runnable install = parser.parse(args);

        install.run();

        assertThat(install, instanceOf(ZpmInstall.class));

        Path launcherPath = launcherDir.resolve("zilla");
        assertThat(launcherPath.toFile(), anExistingFile());

        String launcher = Files.readString(launcherPath);
        assertThat(launcher, containsString("--add-modules jdk.incubator.vector"));
    }
}
