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
package io.aklivity.zilla.runtime.engine.internal;

import static java.nio.ByteOrder.nativeOrder;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.time.Instant.ofEpochSecond;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.agrona.LangUtil;

public final class Info implements AutoCloseable
{
    private final Instant startTime;
    private final long startNanos;
    private final int workers;

    private Info(
        Instant startTime,
        long startNanos,
        int workers)
    {
        this.workers = workers;
        this.startTime = startTime;
        this.startNanos = startNanos;
    }

    public int workers()
    {
        return workers;
    }

    public Instant startTime()
    {
        return startTime;
    }

    public long startNanos()
    {
        return startNanos;
    }

    @Override
    public void close() throws Exception
    {
    }

    public static final class Builder
    {
        private static final int OFFSET_PROCESS_ID = 0;
        private static final int LIMIT_PROCESS_ID = OFFSET_PROCESS_ID + Long.BYTES;

        private static final int OFFSET_START_TIME_SECONDS = LIMIT_PROCESS_ID;
        private static final int LIMIT_START_TIME_SECONDS = OFFSET_START_TIME_SECONDS + Long.BYTES;

        private static final int OFFSET_START_TIME_NANOS = LIMIT_START_TIME_SECONDS;
        private static final int LIMIT_START_TIME_NANOS = OFFSET_START_TIME_NANOS + Integer.BYTES;

        private static final int OFFSET_START_NANOS = LIMIT_START_TIME_NANOS;
        private static final int LIMIT_START_NANOS = OFFSET_START_NANOS + Long.BYTES;

        private static final int OFFSET_WORKERS = LIMIT_START_NANOS;
        private static final int LIMIT_WORKERS = OFFSET_WORKERS + Integer.BYTES;

        private static final int SIZEOF_INFO = LIMIT_WORKERS;

        private Path directory;
        private int workers;
        private boolean readonly;

        public Builder directory(
            Path directory)
        {
            this.directory = directory;
            return this;
        }

        public Builder workers(
            int workers)
        {
            this.workers = workers;
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
            Path path = directory.resolve("info");

            Info info = null;

            if (readonly)
            {
                try
                {
                    byte[] bytes = Files.readAllBytes(path);
                    ByteBuffer byteBuf = ByteBuffer
                        .wrap(bytes, Long.BYTES, SIZEOF_INFO)
                        .order(nativeOrder());

                    Instant startTime =
                        ofEpochSecond(byteBuf.getLong(), byteBuf.getInt());
                    long startNanos = byteBuf.getLong();
                    int workers = byteBuf.getInt();

                    info = new Info(startTime, startNanos, workers);
                }
                catch (IOException ex)
                {
                    System.out.printf("Error: %s is not readable\n", path);
                    LangUtil.rethrowUnchecked(ex);
                }
            }
            else
            {
                info = new Info(Instant.now(), System.nanoTime(), workers);

                try
                {
                    Files.deleteIfExists(path);
                    Files.createDirectories(path.getParent());
                    Files.createFile(path);

                    long processId = ProcessHandle.current().pid();
                    try (SeekableByteChannel channel = Files.newByteChannel(path, APPEND))
                    {
                        ByteBuffer byteBuf = ByteBuffer
                                .wrap(new byte[Long.BYTES + SIZEOF_INFO])
                                .order(nativeOrder());
                        byteBuf.putLong(processId);
                        byteBuf.putLong(info.startTime.getEpochSecond());
                        byteBuf.putInt(info.startTime.getNano());
                        byteBuf.putLong(info.startNanos);
                        byteBuf.putInt(info.workers);
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
                    System.out.printf("Error: %s is not writeable\n", path);
                    LangUtil.rethrowUnchecked(ex);
                }
            }

            return info;
        }
    }
}
