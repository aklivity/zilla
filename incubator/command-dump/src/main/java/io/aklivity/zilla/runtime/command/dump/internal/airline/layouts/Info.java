/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.dump.internal.airline.layouts;

import static java.nio.ByteOrder.nativeOrder;

import java.io.IOException;
import java.nio.ByteBuffer;
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
        private Path directory;

        public Builder directory(
            Path directory)
        {
            this.directory = directory;
            return this;
        }

        public Info build()
        {
            Path path = directory.resolve("info");

            Info info = null;

            try
            {
                byte[] bytes = Files.readAllBytes(path);
                ByteBuffer byteBuf = ByteBuffer
                    .wrap(bytes, Long.BYTES, Long.BYTES + Integer.BYTES + Long.BYTES + Integer.BYTES)
                    .order(nativeOrder());

                Instant startTime = Instant.ofEpochSecond(byteBuf.getLong(), byteBuf.getInt());
                long startNanos = byteBuf.getLong();
                int workers = byteBuf.getInt();

                info = new Info(startTime, startNanos, workers);
            }
            catch (IOException ex)
            {
                System.out.printf("Error: %s is not readable\n", path);
                LangUtil.rethrowUnchecked(ex);
            }

            return info;
        }
    }
}
