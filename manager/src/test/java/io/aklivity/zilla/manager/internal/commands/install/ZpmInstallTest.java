/*
 * Copyright 2021-2026 Aklivity Inc.
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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.File;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import com.github.rvesse.airline.Cli;

import io.aklivity.zilla.manager.internal.ZpmCli;

public class ZpmInstallTest
{
    @Test
    public void shouldInstallEngine()
    {
        String[] args =
        {
            "install",
            "--config-directory", "src/conf/install",
            "--lock-directory", "target/test-locks/install",
            "--output-directory", "target/zpm",
            "--launcher-directory", "target",
            "--silent"
        };

        Cli<Runnable> parser = new Cli<>(ZpmCli.class);
        Runnable install = parser.parse(args);

        install.run();

        assertThat(install, instanceOf(ZpmInstall.class));
        assertThat(new File("src/conf/install/zpm.json"), anExistingFile());
        assertThat(new File("target/test-locks/install/zpm-lock.json"), anExistingFile());
        assertThat(new File("target/zpm/cache/io/aklivity/zilla/engine/0.9.5/engine-0.9.5.jar"), anExistingFile());
        assertThat(new File("target/zpm/cache/io/aklivity/zilla/binding-tcp/0.9.5/binding-tcp-0.9.5.jar"), anExistingFile());
        assertThat(new File("target/zpm/cache/io/aklivity/zilla/binding-tls/0.9.5/binding-tls-0.9.5.jar"), anExistingFile());
        assertThat(new File("target/zpm/cache/org/agrona/agrona/1.6.0/agrona-1.6.0.jar"), anExistingFile());
    }

    @Test
    public void shouldPreserveUsesClauseWhenMergingModuleIntoDelegate()
    {
        String[] args =
        {
            "install",
            "--config-directory", "src/conf/install-delegate-uses",
            "--lock-directory", "target/test-locks/install-delegate-uses",
            "--output-directory", "target/zpm-delegate-uses",
            "--launcher-directory", "target",
            "--exclude-remote-repositories",
            "--silent"
        };

        Cli<Runnable> parser = new Cli<>(ZpmCli.class);
        Runnable install = parser.parse(args);

        install.run();

        Path delegateJar = Paths.get("target/zpm-delegate-uses/modules/io.aklivity.zilla.manager.delegate.jar");
        assertThat(delegateJar.toFile(), anExistingFile());

        ModuleReference reference = ModuleFinder.of(delegateJar).findAll().iterator().next();
        ModuleDescriptor descriptor = reference.descriptor();

        assertThat(descriptor.uses(), hasItem("com.example.fixture.uses.FixtureService"));
    }
}
