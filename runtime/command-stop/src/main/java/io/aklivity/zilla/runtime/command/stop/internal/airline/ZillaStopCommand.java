/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.command.stop.internal.airline;

import static java.nio.ByteOrder.nativeOrder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.github.rvesse.airline.annotations.Command;

import io.aklivity.zilla.runtime.command.ZillaCommand;

@Command(name = "stop", description = "Stop engine")
public final class ZillaStopCommand extends ZillaCommand
{
    private final Path directory;

    public ZillaStopCommand()
    {
        this.directory = Paths.get(".zilla", "engine");
    }

    @Override
    public void run()
    {
        Path info = directory.resolve("info");

        try
        {
            if (Files.exists(info))
            {
                ByteBuffer byteBuf = ByteBuffer
                        .wrap(Files.readAllBytes(info))
                        .order(nativeOrder());

                long pid = byteBuf.getLong(0);
                ProcessHandle.of(pid)
                    .ifPresent(ProcessHandle::destroy);
            }
        }
        catch (IOException ex)
        {
            System.out.printf("Error: %s is not readable\n", info);
        }
    }
}
