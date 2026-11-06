/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BACKOFF_MAX_SPINS;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CONFIG_URL;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_ROUTED_DELAY_MILLIS;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_SYNTHETIC_ABORT;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;
import static java.nio.file.FileVisitOption.FOLLOW_LINKS;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.agrona.concurrent.MessageHandlerEx;
import io.aklivity.zilla.runtime.common.agrona.concurrent.RingBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration.PropertyDef;
import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineBuilder;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public final class EngineBench implements AutoCloseable
{
    public static final String WORKER_THREAD_NAME = "engine/worker#0";

    private static final String DEFAULT_NAMESPACE = "test";
    private static final int EXTERNAL_INDEX = Long.SIZE - 1;
    private static final long EXTERNAL_AFFINITY_MASK = 1L << EXTERNAL_INDEX;
    private static final long ROUTED_DELAY_MILLIS = 500L;
    private static final int WORKER_INDEX = 0;
    private static final long INITIAL_ID_PREFIX = ((long) EXTERNAL_INDEX << 56) | ((long) WORKER_INDEX << 48);
    private static final long THROTTLE_ID_MASK = 0x0000_0000_7fff_fffeL;
    private static final int THROTTLE_TYPE_MASK = 0x4000_0000;
    private static final int WRITE_BUFFER_CAPACITY = 64 * 1024;
    private static final long TIMEOUT_NANOS = SECONDS.toNanos(10L);
    private static final Pattern DATA_FILENAME_PATTERN = Pattern.compile("data\\d+");
    private static final DirectBufferEx EMPTY_EXTENSION = new UnsafeBufferEx(new byte[0]);

    private final Properties properties = new Properties();
    private final EngineBuilder builder = Engine.builder();
    private final List<Throwable> errors = new ArrayList<>();

    private final FrameFW frameRO = new FrameFW();
    private final BeginFW beginRO = new BeginFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[WRITE_BUFFER_CAPACITY]);

    private final Long2ObjectHashMap<MessageConsumer> streamsById = new Long2ObjectHashMap<>();
    private final Long2ObjectHashMap<MessageConsumer> throttlesById = new Long2ObjectHashMap<>();
    private final Long2ObjectHashMap<BindingHandler> serversByRoutedId = new Long2ObjectHashMap<>();
    private final Queue<Client> idleClients = new ArrayDeque<>();
    private final MessageConsumer sender = this::write;
    private final MessageHandlerEx dispatcher = this::dispatch;

    private FileSystem configFileSystem;
    private Engine engine;
    private StreamsLayout externalLayout;
    private StreamsLayout workerLayout;
    private RingBufferEx externalStreams;
    private RingBufferEx workerStreams;
    private long initialSequence;

    public EngineBench directory(
        String directory)
    {
        return configure(ENGINE_DIRECTORY, directory);
    }

    public <T> EngineBench configure(
        PropertyDef<T> property,
        T value)
    {
        properties.setProperty(property.name(), requireNonNull(value).toString());
        return this;
    }

    public EngineBench configure(
        String name,
        String value)
    {
        properties.setProperty(name, requireNonNull(value));
        return this;
    }

    public EngineBench external(
        String binding)
    {
        return external(DEFAULT_NAMESPACE, binding);
    }

    public EngineBench external(
        String namespace,
        String binding)
    {
        builder.affinity(namespace, binding, EXTERNAL_AFFINITY_MASK);
        return this;
    }

    public EngineBench start(
        String configuration) throws Exception
    {
        final URL configURL = requireNonNull(EngineBench.class.getClassLoader().getResource(configuration), configuration);

        configure(ENGINE_CONFIG_URL, configURL);
        configure(ENGINE_WORKERS, 1);
        configure(ENGINE_DRAIN_ON_CLOSE, true);
        configure(ENGINE_SYNTHETIC_ABORT, true);
        configure(ENGINE_ROUTED_DELAY_MILLIS, ROUTED_DELAY_MILLIS);
        properties.putIfAbsent(ENGINE_BACKOFF_MAX_SPINS.name(), Integer.toString(Integer.MAX_VALUE));

        final EngineConfiguration config = new EngineConfiguration(properties);
        clean(config.directory());

        configFileSystem = EngineRule.openConfigFileSystem(config.configURI());

        engine = builder.config(config)
                        .errorHandler(this::onError)
                        .build();
        engine.start();

        externalLayout = new StreamsLayout.Builder()
                .path(config.directory().resolve(String.format("data%d", EXTERNAL_INDEX)))
                .streamsCapacity(config.streamsBufferCapacity())
                .readonly(false)
                .build();
        workerLayout = new StreamsLayout.Builder()
                .path(config.directory().resolve(String.format("data%d", WORKER_INDEX)))
                .readonly(true)
                .build();
        externalStreams = externalLayout.streamsBuffer();
        workerStreams = workerLayout.streamsBuffer();

        return this;
    }

    public int labelId(
        String label)
    {
        return engine.supplyLabelId(label);
    }

    public long bindingId(
        String binding)
    {
        return bindingId(DEFAULT_NAMESPACE, binding);
    }

    public long bindingId(
        String namespace,
        String binding)
    {
        return NamespacedId.id(engine.supplyLabelId(namespace), engine.supplyLabelId(binding));
    }

    public void newServer(
        String binding,
        BindingHandler handler)
    {
        newServer(bindingId(binding), handler);
    }

    public void newServer(
        long routedId,
        BindingHandler handler)
    {
        serversByRoutedId.put(routedId, requireNonNull(handler));
    }

    public Client newClient(
        String binding,
        MessageConsumer reply)
    {
        return newClient(bindingId(binding), reply);
    }

    public Client newClient(
        long routedId,
        MessageConsumer reply)
    {
        Client client = idleClients.poll();

        if (client == null)
        {
            client = new Client();
        }

        initialSequence += 2L;

        return client.reset(routedId, INITIAL_ID_PREFIX | (initialSequence & THROTTLE_ID_MASK) | 1L, reply);
    }

    public int poll()
    {
        checkErrors();

        return externalStreams.readEx(dispatcher);
    }

    public boolean pollUntil(
        BooleanSupplier condition)
    {
        final long deadline = System.nanoTime() + TIMEOUT_NANOS;

        while (!condition.getAsBoolean() && System.nanoTime() < deadline)
        {
            if (poll() == 0)
            {
                Thread.onSpinWait();
            }
        }

        return condition.getAsBoolean();
    }

    public long workerThreadId()
    {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> WORKER_THREAD_NAME.equals(t.getName()))
                .mapToLong(Thread::threadId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("engine worker thread not found"));
    }

    @Override
    public void close() throws Exception
    {
        try
        {
            if (engine != null)
            {
                engine.close();
            }
        }
        finally
        {
            if (externalLayout != null)
            {
                externalLayout.close();
            }

            if (workerLayout != null)
            {
                workerLayout.close();
            }

            if (configFileSystem != null)
            {
                configFileSystem.close();
            }
        }

        checkErrors();
    }

    private void onError(
        Throwable error)
    {
        synchronized (errors)
        {
            errors.add(error);
        }
    }

    private void checkErrors()
    {
        synchronized (errors)
        {
            if (!errors.isEmpty())
            {
                final IllegalStateException failure = new IllegalStateException("engine failed");
                errors.forEach(failure::addSuppressed);
                throw failure;
            }
        }
    }

    private void clean(
        Path directory) throws IOException
    {
        if (Files.exists(directory))
        {
            try (Stream<Path> paths = Files.walk(directory, FOLLOW_LINKS))
            {
                paths.filter(this::shouldDelete)
                     .map(Path::toFile)
                     .forEach(File::delete);
            }
        }
    }

    private boolean shouldDelete(
        Path path)
    {
        final String filename = path.getFileName().toString();

        return "control".equals(filename) ||
               "routes".equals(filename) ||
               "streams".equals(filename) ||
               "labels".equals(filename) ||
               DATA_FILENAME_PATTERN.matcher(filename).matches();
    }

    private void dispatch(
        int msgTypeId,
        MutableDirectBufferEx buffer,
        int index,
        int length)
    {
        final long streamId = frameRO.wrap(buffer, index, index + length).streamId();
        final boolean throttle = (msgTypeId & THROTTLE_TYPE_MASK) != 0;
        final MessageConsumer consumer = (throttle ? throttlesById : streamsById).get(streamId);

        if (consumer != null)
        {
            consumer.accept(msgTypeId, buffer, index, length);

            if (msgTypeId == EndFW.TYPE_ID || msgTypeId == AbortFW.TYPE_ID)
            {
                streamsById.remove(streamId);
            }
            else if (msgTypeId == ResetFW.TYPE_ID)
            {
                throttlesById.remove(streamId);
            }
        }
        else if (msgTypeId == BeginFW.TYPE_ID && (streamId & 1L) != 0L)
        {
            onServerBegin(beginRO.wrap(buffer, index, index + length));
        }
    }

    private void onServerBegin(
        BeginFW begin)
    {
        final long initialId = begin.streamId();
        final BindingHandler server = serversByRoutedId.get(begin.routedId());
        final MessageConsumer receiver = server != null
            ? server.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender)
            : null;

        if (receiver != null)
        {
            streamsById.put(initialId, receiver);
            throttlesById.put(initialId & ~1L, receiver);
            receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }
        else
        {
            final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(begin.originId())
                .routedId(begin.routedId())
                .streamId(initialId)
                .sequence(begin.sequence())
                .acknowledge(begin.acknowledge())
                .maximum(begin.maximum())
                .traceId(begin.traceId())
                .build();

            write(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
        }
    }

    private void write(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length)
    {
        final long deadline = System.nanoTime() + TIMEOUT_NANOS;

        while (!workerStreams.write(msgTypeId, buffer, index, length))
        {
            if (System.nanoTime() > deadline)
            {
                throw new IllegalStateException("streams buffer full");
            }

            Thread.onSpinWait();
        }

        if (msgTypeId == EndFW.TYPE_ID || msgTypeId == AbortFW.TYPE_ID)
        {
            throttlesById.remove(frameRO.wrap(buffer, index, index + length).streamId());
        }
        else if (msgTypeId == ResetFW.TYPE_ID)
        {
            streamsById.remove(frameRO.wrap(buffer, index, index + length).streamId());
        }
    }

    public final class Client implements MessageConsumer
    {
        private static final int INITIAL_CLOSED = 1;
        private static final int REPLY_CLOSED = 2;

        private final MessageConsumer inbound = this::onInbound;
        private final DataFW dataRO = new DataFW();
        private final WindowFW windowRO = new WindowFW();

        private MessageConsumer reply;
        private int state;
        private long originId;
        private long routedId;
        private long initialId;
        private long replyId;
        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long replySeq;
        private long replyAck;
        private int replyMax;

        public long originId()
        {
            return originId;
        }

        public long routedId()
        {
            return routedId;
        }

        public long initialId()
        {
            return initialId;
        }

        public long replyId()
        {
            return replyId;
        }

        public int initialCredit()
        {
            return (int) (initialAck + initialMax - initialSeq);
        }

        public void begin()
        {
            begin(EMPTY_EXTENSION, 0, 0);
        }

        public void begin(
            DirectBufferEx extension,
            int offset,
            int length)
        {
            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .affinity(0L)
                .extension(extension, offset, length)
                .build();

            accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        public void data(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            data(payload, offset, length, EMPTY_EXTENSION, 0, 0);
        }

        public void data(
            DirectBufferEx payload,
            int offset,
            int length,
            DirectBufferEx extension,
            int extensionOffset,
            int extensionLength)
        {
            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .budgetId(0L)
                .reserved(length + initialPad)
                .payload(payload, offset, length)
                .extension(extension, extensionOffset, extensionLength)
                .build();

            accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
        }

        public void end()
        {
            final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .build();

            accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
        }

        public void abort()
        {
            final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .build();

            accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());

            final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .build();

            write(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
            close(REPLY_CLOSED);
        }

        public void replyWindow(
            int maximum)
        {
            replyMax = maximum;
            replyAck = replySeq;

            doReplyWindow();
        }

        @Override
        public void accept(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                streamsById.put(replyId, inbound);
                throttlesById.put(initialId, inbound);
                break;
            case DataFW.TYPE_ID:
                initialSeq += dataRO.wrap(buffer, index, index + length).reserved();
                break;
            default:
                break;
            }

            write(msgTypeId, buffer, index, length);

            if (msgTypeId == EndFW.TYPE_ID || msgTypeId == AbortFW.TYPE_ID)
            {
                close(INITIAL_CLOSED);
            }
        }

        private Client reset(
            long routedId,
            long initialId,
            MessageConsumer reply)
        {
            this.reply = reply;
            this.state = 0;
            this.originId = routedId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = initialId & ~1L;
            this.initialSeq = 0L;
            this.initialAck = 0L;
            this.initialMax = 0;
            this.initialPad = 0;
            this.replySeq = 0L;
            this.replyAck = 0L;
            this.replyMax = 0;
            return this;
        }

        private void onInbound(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                initialAck = window.acknowledge();
                initialMax = window.maximum();
                initialPad = window.padding();
                break;
            case DataFW.TYPE_ID:
                replySeq += dataRO.wrap(buffer, index, index + length).reserved();
                break;
            default:
                break;
            }

            reply.accept(msgTypeId, buffer, index, length);

            switch (msgTypeId)
            {
            case DataFW.TYPE_ID:
                replyAck = replySeq;
                if (replyMax != 0)
                {
                    doReplyWindow();
                }
                break;
            case EndFW.TYPE_ID:
            case AbortFW.TYPE_ID:
                close(REPLY_CLOSED);
                break;
            case ResetFW.TYPE_ID:
                close(INITIAL_CLOSED | REPLY_CLOSED);
                break;
            default:
                break;
            }
        }

        private void doReplyWindow()
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .budgetId(0L)
                .padding(0)
                .build();

            write(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void close(
            int closed)
        {
            state |= closed;

            if (state == (INITIAL_CLOSED | REPLY_CLOSED))
            {
                streamsById.remove(replyId);
                throttlesById.remove(initialId);
                idleClients.offer(this);
            }
        }
    }

    public static final class FrameCounter implements MessageConsumer
    {
        private final DataFW dataRO = new DataFW();

        private int begins;
        private int datas;
        private long dataBytes;
        private int ends;
        private int aborts;
        private int flushes;
        private int windows;
        private int resets;

        public int begins()
        {
            return begins;
        }

        public int datas()
        {
            return datas;
        }

        public long dataBytes()
        {
            return dataBytes;
        }

        public int ends()
        {
            return ends;
        }

        public int aborts()
        {
            return aborts;
        }

        public int flushes()
        {
            return flushes;
        }

        public int windows()
        {
            return windows;
        }

        public int resets()
        {
            return resets;
        }

        public void clear()
        {
            begins = 0;
            datas = 0;
            dataBytes = 0L;
            ends = 0;
            aborts = 0;
            flushes = 0;
            windows = 0;
            resets = 0;
        }

        @Override
        public void accept(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                begins++;
                break;
            case DataFW.TYPE_ID:
                datas++;
                dataBytes += Math.max(dataRO.wrap(buffer, index, index + length).length(), 0);
                break;
            case EndFW.TYPE_ID:
                ends++;
                break;
            case AbortFW.TYPE_ID:
                aborts++;
                break;
            case FlushFW.TYPE_ID:
                flushes++;
                break;
            case WindowFW.TYPE_ID:
                windows++;
                break;
            case ResetFW.TYPE_ID:
                resets++;
                break;
            default:
                break;
            }
        }
    }

    public static final class CountingHandler implements BindingHandler
    {
        private static final int DEFAULT_WINDOW = 8 * 1024;

        private final BeginFW beginRO = new BeginFW();
        private final DataFW dataRO = new DataFW();
        private final BeginFW.Builder beginRW = new BeginFW.Builder();
        private final EndFW.Builder endRW = new EndFW.Builder();
        private final AbortFW.Builder abortRW = new AbortFW.Builder();
        private final ResetFW.Builder resetRW = new ResetFW.Builder();
        private final WindowFW.Builder windowRW = new WindowFW.Builder();
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[256]);
        private final Queue<CountingStream> idleStreams = new ArrayDeque<>();
        private final FrameCounter counter = new FrameCounter();
        private final int window;

        public CountingHandler()
        {
            this(DEFAULT_WINDOW);
        }

        public CountingHandler(
            int window)
        {
            this.window = window;
        }

        public FrameCounter counter()
        {
            return counter;
        }

        @Override
        public MessageConsumer newStream(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length,
            MessageConsumer sender)
        {
            CountingStream stream = idleStreams.poll();

            if (stream == null)
            {
                stream = new CountingStream();
            }

            return stream.reset(sender);
        }

        private final class CountingStream
        {
            private final MessageConsumer consumer = this::onMessage;

            private MessageConsumer sender;
            private long originId;
            private long routedId;
            private long initialId;
            private long replyId;
            private long initialSeq;
            private long replySeq;
            private long replyAck;

            private MessageConsumer reset(
                MessageConsumer sender)
            {
                this.sender = sender;
                this.initialSeq = 0L;
                this.replySeq = 0L;
                this.replyAck = 0L;
                return consumer;
            }

            private void onMessage(
                int msgTypeId,
                DirectBufferEx buffer,
                int index,
                int length)
            {
                counter.accept(msgTypeId, buffer, index, length);

                switch (msgTypeId)
                {
                case BeginFW.TYPE_ID:
                    onBegin(beginRO.wrap(buffer, index, index + length));
                    break;
                case DataFW.TYPE_ID:
                    initialSeq += dataRO.wrap(buffer, index, index + length).reserved();
                    doWindow();
                    break;
                case EndFW.TYPE_ID:
                    doEnd();
                    idleStreams.offer(this);
                    break;
                case AbortFW.TYPE_ID:
                    doAbort();
                    idleStreams.offer(this);
                    break;
                case ResetFW.TYPE_ID:
                    doReset();
                    idleStreams.offer(this);
                    break;
                default:
                    break;
                }
            }

            private void onBegin(
                BeginFW begin)
            {
                this.originId = begin.originId();
                this.routedId = begin.routedId();
                this.initialId = begin.streamId();
                this.replyId = initialId & ~1L;

                doWindow();
                doBegin();
            }

            private void doBegin()
            {
                final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(routedId)
                    .routedId(originId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(0)
                    .affinity(0L)
                    .build();

                sender.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
            }

            private void doWindow()
            {
                final WindowFW windowFW = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(routedId)
                    .routedId(originId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialSeq)
                    .maximum(window)
                    .budgetId(0L)
                    .padding(0)
                    .build();

                sender.accept(windowFW.typeId(), windowFW.buffer(), windowFW.offset(), windowFW.sizeof());
            }

            private void doEnd()
            {
                final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(routedId)
                    .routedId(originId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(0)
                    .build();

                sender.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
            }

            private void doAbort()
            {
                final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(routedId)
                    .routedId(originId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(0)
                    .build();

                sender.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
            }

            private void doReset()
            {
                final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialSeq)
                    .maximum(window)
                    .build();

                sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
            }
        }
    }
}
