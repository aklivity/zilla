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

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIAGNOSTICS_DIRECTORY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class EngineDiagnosticsTaskTest
{
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void shouldReturnNoOpWhenDiagnosticsDirectoryNotConfigured()
    {
        Properties props = new Properties();
        props.setProperty(ENGINE_DIRECTORY.name(), temp.getRoot().getAbsolutePath());
        EngineConfiguration config = new EngineConfiguration(props);

        EngineDiagnosticsTask task = EngineDiagnosticsTask.of(config);
        task.run();
    }

    @Test
    public void shouldCaptureEngineFilesIntoZip() throws IOException
    {
        Path engineDir = temp.newFolder("engine").toPath();
        Path diagnosticsDir = temp.newFolder("diagnostics").toPath();

        Files.writeString(engineDir.resolve("worker.0"), "data");
        Files.writeString(engineDir.resolve("worker.1"), "data");

        Properties props = new Properties();
        props.setProperty(ENGINE_DIRECTORY.name(), engineDir.toString());
        props.setProperty(ENGINE_DIAGNOSTICS_DIRECTORY.name(), diagnosticsDir.toString());
        EngineConfiguration config = new EngineConfiguration(props);

        EngineDiagnosticsTask.of(config).run();

        try (Stream<Path> files = Files.list(diagnosticsDir))
        {
            Path zip = files.filter(p -> p.toString().endsWith(".zip")).findFirst().orElseThrow();
            try (ZipFile zipFile = new ZipFile(zip.toFile()))
            {
                assertTrue(zipFile.getEntry("engine/worker.0") != null);
                assertTrue(zipFile.getEntry("engine/worker.1") != null);
            }
        }
    }

    @Test
    public void shouldDeleteSnapshotDirectoryAfterZipping() throws IOException
    {
        Path engineDir = temp.newFolder("engine").toPath();
        Path diagnosticsDir = temp.newFolder("diagnostics").toPath();

        Files.writeString(engineDir.resolve("worker.0"), "data");

        Properties props = new Properties();
        props.setProperty(ENGINE_DIRECTORY.name(), engineDir.toString());
        props.setProperty(ENGINE_DIAGNOSTICS_DIRECTORY.name(), diagnosticsDir.toString());
        EngineConfiguration config = new EngineConfiguration(props);

        EngineDiagnosticsTask.of(config).run();

        try (Stream<Path> files = Files.list(diagnosticsDir))
        {
            assertFalse(files.anyMatch(p -> Files.isDirectory(p)));
        }
    }

    @Test
    public void shouldPreserveSubdirectoryStructureInZip() throws IOException
    {
        Path engineDir = temp.newFolder("engine").toPath();
        Path diagnosticsDir = temp.newFolder("diagnostics").toPath();
        Path subDir = engineDir.resolve("sub");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("file.bin"), "data");

        Properties props = new Properties();
        props.setProperty(ENGINE_DIRECTORY.name(), engineDir.toString());
        props.setProperty(ENGINE_DIAGNOSTICS_DIRECTORY.name(), diagnosticsDir.toString());
        EngineConfiguration config = new EngineConfiguration(props);

        EngineDiagnosticsTask.of(config).run();

        try (Stream<Path> files = Files.list(diagnosticsDir))
        {
            Path zip = files.filter(p -> p.toString().endsWith(".zip")).findFirst().orElseThrow();
            try (ZipFile zipFile = new ZipFile(zip.toFile()))
            {
                assertTrue(zipFile.getEntry("engine/sub/file.bin") != null);
            }
        }
    }
}
