/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal;

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

    // write mode: write the processId and number of workers to the file
    private Info(
        Path directory,
        int workers)
    {
        this.info = directory.resolve("info");
        this.workers = workers;
        reset();
    }

    // read mode: open the file and read the number of workers
    private Info(
        Path directory)
    {
        this.info = directory.resolve("info");
        this.workers = readWorkers();
    }

    public int workers()
    {
        return workers;
    }

    private void reset()
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

    private int readWorkers()
    {
        try
        {
            byte[] bytes = Files.readAllBytes(info);
            ByteBuffer byteBuf = ByteBuffer
                .wrap(bytes, Long.BYTES, Integer.BYTES)
                .order(nativeOrder());
            return byteBuf.getInt();
        }
        catch (IOException ex)
        {
            System.out.printf("Error: %s is not readable\n", info);
            return 0;
        }
    }

    @Override
    public void close() throws Exception
    {
    }

    public static final class Builder
    {
        private Path path;
        private int workerCount;
        private boolean readonly;

        public Builder path(
            Path path)
        {
            this.path = path;
            return this;
        }

        public Builder workerCount(
            int workerCount)
        {
            this.workerCount = workerCount;
            return this;
        }

        public Builder readonly(
            boolean readonly)
        {
            this.readonly = readonly;
            return this;
        }

        public Info build()
        {
            if (readonly)
            {
                return new Info(path);
            }
            else
            {
                return new Info(path, workerCount);
            }
        }
    }
}
