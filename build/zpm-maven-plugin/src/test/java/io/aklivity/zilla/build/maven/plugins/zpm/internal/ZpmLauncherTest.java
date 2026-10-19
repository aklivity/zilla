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
package io.aklivity.zilla.build.maven.plugins.zpm.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.spi.ToolProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ZpmLauncherTest
{
    private static final String ISOLATED_MAIN = """
        package io.aklivity.zilla.manager.internal;

        public final class ZpmMain
        {
            public static void main(String[] args) throws Exception
            {
                try
                {
                    ZpmMain.class.getClassLoader().loadClass("org.apache.maven.plugin.Mojo");
                    throw new IllegalStateException("plugin realm visible");
                }
                catch (ClassNotFoundException ex)
                {
                    Thread.currentThread().getContextClassLoader().loadClass(ZpmMain.class.getName());
                }
            }
        }
        """;

    private static final String FAILING_MAIN = """
        package io.aklivity.zilla.manager.internal;

        public final class ZpmMain
        {
            public static void main(String[] args)
            {
                java.nio.file.Path out = java.nio.file.Path.of(args[0]);
                try
                {
                    java.nio.file.Files.writeString(out, String.join(" ", args));
                }
                catch (java.io.IOException ex)
                {
                    throw new java.io.UncheckedIOException(ex);
                }
                throw new IllegalArgumentException("resolve failed");
            }
        }
        """;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shouldRunInIsolatedClassLoader() throws Exception
    {
        File manager = managerJar(ISOLATED_MAIN);

        new ZpmLauncher(manager).run(List.of("resolve"));
    }

    @Test
    public void shouldPropagateFailureCause() throws Exception
    {
        File manager = managerJar(FAILING_MAIN);
        Path out = folder.getRoot().toPath().resolve("args.txt");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> new ZpmLauncher(manager).run(List.of(out.toString(), "resolve")));

        assertThat(ex.getMessage(), containsString("resolve failed"));
        assertThat(Files.readString(out, UTF_8), equalTo(String.format("%s resolve", out)));
    }

    private File managerJar(
        String source) throws IOException
    {
        Path sources = folder.newFolder().toPath();
        Path classes = folder.newFolder().toPath();
        Path file = sources.resolve("ZpmMain.java");
        Files.writeString(file, source, UTF_8);
        ToolProvider.findFirst("javac").get().run(System.out, System.err,
            "-d", classes.toString(), file.toString());

        String entry = "io/aklivity/zilla/manager/internal/ZpmMain.class";
        File jar = folder.getRoot().toPath().resolve(String.format("manager-%d.jar", System.nanoTime())).toFile();
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath())))
        {
            out.putNextEntry(new JarEntry(entry));
            out.write(Files.readAllBytes(classes.resolve(entry)));
            out.closeEntry();
        }
        return jar;
    }
}
