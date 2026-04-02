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
package io.aklivity.zilla.runtime.engine;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class EngineDiagnosticsTask implements Runnable
{
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path engineDirectory;
    private final Path diagnosticsDirectory;
    private final AtomicBoolean capturing = new AtomicBoolean();

    public EngineDiagnosticsTask(
        Path engineDirectory,
        Path diagnosticsDirectory)
    {
        this.engineDirectory = engineDirectory;
        this.diagnosticsDirectory = diagnosticsDirectory;
    }

    @Override
    public void run()
    {
        if (!capturing.compareAndSet(false, true))
        {
            return;
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String archiveName = String.format("zilla-diagnostics-%s.zip", timestamp);
        Path archivePath = diagnosticsDirectory.resolve(archiveName);

        try
        {
            Files.createDirectories(diagnosticsDirectory);

            try (OutputStream out = Files.newOutputStream(archivePath);
                 ZipOutputStream zipOut = new ZipOutputStream(out))
            {
                Files.walkFileTree(engineDirectory, new SimpleFileVisitor<>()
                {
                    @Override
                    public FileVisitResult visitFile(
                        Path file,
                        BasicFileAttributes attrs)
                        throws IOException
                    {
                        Path relative = engineDirectory.relativize(file);
                        zipOut.putNextEntry(new ZipEntry(relative.toString()));
                        Files.copy(file, zipOut);
                        zipOut.closeEntry();
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
            }

            System.out.printf("Diagnostics captured: %s%n", archivePath);
        }
        catch (IOException ex)
        {
            System.err.printf("Failed to capture diagnostics: %s%n", ex.getMessage());
        }
        finally
        {
            capturing.set(false);
        }
    }
}
