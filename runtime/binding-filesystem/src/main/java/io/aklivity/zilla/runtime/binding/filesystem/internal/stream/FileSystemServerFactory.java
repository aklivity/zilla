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
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.time.Instant.now;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.filesystem.internal.FileSystemBinding;
import io.aklivity.zilla.runtime.binding.filesystem.internal.FileSystemConfiguration;
import io.aklivity.zilla.runtime.binding.filesystem.internal.FileSystemWatcher;
import io.aklivity.zilla.runtime.binding.filesystem.internal.config.FileSystemBindingConfig;
import io.aklivity.zilla.runtime.binding.filesystem.internal.config.FileSystemOptionsConfig;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.FileSystemCapabilities;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.FileSystemBeginExFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class FileSystemServerFactory implements FileSystemStreamFactory
{
    private static final LinkOption[] LINK_OPTIONS_NONE = new LinkOption[0];
    private static final LinkOption[] LINK_OPTIONS_NOFOLLOW = new LinkOption[] { NOFOLLOW_LINKS };

    private static final OctetsFW EMPTY_EXTENSION = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);

    private static final int READ_PAYLOAD_MASK = 1 << FileSystemCapabilities.READ_PAYLOAD.ordinal();
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final int TIMEOUT_EXPIRED_SIGNAL_ID = 0;
    public static final int FILE_CHANGED_SIGNAL_ID = 1;

    private final BeginFW beginRO = new BeginFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();

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
    private final BufferPool bufferPool;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer readBuffer;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final LongUnaryOperator supplyReplyId;
    private final int fileSystemTypeId;
    private final URI serverRoot;
    private final MessageDigest md5;
    private final Signaler signaler;
    private final Supplier<FileSystemWatcher> fileSystemWatcherSupplier;

    private FileSystemWatcher fileSystemWatcher;

    public FileSystemServerFactory(
        FileSystemConfiguration config,
        EngineContext context,
        Supplier<FileSystemWatcher> fileSystemWatcherSupplier)
    {
        this.bufferPool = context.bufferPool();
        this.serverRoot = config.serverRoot();
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.readBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.supplyDebitor = context::supplyDebitor;
        this.supplyReplyId = context::supplyReplyId;
        this.fileSystemTypeId = context.supplyTypeId(FileSystemBinding.NAME);
        this.bindings = new Long2ObjectHashMap<>();
        this.signaler = context.signaler();
        this.md5 = initMessageDigest("MD5");
        this.fileSystemWatcherSupplier = fileSystemWatcherSupplier;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        FileSystemBindingConfig fsBinding = new FileSystemBindingConfig(binding);
        bindings.put(binding.id, fsBinding);
        fileSystemWatcher = fileSystemWatcherSupplier.get();
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
            final String tag = beginEx.tag().asString();
            try
            {
                if (Files.exists(Paths.get(resolvedPath), symlinks))
                {
                    String type = probeContentTypeOrDefault(path);
                    newStream = new FileSystemServer(app, routeId, initialId, type, symlinks,
                        relativePath, resolvedPath, capabilities, tag)::onAppMessage;
                }
            }
            catch (IOException ex)
            {
                // reject
            }
        }

        return newStream;
    }


    private static MessageDigest initMessageDigest(
        String algorithm)
    {
        MessageDigest messageDigest = null;
        try
        {
            messageDigest = MessageDigest.getInstance(algorithm);
        }
        catch (NoSuchAlgorithmException ex)
        {
            rethrowUnchecked(ex);
        }
        return messageDigest;
    }
    private String probeContentTypeOrDefault(
        Path path) throws IOException
    {
        final String contentType = Files.probeContentType(path);
        return contentType != null ? contentType : DEFAULT_CONTENT_TYPE;
    }

    private final class FileSystemServer
    {
        private final MessageConsumer app;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final String type;
        private final String relativePath;
        private final Path resolvedPath;
        private final int capabilities;
        private final String tag;
        private final LinkOption[] symlinks;
        private FileSystemWatcher.WatchedFile watchedFile;
        private BasicFileAttributes attributes;
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
        private BudgetDebitor replyDeb;
        private long replyDebIndex = NO_DEBITOR_INDEX;

        private int state;

        private FileSystemServer(
            MessageConsumer app,
            long routeId,
            long initialId,
            String type,
            LinkOption[] symlinks,
            String relativePath,
            String resolvedPath,
            int capabilities,
            String tag)
        {
            this.app = app;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.type = type;
            this.symlinks = symlinks;
            this.relativePath = relativePath;
            this.resolvedPath = Paths.get(resolvedPath);
            this.capabilities = capabilities;
            this.tag = tag;
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
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onAppSignal(signal);
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
            String currentTag = calculateTag();
            if (tag == null || tag.isEmpty() || !tag.equals(currentTag))
            {
                Flyweight replyBeginEx = getReplyBeginEx(currentTag);
                doAppBegin(traceId, replyBeginEx);
                flushAppData(traceId);
            }
            else
            {
                long timeoutAt = now().toEpochMilli() + beginEx.timeout();
                long timeoutId = signaler.signalAt(timeoutAt, routeId, replyId, TIMEOUT_EXPIRED_SIGNAL_ID, 0);
                watchedFile = new FileSystemWatcher.WatchedFile(
                    resolvedPath, symlinks, this::calculateTag, tag, timeoutId, routeId, replyId);
                fileSystemWatcher.watch(watchedFile);
            }
        }

        private Flyweight getReplyBeginEx(String newTag)
        {
            int capabilities = tag == null || !tag.equals(newTag) ? this.capabilities : 0;
            return getReplyBeginEx(newTag, capabilities);
        }

        private Flyweight getReplyBeginEx(
            String newTag,
            int capabilities)
        {
            attributes = getAttributes();
            long size = 0L;
            if (attributes != null)
            {
                size = attributes.size();
            }
            Flyweight replyBeginEx = beginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(fileSystemTypeId)
                .capabilities(capabilities)
                .path(relativePath)
                .type(type)
                .payloadSize(size)
                .tag(newTag)
                .build();
            return replyBeginEx;
        }

        private BasicFileAttributes getAttributes()
        {
            BasicFileAttributes attributes = null;
            try
            {
                BasicFileAttributeView view = Files.getFileAttributeView(resolvedPath, BasicFileAttributeView.class, symlinks);
                attributes = view.readAttributes();
            }
            catch (IOException ex)
            {
                // reject
            }
            return attributes;
        }

        private String calculateTag()
        {
            String newTag = null;
            try
            {
                InputStream input = getInputStream();
                if (input != null)
                {
                    final byte[] readArray = readBuffer.byteArray();
                    int bytesRead = input.read(readArray, 0, readArray.length);
                    byte[] content = new byte[bytesRead];
                    readBuffer.getBytes(0, content, 0, bytesRead);
                    newTag = calculateHash(content);
                }
            }
            catch (IOException ex)
            {
                // reject
            }
            return newTag;
        }

        private InputStream getInputStream()
        {
            InputStream input = null;
            try
            {
                input = canReadPayload(capabilities) ? Files.newInputStream(resolvedPath, symlinks) : null;
            }
            catch (IOException ex)
            {
                // reject
            }
            return input;
        }

        private String calculateHash(
            byte[] content)
        {

            byte[] hash = md5.digest(content);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b: hash)
            {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
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

            if (FileSystemState.replyOpening(state) && !FileSystemState.replyOpened(state))
            {
                state = FileSystemState.openReply(state);

                if (replyBud != 0L && replyDebIndex == NO_DEBITOR_INDEX)
                {
                    replyDeb = supplyDebitor.apply(budgetId);
                    replyDebIndex = replyDeb.acquire(budgetId, replyId, this::flushAppData);
                }
                flushAppData(traceId);
            }
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

        private void onAppSignal(
            SignalFW signal)
        {
            long traceId = signal.traceId();
            switch (signal.signalId())
            {
            case FILE_CHANGED_SIGNAL_ID:
                flushAppData(traceId);
                break;
            case TIMEOUT_EXPIRED_SIGNAL_ID:
                Flyweight replyBeginEx = getReplyBeginEx(tag, 0);
                doAppBegin(traceId, replyBeginEx);
                doAppEnd(traceId);
                break;
            default:
                break;
            }
            fileSystemWatcher.unregister(watchedFile);
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
            final int replyNoAck = (int)(replySeq - replyAck);
            final int replyWin = replyMax - replyNoAck - replyPad;
            if (!FileSystemState.replyOpening(state))
            {
                String newTag = calculateTag();
                Flyweight replyBeginEx = getReplyBeginEx(newTag);
                doAppBegin(traceId, replyBeginEx);
            }
            if (replyWin > 0)
            {
                InputStream input = getInputStream();
                boolean replyClosable = input == null;
                if (input != null)
                {
                    try
                    {
                        int reserved = Math.min(replyWin, input.available() + replyPad);
                        int length = Math.max(reserved - replyPad, 0);

                        if (length > 0 && replyDebIndex != NO_DEBITOR_INDEX && replyDeb != null)
                        {
                            final int minimum = Math.min(bufferPool.slotCapacity(), reserved); // TODO: fragmentation
                            reserved = replyDeb.claim(traceId, replyDebIndex, replyId, minimum, reserved, 0);
                            length = Math.max(reserved - replyPad, 0);
                        }

                        if (length > 0)
                        {
                            final byte[] readArray = readBuffer.byteArray();
                            int bytesRead = input.read(readArray, 0, Math.min(readArray.length, length));
                            if (bytesRead != -1)
                            {
                                OctetsFW payload = payloadRO.wrap(readBuffer, 0, bytesRead);

                                doAppData(traceId, reserved, payload);

                                replyBytes += bytesRead;
                                replyClosable = replyBytes == attributes.size();
                            }
                        }
                        if (replyClosable)
                        {
                            input.close();
                            doAppEnd(traceId);
                        }
                    }
                    catch (IOException ex)
                    {
                        doAppAbort(traceId);
                    }
                }
                else
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
