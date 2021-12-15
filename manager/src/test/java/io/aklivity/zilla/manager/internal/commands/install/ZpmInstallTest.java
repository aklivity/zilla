/*
 * Copyright 2021-2021 Aklivity Inc.
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
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.github.rvesse.airline.Cli;

import io.aklivity.zilla.manager.internal.ZpmCli;

public class ZpmInstallTest
{
    @Test
    public void shouldInstallEngine() throws IOException
    {
        String[] args =
        {
            "install",
            "--config-directory", "src/test/conf/install",
            "--lock-directory", "target/test-locks/install",
            "--output-directory", "target/zpm",
            "--launcher-directory", "target",
            "--silent"
        };

        Cli<Runnable> parser = new Cli<>(ZpmCli.class);
        Runnable install = parser.parse(args);

        install.run();

        assertThat(install, instanceOf(ZpmInstall.class));
        assertThat(new File("src/test/conf/install/zpm.json"), anExistingFile());
        assertThat(new File("target/test-locks/install/zpm-lock.json"), anExistingFile());
        assertThat(new File("target/zpm/cache/io.aklivity.zilla/engine/jars/engine-develop-SNAPSHOT.jar"), anExistingFile());
        assertThat(new File("target/zpm/cache/io.aklivity.zilla/cog-tcp/jars/cog-tcp-develop-SNAPSHOT.jar"), anExistingFile());
        assertThat(new File("target/zpm/cache/io.aklivity.zilla/cog-tls/jars/cog-tls-develop-SNAPSHOT.jar"), anExistingFile());
        assertThat(new File("target/zpm/cache/org.agrona/agrona/jars/agrona-1.6.0.jar"), anExistingFile());
    }
}
