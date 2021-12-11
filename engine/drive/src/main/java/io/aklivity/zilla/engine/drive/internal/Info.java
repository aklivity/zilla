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
package io.aklivity.zilla.engine.drive.internal;

import static java.nio.ByteOrder.nativeOrder;
import static java.nio.file.StandardOpenOption.APPEND;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Info implements AutoCloseable
{
    private final int workers;
    private final Path info;

    public Info(
        Path directory,
        int workers)
    {
        this.info = directory.resolve("info");
        this.workers = workers;
    }

    public void reset()
    {
        try
        {
            Files.deleteIfExists(info);
            Files.createDirectories(info.getParent());
            Files.createFile(info);

            long processId = ProcessHandle.current().pid();
            try (SeekableByteChannel channel = Files.newByteChannel(info, APPEND))
            {
                ByteBuffer byteBuf = ByteBuffer
                        .wrap(new byte[Long.BYTES + Integer.BYTES])
                        .order(nativeOrder());
                byteBuf.putLong(processId);
                byteBuf.putInt(workers);
                byteBuf.flip();

                while (byteBuf.hasRemaining())
                {
                    channel.write(byteBuf);
                    Thread.onSpinWait();
                }
            }
            catch (IOException ex)
            {
                System.out.printf("Error: %s is not writeable\n", info);
            }
        }
        catch (IOException ex)
        {
            System.out.printf("Error: %s is not writeable\n", info);
        }
    }

    @Override
    public void close() throws Exception
    {
    }
}
