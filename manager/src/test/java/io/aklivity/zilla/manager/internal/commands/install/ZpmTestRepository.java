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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.spi.ToolProvider;

final class ZpmTestRepository
{
    private final Path directory;

    ZpmTestRepository(
        Path directory)
    {
        this.directory = directory;
    }

    Path directory()
    {
        return directory;
    }

    ZpmTestRepository pom(
        String artifactId,
        String body) throws IOException
    {
        Path dir = Files.createDirectories(artifactDir(artifactId));
        Files.writeString(dir.resolve(String.format("%s-1.0.pom", artifactId)), String.format("""
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>test</groupId>
              <artifactId>%s</artifactId>
              <version>1.0</version>
            %s
            </project>
            """, artifactId, body), UTF_8);
        return this;
    }

    ZpmTestRepository jar(
        String artifactId) throws IOException
    {
        try (JarOutputStream out = newJar(artifactId))
        {
            out.flush();
        }
        return this;
    }

    ZpmTestRepository jar(
        String artifactId,
        String className,
        String source,
        String... classpathSources) throws IOException
    {
        Path sources = Files.createTempDirectory(directory, "sources");
        Path classes = Files.createTempDirectory(directory, "classes");

        Path sourceFile = writeSource(sources, className, source);
        String[] args = new String[classpathSources.length / 2 + 3];
        args[0] = "-d";
        args[1] = classes.toString();
        args[2] = sourceFile.toString();
        for (int i = 0; i < classpathSources.length; i += 2)
        {
            args[3 + i / 2] = writeSource(sources, classpathSources[i], classpathSources[i + 1]).toString();
        }
        ToolProvider.findFirst("javac").get().run(System.out, System.err, args);

        String classEntry = String.format("%s.class", className.replace('.', '/'));
        try (JarOutputStream out = newJar(artifactId))
        {
            out.putNextEntry(new JarEntry(classEntry));
            out.write(Files.readAllBytes(classes.resolve(classEntry)));
            out.closeEntry();
        }
        return this;
    }

    private Path writeSource(
        Path sources,
        String className,
        String source) throws IOException
    {
        Path file = sources.resolve(String.format("%s.java", className.replace('.', '/')));
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, UTF_8);
        return file;
    }

    private JarOutputStream newJar(
        String artifactId) throws IOException
    {
        Path dir = Files.createDirectories(artifactDir(artifactId));
        OutputStream out = Files.newOutputStream(dir.resolve(String.format("%s-1.0.jar", artifactId)));
        return new JarOutputStream(out, new Manifest());
    }

    private Path artifactDir(
        String artifactId)
    {
        return directory.resolve(String.format("test/%s/1.0", artifactId));
    }
}
