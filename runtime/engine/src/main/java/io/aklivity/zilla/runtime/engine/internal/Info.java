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

import org.agrona.LangUtil;

public final class Info implements AutoCloseable
{
    private final Path path;

    private int workerCount;

    private Info(
        Path directory)
    {
        this.path = directory.resolve("info");
    }

    public int workerCount()
    {
        return workerCount;
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
            Info info = new Info(path);
            if (readonly)
            {
                try
                {
                    byte[] bytes = Files.readAllBytes(info.path);
                    ByteBuffer byteBuf = ByteBuffer
                        .wrap(bytes, Long.BYTES, Integer.BYTES)
                        .order(nativeOrder());
                    info.workerCount = byteBuf.getInt();
                }
                catch (IOException ex)
                {
                    System.out.printf("Error: %s is not readable\n", info.path);
                    LangUtil.rethrowUnchecked(ex);
                }
            }
            else
            {
                info.workerCount = workerCount;
                try
                {
                    Files.deleteIfExists(info.path);
                    Files.createDirectories(info.path.getParent());
                    Files.createFile(info.path);

                    long processId = ProcessHandle.current().pid();
                    try (SeekableByteChannel channel = Files.newByteChannel(info.path, APPEND))
                    {
                        ByteBuffer byteBuf = ByteBuffer
                                .wrap(new byte[Long.BYTES + Integer.BYTES])
                                .order(nativeOrder());
                        byteBuf.putLong(processId);
                        byteBuf.putInt(workerCount);
                        byteBuf.flip();

                        while (byteBuf.hasRemaining())
                        {
                            channel.write(byteBuf);
                            Thread.onSpinWait();
                        }
                    }
                }
                catch (IOException ex)
                {
                    System.out.printf("Error: %s is not writeable\n", info.path);
                    LangUtil.rethrowUnchecked(ex);
                }
            }
            return info;
        }
    }
}
