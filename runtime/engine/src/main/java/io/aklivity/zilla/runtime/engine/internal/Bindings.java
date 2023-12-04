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
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class Bindings implements AutoCloseable
{
    private static final int RECORD_SIZE = 5 * Long.BYTES;

    private final Path path;
    private final ByteChannel channel;
    private final Map<Long, long[]> bindings;

    private Bindings(
        Path path,
        ByteChannel channel,
        Map<Long, long[]> bindings)
    {
        this.path = path;
        this.channel = channel;
        this.bindings = bindings;
    }

    public void writeBindingInfo(
        BindingConfig binding)
    {
        ByteBuffer byteBuf = ByteBuffer.wrap(new byte[RECORD_SIZE]).order(nativeOrder());
        byteBuf.putLong(binding.id);
        byteBuf.putLong(binding.typeId);
        byteBuf.putLong(binding.kindId);
        byteBuf.putLong(binding.originTypeId);
        byteBuf.putLong(binding.routedTypeId);
        byteBuf.flip();
        while (byteBuf.hasRemaining())
        {
            try
            {
                channel.write(byteBuf);
            }
            catch (IOException ex)
            {
                System.out.printf("Error: %s is not writeable\n", path);
                LangUtil.rethrowUnchecked(ex);
            }
            Thread.onSpinWait();
        }
    }

    public Map<Long, long[]> bindings()
    {
        return bindings;
    }

    @Override
    public void close() throws Exception
    {
        if (channel != null)
        {
            channel.close();
        }
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static final class Builder
    {
        private Path path;
        private boolean readonly;

        public Builder directory(
            Path directory)
        {
            this.path = directory.resolve("bindings");
            return this;
        }

        public Builder readonly(
            boolean readonly)
        {
            this.readonly = readonly;
            return this;
        }

        public Bindings build()
        {
            ByteChannel channel = null;
            Map<Long, long[]> bindings = null;
            if (readonly)
            {
                try
                {
                    byte[] bytes = Files.readAllBytes(path);
                    ByteBuffer byteBuf = ByteBuffer
                        .wrap(bytes)
                        .order(nativeOrder());
                    bindings = new HashMap<>();
                    while (byteBuf.hasRemaining())
                    {
                        long bindingId = byteBuf.getLong();
                        long[] values = new long[4];
                        values[0] = byteBuf.getLong();
                        values[1] = byteBuf.getLong();
                        values[2] = byteBuf.getLong();
                        values[3] = byteBuf.getLong();
                        bindings.put(bindingId, values);
                    }
                }
                catch (IOException ex)
                {
                    System.out.printf("Error: %s is not readable\n", path);
                    LangUtil.rethrowUnchecked(ex);
                }
            }
            else
            {
                try
                {
                    Files.deleteIfExists(path);
                    Files.createDirectories(path.getParent());
                    Files.createFile(path);
                    channel = Files.newByteChannel(path, WRITE);
                }
                catch (IOException ex)
                {
                    System.out.printf("Error: %s is not writeable\n", path);
                    LangUtil.rethrowUnchecked(ex);
                }
            }
            return new Bindings(path, channel, bindings);
        }
    }
}
