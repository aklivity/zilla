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
package io.aklivity.zilla.runtime.binding.filesystem.internal.stream;

import static io.aklivity.zilla.runtime.binding.filesystem.internal.config.FileSystemSymbolicLinksConfig.IGNORE;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.filesystem.internal.FileSystemBinding;
import io.aklivity.zilla.runtime.binding.filesystem.internal.FileSystemConfiguration;
import io.aklivity.zilla.runtime.binding.filesystem.internal.config.FileSystemBindingConfig;
import io.aklivity.zilla.runtime.binding.filesystem.internal.config.FileSystemOptionsConfig;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.FileSystemCapabilities;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.FileSystemBeginExFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class FileSystemServerFactory implements FileSystemStreamFactory
{
    private static final LinkOption[] LINK_OPTIONS_NONE = new LinkOption[0];
    private static final LinkOption[] LINK_OPTIONS_NOFOLLOW = new LinkOption[] { NOFOLLOW_LINKS };

    private static final OctetsFW EMPTY_EXTENSION = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);

    private static final int READ_PAYLOAD_MASK = 1 << FileSystemCapabilities.READ_PAYLOAD.ordinal();

    private final BeginFW beginRO = new BeginFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final FileSystemBeginExFW beginExRO = new FileSystemBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();

    private final FileSystemBeginExFW.Builder beginExRW = new FileSystemBeginExFW.Builder();
    private final OctetsFW payloadRO = new OctetsFW();

    private final Long2ObjectHashMap<FileSystemBindingConfig> bindings;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer readBuffer;
    private final LongUnaryOperator supplyReplyId;
    private final int fileSystemTypeId;

    private final URI serverRoot;

    public FileSystemServerFactory(
        FileSystemConfiguration config,
        EngineContext context)
    {
        this.serverRoot = config.serverRoot();
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.readBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.supplyReplyId = context::supplyReplyId;
        this.fileSystemTypeId = context.supplyTypeId(FileSystemBinding.NAME);

        this.bindings = new Long2ObjectHashMap<>();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        FileSystemBindingConfig fsBinding = new FileSystemBindingConfig(binding);
        bindings.put(binding.id, fsBinding);
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer app)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routeId = begin.routeId();
        final long initialId = begin.streamId();
        final FileSystemBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);

        final FileSystemBindingConfig binding = bindings.get(routeId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final FileSystemOptionsConfig options = binding.options;
            final URI location = options.location;

            final URI resolvedRoot = serverRoot.resolve(location);
            final FileSystem fileSystem = options.fileSystem(resolvedRoot);
            final LinkOption[] symlinks = options.symlinks == IGNORE ? LINK_OPTIONS_NOFOLLOW : LINK_OPTIONS_NONE;

            final int capabilities = beginEx.capabilities();
            final String relativePath = beginEx.path().asString();
            final String resolvedPath = resolvedRoot.resolve(relativePath).getPath();

            final Path path = fileSystem.getPath(resolvedPath);

            try
            {
                String type = Files.probeContentType(path);
                BasicFileAttributeView view = Files.getFileAttributeView(path, BasicFileAttributeView.class, symlinks);
                BasicFileAttributes attributes = view.readAttributes();
                InputStream input = canReadPayload(capabilities) ? Files.newInputStream(path, symlinks) : null;

                return new FileSystemServer(app, routeId, initialId, attributes, type, input)::onAppMessage;
            }
            catch (IOException ex)
            {
                // reject
            }
        }

        return newStream;
    }

    private final class FileSystemServer
    {
        private final MessageConsumer app;
        private final long routeId;
        private final long initialId;
        private final long replyId;

        private final BasicFileAttributes attributes;
        private final String type;
        private final InputStream input;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialAuth;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBud;
        private long replyBytes;

        private int state;

        private FileSystemServer(
            MessageConsumer app,
            long routeId,
            long initialId,
            BasicFileAttributes attributes,
            String type,
            InputStream input)
        {
            this.app = app;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.attributes = attributes;
            this.type = type;
            this.input = input;
        }

        private void onAppMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onAppBegin(begin);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onAppEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAppAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long authorization = begin.authorization();
            final long traceId = begin.traceId();
            final FileSystemBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            initialAuth = authorization;

            assert initialAck <= initialSeq;

            state = FileSystemState.openingInitial(state);

            doAppWindow(traceId);

            final int capabilities = beginEx.capabilities();
            final String16FW path = beginEx.path();
            final long size = attributes.size();
            final long modifiedTime = attributes.lastModifiedTime().toMillis();

            Flyweight newBeginEx = beginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(fileSystemTypeId)
                .capabilities(capabilities)
                .path(path)
                .type(type)
                .payloadSize(size)
                .modifiedTime(modifiedTime)
                .build();

            doAppBegin(traceId, newBeginEx);

            flushAppData(traceId);
        }

        private void onAppEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            state = FileSystemState.closeInitial(state);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            state = FileSystemState.closeInitial(state);

            doAppAbort(traceId);
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final int padding = window.padding();
            final long budgetId = window.budgetId();
            final long traceId = window.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;
            replyBud = budgetId;

            assert replyAck <= replySeq;

            state = FileSystemState.openReply(state);

            flushAppData(traceId);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= replyAck;

            replyAck = acknowledge;

            assert replyAck <= replySeq;

            state = FileSystemState.closeReply(state);

            doAppReset(traceId);
        }

        private void doAppBegin(
            long traceId,
            Flyweight extension)
        {
            state = FileSystemState.openingReply(state);

            doBegin(app, routeId, replyId, replySeq, replyAck, replyMax, traceId, 0L, 0L, extension);
        }

        private void doAppData(
            long traceId,
            int reserved,
            OctetsFW payload)
        {
            final int length = payload != null ? payload.sizeof() : 0;
            assert reserved >= length + replyPad : String.format("%d >= %d", reserved, length + replyPad);

            doData(app, routeId, replyId, replySeq, replyAck, replyMax, traceId, 0L, replyBud,
                    reserved, payload, EMPTY_EXTENSION);

            replySeq += reserved;
            assert replySeq <= replyAck + replyMax;
        }

        private void doAppEnd(
            long traceId)
        {
            if (FileSystemState.replyOpening(state) && !FileSystemState.replyClosed(state))
            {
                state = FileSystemState.closeReply(state);
                doEnd(app, routeId, replyId, replySeq, replyAck, replyMax, traceId, 0L, EMPTY_EXTENSION);
            }
        }

        private void doAppAbort(
            long traceId)
        {
            if (FileSystemState.replyOpening(state) && !FileSystemState.replyClosed(state))
            {
                state = FileSystemState.closeReply(state);
                doAbort(app, routeId, replyId, replySeq, replyAck,
                        replyMax, traceId, 0L, EMPTY_EXTENSION);
            }
        }

        private void doAppReset(
            long traceId)
        {
            if (FileSystemState.initialOpening(state) && !FileSystemState.initialClosed(state))
            {
                state = FileSystemState.closeInitial(state);

                doReset(app, routeId, initialId, initialSeq, initialAck, initialMax, traceId, 0L);
            }
        }

        private void doAppWindow(
            long traceId)
        {
            state = FileSystemState.openInitial(state);

            doWindow(app, routeId, initialId, initialSeq, initialAck, initialMax, traceId,
                     initialAuth, 0L, initialPad);
        }

        private void flushAppData(
            long traceId)
        {
            int replyWin = replyMax - (int)(replySeq - replyAck) - replyPad;

            if (replyWin > 0)
            {
                boolean replyClosable = input == null;

                if (input != null)
                {
                    try
                    {
                        final byte[] readArray = readBuffer.byteArray();
                        int bytesRead = input.read(readArray, 0, Math.min(readArray.length, replyWin));

                        if (bytesRead != -1)
                        {
                            OctetsFW payload = payloadRO.wrap(readBuffer, 0, bytesRead);
                            int reserved = bytesRead + replyPad;

                            doAppData(traceId, reserved, payload);

                            replyBytes += bytesRead;
                            replyClosable = replyBytes == attributes.size();
                        }
                        else
                        {
                            input.close();
                        }
                    }
                    catch (IOException ex)
                    {
                        doAppAbort(traceId);
                    }
                }

                if (replyClosable)
                {
                    doAppEnd(traceId);
                }
            }
        }
    }

    private void doBegin(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                                     .routeId(routeId)
                                     .streamId(streamId)
                                     .sequence(sequence)
                                     .acknowledge(acknowledge)
                                     .maximum(maximum)
                                     .traceId(traceId)
                                     .authorization(authorization)
                                     .affinity(affinity)
                                     .extension(extension.buffer(), extension.offset(), extension.sizeof())
                                     .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        OctetsFW payload,
        Flyweight extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                                  .routeId(routeId)
                                  .streamId(streamId)
                                  .sequence(sequence)
                                  .acknowledge(acknowledge)
                                  .maximum(maximum)
                                  .traceId(traceId)
                                  .authorization(authorization)
                                  .budgetId(budgetId)
                                  .reserved(reserved)
                                  .payload(payload)
                                  .extension(extension.buffer(), extension.offset(), extension.sizeof())
                                  .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                               .routeId(routeId)
                               .streamId(streamId)
                               .sequence(sequence)
                               .acknowledge(acknowledge)
                               .maximum(maximum)
                               .traceId(traceId)
                               .authorization(authorization)
                               .extension(extension.buffer(), extension.offset(), extension.sizeof())
                               .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                                     .routeId(routeId)
                                     .streamId(streamId)
                                     .sequence(sequence)
                                     .acknowledge(acknowledge)
                                     .maximum(maximum)
                                     .traceId(traceId)
                                     .authorization(authorization)
                                     .extension(extension.buffer(), extension.offset(), extension.sizeof())
                                     .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                                     .routeId(routeId)
                                     .streamId(streamId)
                                     .sequence(sequence)
                                     .acknowledge(acknowledge)
                                     .maximum(maximum)
                                     .traceId(traceId)
                                     .authorization(authorization)
                                     .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doWindow(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int padding)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                                        .routeId(routeId)
                                        .streamId(streamId)
                                        .sequence(sequence)
                                        .acknowledge(acknowledge)
                                        .maximum(maximum)
                                        .traceId(traceId)
                                        .authorization(authorization)
                                        .budgetId(budgetId)
                                        .padding(padding)
                                        .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private boolean canReadPayload(
        int capabilities)
    {
        return (capabilities & READ_PAYLOAD_MASK) != 0;
    }
}
