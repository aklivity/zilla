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
package io.aklivity.zilla.runtime.binding.filesystem.internal.stream;

import static io.aklivity.zilla.runtime.binding.filesystem.config.FileSystemSymbolicLinksConfig.IGNORE;
import static io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.FileSystemError.DIRECTORY_EXISTS;
import static io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.FileSystemError.DIRECTORY_NOT_EMPTY;
import static io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.FileSystemError.DIRECTORY_NOT_FOUND;
import static io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.FileSystemError.FILE_EXISTS;
import static io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.FileSystemError.FILE_MODIFIED;
import static io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.FileSystemError.FILE_NOT_FOUND;
import static io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.FileSystemError.FILE_TAG_MISSING;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.time.Instant.now;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.ByteChannel;
import java.nio.charset.StandardCharsets;
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
import java.util.stream.Stream;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.filesystem.config.FileSystemOptionsConfig;
import io.aklivity.zilla.runtime.binding.filesystem.internal.FileSystemBinding;
import io.aklivity.zilla.runtime.binding.filesystem.internal.FileSystemConfiguration;
import io.aklivity.zilla.runtime.binding.filesystem.internal.FileSystemWatcher;
import io.aklivity.zilla.runtime.binding.filesystem.internal.config.FileSystemBindingConfig;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.FileSystemCapabilities;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.FileSystemBeginExFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.FileSystemError;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.FileSystemErrorFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.FileSystemResetExFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.filesystem.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.binding.filesystem.model.FileSystemObject;
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

    private static final int READ_FILE_MASK = 1 << FileSystemCapabilities.READ_FILE.ordinal();
    private static final int WRITE_FILE_MASK = 1 << FileSystemCapabilities.WRITE_FILE.ordinal();
    private static final int CREATE_FILE_MASK = 1 << FileSystemCapabilities.CREATE_FILE.ordinal();
    private static final int DELETE_FILE_MASK = 1 << FileSystemCapabilities.DELETE_FILE.ordinal();
    private static final int READ_DIRECTORY_MASK = 1 << FileSystemCapabilities.READ_DIRECTORY.ordinal();
    private static final int CREATE_DIRECTORY_MASK = 1 << FileSystemCapabilities.CREATE_DIRECTORY.ordinal();
    private static final int DELETE_DIRECTORY_MASK = 1 << FileSystemCapabilities.DELETE_DIRECTORY.ordinal();
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final int TIMEOUT_EXPIRED_SIGNAL_ID = 0;
    public static final int FILE_CHANGED_SIGNAL_ID = 1;
    private static final int FLAG_FIN = 0x01;
    private static final int FLAG_INIT = 0x02;
    private static final String DIRECTORY_NAME = "directory";
    private static final String FILE_NAME = "file";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
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
    private final FileSystemResetExFW.Builder resetExRW = new FileSystemResetExFW.Builder();
    private final FileSystemErrorFW.Builder errorExRW = new FileSystemErrorFW.Builder();
    private final OctetsFW payloadRO = new OctetsFW();

    private final Long2ObjectHashMap<FileSystemBindingConfig> bindings;
    private final BufferPool bufferPool;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer errorBuffer;
    private final MutableDirectBuffer readBuffer;
    private final MutableDirectBuffer directoryBuffer;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final LongUnaryOperator supplyReplyId;
    private final int fileSystemTypeId;
    private final URI serverRoot;
    private final MessageDigest md5;
    private final Signaler signaler;
    private final Supplier<FileSystemWatcher> supplyWatcher;

    private final int decodeMax;

    private FileSystemWatcher fileSystemWatcher;

    public FileSystemServerFactory(
        FileSystemConfiguration config,
        EngineContext context,
        Supplier<FileSystemWatcher> supplyWatcher)
    {
        this.bufferPool = context.bufferPool();
        this.serverRoot = config.serverRoot();
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.readBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.errorBuffer = new UnsafeBuffer(new byte[1]);
        this.directoryBuffer = new UnsafeBuffer();
        this.supplyDebitor = context::supplyDebitor;
        this.supplyReplyId = context::supplyReplyId;
        this.fileSystemTypeId = context.supplyTypeId(FileSystemBinding.NAME);
        this.bindings = new Long2ObjectHashMap<>();
        this.signaler = context.signaler();
        this.md5 = initMessageDigest("MD5");
        this.supplyWatcher = supplyWatcher;
        this.decodeMax = bufferPool.slotCapacity();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        FileSystemBindingConfig fsBinding = new FileSystemBindingConfig(binding);
        bindings.put(binding.id, fsBinding);
        fileSystemWatcher = supplyWatcher.get();
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
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final FileSystemBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);

        final FileSystemBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final FileSystemOptionsConfig options = binding.options;
            final URI location = options.location;

            final URI resolvedRoot = serverRoot.resolve(location);
            final FileSystem fileSystem = options.fileSystem(resolvedRoot);
            final LinkOption[] symlinks = options.symlinks == IGNORE ? LINK_OPTIONS_NOFOLLOW : LINK_OPTIONS_NONE;

            final int capabilities = beginEx.capabilities();
            final String relativeDir = beginEx.directory().asString();
            final String resolvedDir = resolvedRoot.resolve(relativeDir != null ? relativeDir : "").getPath();
            final String relativePath = beginEx.path().asString();

            final Path path = fileSystem.getPath(resolvedDir).resolve(relativePath != null ? relativePath : "");
            final String resolvedPath = path.toAbsolutePath().toString();

            final String tag = beginEx.tag().asString();
            try
            {
                if (writeOperation(capabilities))
                {
                    String type = probeContentTypeOrDefault(path);
                    newStream = new FileSystemServerWriter(
                        app,
                        originId,
                        routedId,
                        initialId,
                        type,
                        symlinks,
                        relativeDir,
                        relativePath,
                        resolvedPath,
                        capabilities,
                        tag)::onAppMessage;
                }
                else
                {
                    if (Files.exists(Paths.get(resolvedPath), symlinks))
                    {
                        String type = probeContentTypeOrDefault(path);
                        newStream = new FileSystemServerReader(
                            app,
                            originId,
                            routedId,
                            initialId,
                            type,
                            symlinks,
                            relativeDir,
                            relativePath,
                            resolvedPath,
                            capabilities,
                            tag)::onAppMessage;
                    }
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

    private final class FileSystemServerReader
    {
        private final MessageConsumer app;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final String type;
        private final String relativeDir;
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

        private FileSystemServerReader(
            MessageConsumer app,
            long originId,
            long routedId,
            long initialId,
            String type,
            LinkOption[] symlinks,
            String relativeDir,
            String relativePath,
            String resolvedPath,
            int capabilities,
            String tag)
        {
            this.app = app;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.type = type;
            this.symlinks = symlinks;
            this.relativeDir = relativeDir;
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
                doAppBegin(traceId, currentTag);
                flushAppData(traceId);
            }
            else
            {
                long timeoutAt = now().toEpochMilli() + beginEx.timeout();
                long timeoutId = signaler.signalAt(timeoutAt, originId, routedId, replyId, traceId,
                    TIMEOUT_EXPIRED_SIGNAL_ID, 0);
                watchedFile = new FileSystemWatcher.WatchedFile(
                    resolvedPath, symlinks, this::calculateTag, tag, timeoutId, originId, routedId, replyId);
                fileSystemWatcher.watch(watchedFile);
            }
        }

        private BasicFileAttributes readAttributes()
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
                    newTag = calculateHash(readArray, 0, Math.max(bytesRead, 0));
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
            byte[] input,
            int offset,
            int length)
        {
            md5.reset();
            md5.update(input, offset, length);
            byte[] hash = md5.digest();
            return BitUtil.toHex(hash);
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

            if (FileSystemState.replyOpening(state) && !FileSystemState.replyClosed(state))
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
                doAppBegin(traceId, tag, 0);
                doAppEnd(traceId);
                break;
            default:
                break;
            }
            fileSystemWatcher.unregister(watchedFile);
        }

        private void doAppBegin(
            long traceId,
            String tag)
        {
            doAppBegin(traceId, tag, capabilities);
        }

        private void doAppBegin(
            long traceId,
            String tag,
            int capabilities)
        {
            state = FileSystemState.openingReply(state);
            attributes = readAttributes();

            long size = (capabilities & READ_DIRECTORY_MASK) == 0 && attributes != null
                ? attributes.size()
                : FileSystemBeginExFW.Builder.DEFAULT_PAYLOAD_SIZE;

            FileSystemBeginExFW extension = beginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(fileSystemTypeId)
                .capabilities(capabilities)
                .directory(relativeDir)
                .path(relativePath)
                .type(type)
                .payloadSize(size)
                .tag(tag)
                .build();

            doBegin(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, 0L, 0L, extension);
        }

        private void doAppData(
            long traceId,
            int reserved,
            OctetsFW payload)
        {
            final int length = payload != null ? payload.sizeof() : 0;
            assert reserved >= length + replyPad : String.format("%d >= %d", reserved, length + replyPad);

            doData(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, 0L, replyBud,
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
                doEnd(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, 0L, EMPTY_EXTENSION);
            }
        }

        private void doAppAbort(
            long traceId)
        {
            if (FileSystemState.replyOpening(state) && !FileSystemState.replyClosed(state))
            {
                state = FileSystemState.closeReply(state);
                doAbort(app, originId, routedId, replyId, replySeq, replyAck,
                        replyMax, traceId, 0L, EMPTY_EXTENSION);
            }
        }

        private void doAppReset(
            long traceId)
        {
            if (FileSystemState.initialOpening(state) && !FileSystemState.initialClosed(state))
            {
                state = FileSystemState.closeInitial(state);

                doReset(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, 0L, EMPTY_EXTENSION);
            }
        }

        private void doAppWindow(
            long traceId)
        {
            state = FileSystemState.openInitial(state);

            doWindow(app, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
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
                doAppBegin(traceId, newTag);
            }

            if (replyWin > 0)
            {
                try
                {
                    if (canReadDirectory(capabilities))
                    {
                        try (Jsonb jsonb = JsonbBuilder.create();
                             Stream<Path> list = Files.list(resolvedPath))
                        {
                            String response = jsonb.toJson(
                                list.map(path -> new FileSystemObject(
                                        path.getFileName().toString(),
                                        Files.isDirectory(path) ? DIRECTORY_NAME : FILE_NAME))
                                    .toList());
                            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                            int size = responseBytes.length;

                            if (size > 0)
                            {
                                directoryBuffer.wrap(responseBytes);

                                int reserved = Math.min(replyWin, size + replyPad);
                                int length = Math.max(reserved - replyPad, 0);

                                if (length > 0 && replyDebIndex != NO_DEBITOR_INDEX && replyDeb != null)
                                {
                                    final int minimum = Math.min(bufferPool.slotCapacity(), reserved);
                                    reserved = replyDeb.claim(traceId, replyDebIndex, replyId, minimum, reserved, 0);
                                    length = Math.max(reserved - replyPad, 0);
                                }

                                if (length > 0)
                                {
                                    OctetsFW payload = payloadRO.wrap(directoryBuffer, 0, size);

                                    doAppData(traceId, reserved, payload);

                                    replyBytes += size;

                                    if (replyBytes == size)
                                    {
                                        replyBytes = 0;
                                        doAppEnd(traceId);
                                    }
                                }
                            }
                            else
                            {
                                doAppEnd(traceId);
                            }
                        }
                    }
                    else
                    {
                        InputStream input = getInputStream();

                        if (input != null)
                        {
                            input.skip(replyBytes);
                        }

                        int available = input != null ? input.available() : 0;

                        if (available > 0)
                        {
                            int reserved = Math.min(replyWin, available + replyPad);
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

                                    if (replyBytes == attributes.size())
                                    {
                                        input.close();
                                        input = null;
                                        replyBytes = 0;
                                    }
                                }
                            }
                        }

                        if (available <= 0 || input == null)
                        {
                            doAppEnd(traceId);
                        }
                    }
                }
                catch (Exception ex)
                {
                    doAppAbort(traceId);
                }
            }
        }
    }

    private final class FileSystemServerWriter
    {
        private final MessageConsumer app;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final String type;
        private final String relativeDir;
        private final String relativePath;
        private final Path resolvedPath;
        private final int capabilities;
        private final String tag;
        private final LinkOption[] symlinks;
        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBud;

        private int state;

        private ByteChannel out;
        private Path tmpPath;

        private FileSystemServerWriter(
            MessageConsumer app,
            long originId,
            long routedId,
            long initialId,
            String type,
            LinkOption[] symlinks,
            String relativeDir,
            String relativePath,
            String resolvedPath,
            int capabilities,
            String tag)
        {
            this.app = app;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.type = type;
            this.symlinks = symlinks;
            this.relativeDir = relativeDir;
            this.relativePath = relativePath;
            this.resolvedPath = Paths.get(resolvedPath);
            this.capabilities = capabilities;
            this.tag = tag != null && !tag.isEmpty() ? tag : null;
            this.initialMax = decodeMax;
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
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onAppData(data);
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
            final long traceId = begin.traceId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            state = FileSystemState.openingInitial(state);

            FileSystemError error = detectErrorCondition(traceId);

            if (error != null)
            {
                errorExRW.wrap(errorBuffer, 0, errorBuffer.capacity()).set(error);

                FileSystemResetExFW extension = resetExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(fileSystemTypeId)
                    .error(errorExRW.build())
                    .build();

                doAppReset(traceId, extension);
            }
            else
            {
                processOperation(traceId);
            }
        }

        private FileSystemError detectErrorCondition(
            long traceId)
        {
            FileSystemError error = null;

            if ((capabilities & CREATE_FILE_MASK) != 0)
            {
                if (Files.notExists(resolvedPath.getParent()))
                {
                    error = DIRECTORY_NOT_FOUND;
                }
                else if (Files.exists(resolvedPath))
                {
                    error = FILE_EXISTS;
                }
            }
            else if ((capabilities & WRITE_FILE_MASK) != 0)
            {
                if (Files.notExists(resolvedPath))
                {
                    error = FILE_NOT_FOUND;
                }
                else if (tag == null)
                {
                    error = FILE_TAG_MISSING;
                }
                else if (!validateTag())
                {
                    error = FILE_MODIFIED;
                }
            }
            else if ((capabilities & DELETE_FILE_MASK) != 0)
            {
                if (Files.notExists(resolvedPath))
                {
                    error = FILE_NOT_FOUND;

                }
                else if (tag != null && !validateTag())
                {
                    error = FILE_MODIFIED;
                }
            }
            else if ((capabilities & CREATE_DIRECTORY_MASK) != 0 && Files.exists(resolvedPath))
            {
                error = DIRECTORY_EXISTS;
            }
            else if ((capabilities & DELETE_DIRECTORY_MASK) != 0)
            {
                if (Files.notExists(resolvedPath))
                {
                    error = DIRECTORY_NOT_FOUND;
                }
                else
                {
                    try (Stream<Path> files = Files.list(resolvedPath))
                    {
                        if (files.findAny().isPresent())
                        {
                            error = DIRECTORY_NOT_EMPTY;
                        }
                    }
                    catch (Exception ex)
                    {
                        cleanup(traceId);
                    }
                }
            }
            return error;
        }

        private void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            int offset = payload.offset();
            int length = payload.sizeof();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + initialMax)
            {
                cleanup(traceId);
            }
            else
            {
                try
                {
                    if ((flags & FLAG_INIT) != 0x00)
                    {
                        out = getOutputStream();
                    }

                    assert out != null;

                    out.write(payload.buffer().byteBuffer().slice(offset, length));

                    if ((flags & FLAG_FIN) != 0x00)
                    {
                        out.close();

                        if ((capabilities & WRITE_FILE_MASK) != 0)
                        {
                            Files.move(tmpPath, resolvedPath, REPLACE_EXISTING, ATOMIC_MOVE);
                        }

                        String currentTag = calculateTag();
                        doAppBegin(traceId, currentTag);
                        doAppEnd(traceId);
                    }
                    doAppWindow(traceId);
                }
                catch (Exception ex)
                {
                    cleanup(traceId);
                }
            }
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

            cleanupTmpFileIfExists();

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

            if (FileSystemState.replyOpening(state) && !FileSystemState.replyClosed(state))
            {
                state = FileSystemState.openReply(state);
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

            cleanupTmpFileIfExists();

            doAppReset(traceId, EMPTY_EXTENSION);
        }

        private void doAppBegin(
            long traceId,
            String tag)
        {
            doAppBegin(traceId, tag, capabilities);
        }

        private void doAppBegin(
            long traceId,
            String tag,
            int capabilities)
        {
            state = FileSystemState.openingReply(state);
            Flyweight extension = beginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(fileSystemTypeId)
                .capabilities(capabilities)
                .directory(relativeDir)
                .path(relativePath)
                .tag(tag)
                .build();
            doBegin(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, 0L, 0L, extension);
        }

        private void doAppEnd(
            long traceId)
        {
            if (FileSystemState.replyOpening(state) && !FileSystemState.replyClosed(state))
            {
                state = FileSystemState.closeReply(state);
                doEnd(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, 0L, EMPTY_EXTENSION);
            }
        }

        private void doAppAbort(
            long traceId)
        {
            if (FileSystemState.replyOpening(state) && !FileSystemState.replyClosed(state))
            {
                state = FileSystemState.closeReply(state);
                doAbort(app, originId, routedId, replyId, replySeq, replyAck,
                        replyMax, traceId, 0L, EMPTY_EXTENSION);
            }
        }

        private void doAppReset(
            long traceId,
            Flyweight extension)
        {
            if (FileSystemState.initialOpening(state) && !FileSystemState.initialClosed(state))
            {
                state = FileSystemState.closeInitial(state);

                doReset(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, 0L, extension);
            }
        }

        private void doAppWindow(
            long traceId)
        {
            if (!FileSystemState.initialClosed(state))
            {
                state = FileSystemState.openInitial(state);

                doWindow(app, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                    0L, 0L, 0);
            }
        }

        private void cleanup(
            long traceId)
        {
            cleanupTmpFileIfExists();

            doAppAbort(traceId);
            doAppReset(traceId, EMPTY_EXTENSION);
        }

        private void cleanupTmpFileIfExists()
        {
            if (tmpPath != null)
            {
                try
                {
                    Files.deleteIfExists(tmpPath);
                }
                catch (IOException ex)
                {
                    rethrowUnchecked(ex);
                }
            }
        }

        private String calculateTag()
        {
            String newTag = null;
            try
            {
                InputStream input = getInputStream();
                if (input != null)
                {
                    md5.reset();
                    while (input.available() > 0)
                    {
                        final byte[] readArray = readBuffer.byteArray();
                        int bytesRead = input.read(readArray, 0, readArray.length);
                        md5.update(readArray, 0, Math.max(bytesRead, 0));
                    }
                    byte[] hash = md5.digest();
                    newTag = BitUtil.toHex(hash);
                }
            }
            catch (IOException ex)
            {
                // reject
            }
            return newTag;
        }

        private boolean validateTag()
        {
            return tag.equals(calculateTag());
        }

        private InputStream getInputStream()
        {
            InputStream input = null;
            try
            {
                input = Files.newInputStream(resolvedPath, symlinks);
            }
            catch (IOException ex)
            {
                // reject
            }
            return input;
        }

        private ByteChannel getOutputStream()
        {
            ByteChannel output = null;
            try
            {
                if (canCreatePayload(capabilities, resolvedPath))
                {
                    output = Files.newByteChannel(resolvedPath, CREATE, WRITE);
                }
                else if (canWritePayload(capabilities, resolvedPath))
                {
                    tmpPath = Files.createTempFile(resolvedPath.getParent(), "temp-", ".tmp");
                    output = Files.newByteChannel(tmpPath, WRITE);
                }
            }
            catch (IOException ex)
            {
                // reject
            }
            return output;
        }

        private void processOperation(
            long traceId)
        {
            try
            {
                boolean processed = false;
                if ((capabilities & DELETE_FILE_MASK) != 0 || (capabilities & DELETE_DIRECTORY_MASK) != 0)
                {
                    Files.delete(resolvedPath);
                    processed = true;
                }
                else if ((capabilities & CREATE_DIRECTORY_MASK) != 0)
                {
                    Files.createDirectory(resolvedPath);
                    processed = true;
                }

                doAppWindow(traceId);

                if (processed)
                {
                    doAppBegin(traceId, null);
                    doAppEnd(traceId);
                }
            }
            catch (IOException ex)
            {
                cleanup(traceId);
            }
        }
    }

    private void doBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
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
                .originId(originId)
                .routedId(routedId)
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
        long originId,
        long routedId,
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
                .originId(originId)
                .routedId(routedId)
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
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
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
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
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
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doWindow(
        MessageConsumer receiver,
        long originId,
        long routedId,
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
                .originId(originId)
                .routedId(routedId)
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
        return (capabilities & READ_FILE_MASK) != 0;
    }

    private boolean canReadDirectory(
        int capabilities)
    {
        return (capabilities & READ_DIRECTORY_MASK) != 0;
    }

    private boolean canWritePayload(
        int capabilities,
        Path path)
    {
        return (capabilities & WRITE_FILE_MASK) != 0 && Files.exists(path);
    }

    private boolean canCreatePayload(
        int capabilities,
        Path path)
    {
        return (capabilities & CREATE_FILE_MASK) != 0 && Files.notExists(path);
    }

    private boolean canDeletePayload(
        int capabilities,
        Path path)
    {
        return (capabilities & DELETE_FILE_MASK) != 0 && Files.exists(path);
    }

    private boolean writeOperation(
        int capabilities)
    {
        return (capabilities & CREATE_FILE_MASK) != 0 ||
            (capabilities & WRITE_FILE_MASK) != 0 ||
            (capabilities & DELETE_FILE_MASK) != 0 ||
            (capabilities & CREATE_DIRECTORY_MASK) != 0 ||
            (capabilities & DELETE_DIRECTORY_MASK) != 0;
    }
}
