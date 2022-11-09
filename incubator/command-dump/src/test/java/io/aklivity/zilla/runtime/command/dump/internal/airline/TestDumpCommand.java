/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.dump.internal.airline;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class TestDumpCommand
{

    private static String baseDir = "src/test/resources/io/aklivity/zilla/runtime/command/dump/internal";
    @TempDir
    private File tempDir;

    private ZillaDumpCommand dumpCommand;
    @BeforeEach
    public void init()
    {
        dumpCommand = new ZillaDumpCommand();
        Properties properties = new Properties();
        properties.put("zilla.engine.directory", baseDir + "/engine");
        final EngineConfiguration config = new EngineConfiguration(new Configuration(), properties);
        dumpCommand.directory = config.directory().toUri();

        dumpCommand.verbose = true;
        dumpCommand.affinity = -1;
        dumpCommand.continuous = false;
        dumpCommand.pcapLocation = Paths.get(tempDir.getPath(), "test.pcap").toUri();
    }
    @Test
    public void testDumpWithoutExtensionFilter() throws IOException
    {
        dumpCommand.run();

        File[] files = tempDir.listFiles();
        Assertions.assertEquals(1, files.length);
        File expectedDump = new File(baseDir + "/expected_dump_without_filter.pcap");
        byte[] expected = Files.readAllBytes(expectedDump.toPath());
        byte[] actual =  Files.readAllBytes(files[0].toPath());
        Assertions.assertTrue(Arrays.equals(expected, actual));
    }

    @Test
    public void testDumpWithKafkaExtensionFilter() throws IOException
    {
        List<String> extensionTypes = new ArrayList<>();
        extensionTypes.add("kafka0");
        dumpCommand.bindingNames = extensionTypes;
        dumpCommand.run();

        File[] files = tempDir.listFiles();
        Assertions.assertEquals(1, files.length);
        File expectedDump = new File(baseDir + "/expected_dump_with_kafka_filter.pcap");
        byte[] expected = Files.readAllBytes(expectedDump.toPath());
        byte[] actual =  Files.readAllBytes(files[0].toPath());
        Assertions.assertTrue(Arrays.equals(expected, actual));
    }
}
