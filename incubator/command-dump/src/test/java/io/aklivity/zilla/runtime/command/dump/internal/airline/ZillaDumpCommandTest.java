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


import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ZillaDumpCommandTest
{
    private static String baseDir = "src/test/resources/io/aklivity/zilla/runtime/command/dump/internal";

    @TempDir
    private File tempDir;

    private ZillaDumpCommand command;

    @BeforeEach
    public void init()
    {
        command = new ZillaDumpCommand();
        command.directory = Paths.get(baseDir, "engine");
        command.verbose = true;
        command.continuous = false;
        command.output = Paths.get(tempDir.getPath(), "test.pcap");
        System.out.println(command.output);
    }

    @Test
    public void shouldDumpWithoutFilter() throws IOException
    {
        command.run();

        File[] files = tempDir.listFiles();
        assertEquals(1, files.length);

        File expectedDump = new File(baseDir + "/expected_dump_without_filter.pcap");
        byte[] expected = Files.readAllBytes(expectedDump.toPath());
        byte[] actual =  Files.readAllBytes(files[0].toPath());
        assertArrayEquals(expected, actual);
    }

    @Test
    public void shouldDumpWithKafkaFilter() throws IOException
    {
        command.bindings = singletonList("test.kafka0");
        command.run();

        File[] files = tempDir.listFiles();
        assertEquals(1, files.length);

        File expectedDump = new File(baseDir + "/expected_dump_with_kafka_filter.pcap");
        byte[] expected = Files.readAllBytes(expectedDump.toPath());
        byte[] actual =  Files.readAllBytes(files[0].toPath());
        assertArrayEquals(expected, actual);
    }
}
