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
package io.aklivity.zilla.runtime.cmd.tune.internal.command;

import static java.nio.ByteOrder.nativeOrder;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongPredicate;

import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Command;

import io.aklivity.zilla.runtime.cli.ZillaCommand;
import io.aklivity.zilla.runtime.cmd.tune.internal.command.labels.LabelManager;

@Command(name = "tune", description = "Tune engine")
public final class ZillaTuneCommand extends ZillaCommand
{
    @Arguments(title = {"name, value"})
    public List<String> args;

    private final LabelManager labels;

    private final Path directory;

    public ZillaTuneCommand()
    {
        this.directory = Paths.get(".zilla", "engine");
        this.labels = new LabelManager(directory);
    }

    @Override
    public void run()
    {
        Path tuning = directory.resolve("tuning");

        if (Files.exists(tuning))
        {
            try
            {
                int workers = Long.SIZE;

                Path info = directory.resolve("info");
                if (Files.exists(info))
                {
                    ByteBuffer byteBuf = ByteBuffer
                            .wrap(Files.readAllBytes(info))
                            .order(nativeOrder());
                    workers = byteBuf.getInt(Long.BYTES);
                }

                LongPredicate filter = id -> true;
                Consumer<ByteBuffer> updater = buf -> {};

                if (args != null && args.size() >= 1)
                {
                    String nameEqualsValue = args.get(0);
                    int equalsAt = nameEqualsValue.indexOf('=');
                    String name = equalsAt != -1 ? nameEqualsValue.substring(0, equalsAt) : nameEqualsValue;
                    String value = equalsAt != -1 ? nameEqualsValue.substring(equalsAt + 1) : null;

                    int dotAt = name.indexOf('.');
                    String namespace = dotAt != -1 ? name.substring(0, dotAt) : "default";
                    String binding = dotAt != -1 ? name.substring(dotAt + 1) : name;

                    int namespaceId = labels.lookupLabelId(namespace);
                    int bindingId = labels.lookupLabelId(binding);

                    long routeId =
                        (long) namespaceId << Integer.SIZE |
                        (long) bindingId << 0;

                    filter = id -> id == routeId;

                    if (value != null)
                    {
                        final long mask = Long.decode(value);
                        updater = buf -> buf.putLong(buf.position() - Long.BYTES, mask);
                    }
                }

                try (FileChannel channel = FileChannel.open(tuning, READ, WRITE))
                {
                    MappedByteBuffer byteBuf = channel.map(MapMode.READ_WRITE, 0, Files.size(tuning));
                    byteBuf.order(nativeOrder());

                    while (byteBuf.remaining() >= Long.BYTES + Long.BYTES)
                    {
                        long routeId = byteBuf.getLong();
                        long mask = byteBuf.getLong();

                        if (filter.test(routeId))
                        {
                            updater.accept(byteBuf);
                            mask = byteBuf.getLong(byteBuf.position() - Long.BYTES);

                            int namespaceId = (int)(routeId >> 32) & 0xffff_ffff;
                            int bindingId = (int)(routeId >> 0) & 0xffff_ffff;

                            String namespace = labels.lookupLabel(namespaceId);
                            String binding = labels.lookupLabel(bindingId);

                            String format = String.format("%%%ds", workers);
                            String maskBits = new StringBuilder()
                                .append(String.format(format, Long.toBinaryString(mask))
                                        .replace(' ', '.')
                                        .replace('0', '.')
                                        .replace('1', 'x'))
                                .reverse()
                                .toString();

                            System.out.printf("%s  %s.%s\n", maskBits, namespace, binding);
                        }
                    }
                }
            }
            catch (IOException ex)
            {
                System.out.printf("Error: %s is not readable\n", tuning);
            }
        }
    }
}
