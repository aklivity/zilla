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
package io.aklivity.zilla.runtime.engine.diagnostic;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public abstract class EngineDiagnosticsTask implements Runnable
{
    public static EngineDiagnosticsTask of(
        EngineConfiguration config)
    {
        Path diagnosticsDir = config.diagnosticsDirectory();
        return diagnosticsDir != null
            ? new EngineDiagnosticsTaskCapture(config.directory(), diagnosticsDir)
            : new EngineDiagnosticsTaskNoOp();
    }

    private static final class EngineDiagnosticsTaskCapture extends EngineDiagnosticsTask
    {
        private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

        private final Path engineDirectory;
        private final Path diagnosticsDirectory;

        private EngineDiagnosticsTaskCapture(
            Path engineDirectory,
            Path diagnosticsDirectory)
        {
            this.engineDirectory = engineDirectory;
            this.diagnosticsDirectory = diagnosticsDirectory;
        }

        @Override
        public void run()
        {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String snapshotName = String.format("zilla-diagnostics-%s", timestamp);
            Path snapshotDir = diagnosticsDirectory.resolve(snapshotName);
            Path archivePath = diagnosticsDirectory.resolve(snapshotName + ".zip");

            try
            {
                Files.createDirectories(diagnosticsDirectory);

                Files.walkFileTree(engineDirectory, new SimpleFileVisitor<>()
                {
                    @Override
                    public FileVisitResult visitFile(
                        Path file,
                        BasicFileAttributes attrs)
                        throws IOException
                    {
                        Path relative = engineDirectory.relativize(file);
                        Path dest = snapshotDir.resolve("engine").resolve(relative);
                        Files.createDirectories(dest.getParent());
                        Files.copy(file, dest);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(
                        Path file,
                        IOException ex)
                    {
                        return FileVisitResult.CONTINUE;
                    }
                });

                try (OutputStream out = Files.newOutputStream(archivePath);
                     ZipOutputStream zipOut = new ZipOutputStream(out))
                {
                    Files.walkFileTree(snapshotDir, new SimpleFileVisitor<>()
                    {
                        @Override
                        public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attrs)
                            throws IOException
                        {
                            Path relative = snapshotDir.relativize(file);
                            zipOut.putNextEntry(new ZipEntry(relative.toString()));
                            Files.copy(file, zipOut);
                            zipOut.closeEntry();
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }

                try (Stream<Path> stream = Files.walk(snapshotDir))
                {
                    stream.sorted(Comparator.reverseOrder())
                          .forEach(p ->
                          {
                              try
                              {
                                  Files.delete(p);
                              }
                              catch (IOException ex)
                              {
                                  ex.printStackTrace();
                              }
                          });
                }

                System.out.printf("Diagnostics captured: %s%n", archivePath);
            }
            catch (IOException ex)
            {
                System.err.printf("Failed to capture diagnostics: %s%n", ex.getMessage());
            }
        }
    }

    private static final class EngineDiagnosticsTaskNoOp extends EngineDiagnosticsTask
    {
        @Override
        public void run()
        {
        }
    }
}
