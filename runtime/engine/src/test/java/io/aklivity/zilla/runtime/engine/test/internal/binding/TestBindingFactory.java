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
package io.aklivity.zilla.runtime.engine.test.internal.binding;

import static io.aklivity.zilla.runtime.engine.test.internal.binding.config.TestBindingOptionsConfigAdapter.DEFAULT_ASSERTION_SCHEMA;
import static java.util.Collections.emptyList;

import java.security.KeyStore;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongConsumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;
import io.aklivity.zilla.runtime.engine.security.Trusted;
import io.aklivity.zilla.runtime.engine.test.internal.binding.config.TestBindingConfig;
import io.aklivity.zilla.runtime.engine.test.internal.binding.config.TestBindingOptionsConfig;
import io.aklivity.zilla.runtime.engine.test.internal.binding.config.TestBindingOptionsConfig.CatalogAssertion;
import io.aklivity.zilla.runtime.engine.test.internal.binding.config.TestBindingOptionsConfig.Event;
import io.aklivity.zilla.runtime.engine.test.internal.binding.config.TestBindingOptionsConfig.VaultAssertion;
import io.aklivity.zilla.runtime.engine.test.internal.binding.config.TestRouteConfig;
import io.aklivity.zilla.runtime.engine.test.internal.event.TestEventContext;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.OctetsFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

final class TestBindingFactory implements BindingHandler
{
    private final BeginFW beginRO = new BeginFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();

    private final DataFW dataRO = new DataFW();
    private final DataFW.Builder dataRW = new DataFW.Builder();

    private final EndFW endRO = new EndFW();
    private final EndFW.Builder endRW = new EndFW.Builder();

    private final AbortFW abortRO = new AbortFW();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();

    private final FlushFW flushRO = new FlushFW();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final ResetFW resetRO = new ResetFW();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();

    private final ChallengeFW challengeRO = new ChallengeFW();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final Configuration config;
    private final EngineContext context;
    private final TestEventContext event;
    private final Long2ObjectHashMap<TestBindingConfig> bindings;

    private ConverterHandler valueType;
    private String schema;
    private SchemaConfig catalog;
    private List<CatalogHandler> catalogs;
    private List<CatalogAssertion> catalogAssertions;
    private GuardHandler guard;
    private String credentials;
    private List<Event> events;
    private int eventIndex;
    private VaultHandler vault;
    private VaultAssertion vaultAssertion;

    TestBindingFactory(
        Configuration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.event = new TestEventContext(context);
        this.bindings = new Long2ObjectHashMap<>();
    }

    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, new TestBindingConfig(binding));

        TestBindingOptionsConfig options = (TestBindingOptionsConfig) binding.options;
        if (options != null)
        {
            int namespaceId = NamespacedId.namespaceId(binding.id);

            if (options.value != null)
            {
                this.valueType = context.supplyWriteConverter(options.value);
            }

            this.schema = options.schema;

            if (options.cataloged != null)
            {
                this.catalog = !options.cataloged.isEmpty() ? options.cataloged.get(0).schemas.get(0) : null;
                this.catalogs = new LinkedList<>();
                for (CatalogedConfig catalog : options.cataloged)
                {
                    int catalogId = context.supplyTypeId(catalog.name);
                    final CatalogHandler handler = context.supplyCatalog(NamespacedId.id(namespaceId, catalogId));
                    catalogs.add(handler);
                }
                this.catalogAssertions = options.catalogAssertions != null && !options.catalogAssertions.isEmpty() ?
                    options.catalogAssertions.get(0).assertions : null;
            }

            if (options.authorization != null)
            {
                int guardId = context.supplyTypeId(options.authorization.name);
                this.guard = context.supplyGuard(NamespacedId.id(namespaceId, guardId));
                this.credentials = options.authorization.credentials;
            }

            this.events = options.events;

            if (binding.vault != null)
            {
                this.vault = context.supplyVault(binding.vaultId);
                this.vaultAssertion = options.vaultAssertion;
            }

            if (options.metrics != null && !options.metrics.isEmpty())
            {
                for (TestBindingOptionsConfig.Metric metric : options.metrics)
                {
                    long metricId = NamespacedId.id(namespaceId, context.supplyTypeId(metric.name));

                    LongConsumer writer = context.supplyMetricWriter(Metric.Kind.valueOf(metric.kind.toUpperCase()),
                        binding.id, metricId);

                    writer.accept(metric.values[context.index()]);
                }
            }

        }
    }

    public void detach(
        BindingConfig binding)
    {
        bindings.remove(binding.id);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer source)
    {
        BeginFW begin = beginRO.wrap(buffer, index, index + length);
        long originId = begin.originId();
        long routedId = begin.routedId();
        long initialId = begin.streamId();
        long replyId = initialId ^ 1L;

        MessageConsumer newStream =  null;

        TestBindingConfig binding = bindings.get(routedId);
        long authorization = begin.authorization();

        if (guard != null)
        {
            authorization = guard.reauthorize(begin.traceId(), routedId, 0, credentials);
        }

        TestRouteConfig route = binding != null ? binding.resolve(authorization) : null;

        if (route != null)
        {
            final long resolvedId = route.id;
            newStream = new TestSource(source, originId, routedId, initialId, replyId, resolvedId)::onMessage;
        }

        return newStream;
    }

    private final class TestSource
    {
        private final MessageConsumer source;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBud;
        private int initialPad;
        private int initialCap;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBud;
        private int replyCap;

        private final TestTarget target;

        private TestSource(
            MessageConsumer source,
            long originId,
            long routedId,
            long initialId,
            long replyId,
            long resolvedId)
        {
            this.source = source;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = replyId;
            this.target = new TestTarget(routedId, resolvedId);
        }

        private void onMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onInitialBegin(begin);
                break;
            case DataFW.TYPE_ID:
                DataFW data = dataRO.wrap(buffer, index, index + length);
                onInitialData(data);
                break;
            case EndFW.TYPE_ID:
                EndFW end = endRO.wrap(buffer, index, index + length);
                onInitialEnd(end);
                break;
            case AbortFW.TYPE_ID:
                AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onInitialAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onInitialFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onReplyReset(reset);
                break;
            case WindowFW.TYPE_ID:
                WindowFW window = windowRO.wrap(buffer, index, index + length);
                onReplyWindow(window);
                break;
            case ChallengeFW.TYPE_ID:
                ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onReplyChallenge(challenge);
                break;
            }
        }

        private void onInitialBegin(
            BeginFW begin)
        {
            long traceId = begin.traceId();

            target.doInitialBegin(traceId);

            if (vault != null && vaultAssertion != null)
            {
                String key = vaultAssertion.key;
                if (key != null)
                {
                    vault.initKeys(List.of(key));
                }

                String signer = vaultAssertion.signer;
                if (signer != null)
                {
                    vault.initSigners(List.of(signer));
                }

                String trust = vaultAssertion.trust;
                boolean trustcacerts = vaultAssertion.trustcacerts;
                if (trust != null || trustcacerts)
                {
                    KeyStore cacerts = trustcacerts ? Trusted.cacerts(config) : null;
                    List<String> certRefs = trust != null ? List.of(trust) : emptyList();
                    vault.initTrust(certRefs, cacerts);
                }
            }

            if (catalogs != null)
            {
                CatalogHandler handler = catalogs.get(0);
                if (catalogAssertions != null && !catalogAssertions.isEmpty())
                {
                    for (CatalogAssertion assertion : catalogAssertions)
                    {
                        try
                        {
                            Thread.sleep(assertion.delay);
                        }
                        catch (Exception ex)
                        {
                            throw new RuntimeException(ex);
                        }
                        if (catalog.subject != null && catalog.version != null)
                        {
                            int id = handler.resolve(catalog.subject, catalog.version);
                            if (id != assertion.id)
                            {
                                doInitialReset(traceId);
                            }
                            if (DEFAULT_ASSERTION_SCHEMA != assertion.schema)
                            {
                                String schema = handler.resolve(id);
                                if (!Objects.equals(assertion.schema, schema))
                                {
                                    doInitialReset(traceId);
                                }
                            }
                        }
                        else
                        {
                            String schema = handler.resolve(catalog.id);
                            if (assertion.schema == null && schema != null)
                            {
                                doInitialReset(traceId);
                            }
                            else if (assertion.schema != null && !assertion.schema.equals(schema))
                            {
                                doInitialReset(traceId);
                            }
                        }
                    }
                }
                else
                {
                    if (catalog.subject != null && schema == null)
                    {
                        handler.unregister(catalog.subject);
                    }
                    if (catalog.subject != null && schema != null)
                    {
                        handler.register(catalog.subject, schema);
                    }
                    else if (catalog.subject != null && catalog.version != null && schema != null)
                    {
                        handler.resolve(catalog.subject, catalog.version);
                    }
                    else
                    {
                        handler.resolve(catalog.id);
                    }
                }
            }

            while (events != null && eventIndex < events.size())
            {
                Event e = events.get(eventIndex);
                event.connected(traceId, routedId, e.timestamp, e.message);
                eventIndex++;
            }
        }

        private void onInitialData(
            DataFW data)
        {
            long sequence = data.sequence();
            long traceId = data.traceId();
            int reserved = data.reserved();
            int flags = data.flags();
            OctetsFW payload = data.payload();

            initialSeq = sequence + reserved;

            if (valueType != null &&
                valueType.convert(traceId, routedId, payload.buffer(), payload.offset(), payload.sizeof(),
                        ValueConsumer.NOP) < 0)
            {
                target.doInitialAbort(traceId);
            }
            else
            {
                target.doInitialData(traceId, flags, reserved, payload);
            }
        }

        private void onInitialEnd(
            EndFW end)
        {
            long traceId = end.traceId();

            target.doInitialEnd(traceId);
        }

        private void onInitialAbort(
            AbortFW abort)
        {
            long traceId = abort.traceId();

            target.doInitialAbort(traceId);
        }

        private void onInitialFlush(
            FlushFW flush)
        {
            long traceId = flush.traceId();
            int reserved = flush.reserved();

            target.doInitialFlush(traceId, reserved);
        }

        private void onReplyReset(
            ResetFW reset)
        {
            long traceId = reset.traceId();

            target.doReplyReset(traceId);
        }

        private void onReplyWindow(
            WindowFW window)
        {
            long traceId = window.traceId();

            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyPad = window.padding();
            replyBud = window.budgetId();
            replyCap = window.capabilities();

            target.doReplyWindow(traceId, replyAck, replyMax, replyBud, replyPad, replyCap);
        }

        private void onReplyChallenge(
            ChallengeFW challenge)
        {
            long traceId = challenge.traceId();

            target.doReplyChallenge(traceId);
        }

        private void doInitialReset(
            long traceId)
        {
            doReset(source, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
        }

        private void doInitialWindow(
            long traceId,
            long acknowledge,
            int maximum,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = acknowledge;
            initialMax = maximum;
            initialBud = budgetId;
            initialPad = padding;
            initialCap = capabilities;

            doWindow(source, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                    initialBud, initialPad, initialCap);
        }

        private void doInitialChallenge(
            long traceId)
        {
            doChallenge(source, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
        }

        private void doReplyBegin(
            long traceId)
        {
            doBegin(source, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId);
        }

        private void doReplyData(
            long traceId,
            int flags,
            int reserved,
            OctetsFW payload)
        {
            doData(source, originId, routedId, replyId, replySeq, replyAck, replyMax, replyBud,
                    traceId, flags, reserved, payload);

            replySeq += reserved;
        }

        private void doReplyEnd(
            long traceId)
        {
            doEnd(source, originId, routedId, replyId, replySeq, replyAck, replyPad, traceId);
        }

        private void doReplyAbort(
            long traceId)
        {
            doAbort(source, originId, routedId, replyId, replySeq, replyAck, replyPad, traceId);
        }

        private void doReplyFlush(
            long traceId,
            int reserved)
        {
            doFlush(source, originId, routedId, replyId, replySeq, replyAck, replyPad, traceId, replyBud, reserved);
        }

        private final class TestTarget
        {
            private MessageConsumer target;
            private final long originId;
            private final long routedId;
            private final long initialId;
            private final long replyId;

            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private long initialBud;
            private int initialPad;
            private int initialCap;

            private long replySeq;
            private long replyAck;
            private int replyMax;
            private int replyPad;
            private long replyBud;
            private int replyCap;

            private final TestSource source;

            private TestTarget(
                long originId,
                long routedId)
            {
                this.originId = originId;
                this.routedId = routedId;
                this.initialId = context.supplyInitialId(routedId);
                this.replyId = context.supplyReplyId(initialId);
                this.source = TestSource.this;
            }

            private void onMessage(
                int msgTypeId,
                DirectBuffer buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case ResetFW.TYPE_ID:
                    ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onInitialReset(reset);
                    break;
                case WindowFW.TYPE_ID:
                    WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onInitialWindow(window);
                    break;
                case ChallengeFW.TYPE_ID:
                    ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                    onInitialChallenge(challenge);
                    break;
                case BeginFW.TYPE_ID:
                    BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onReplyBegin(begin);
                    break;
                case DataFW.TYPE_ID:
                    DataFW data = dataRO.wrap(buffer, index, index + length);
                    onReplyData(data);
                    break;
                case EndFW.TYPE_ID:
                    EndFW end = endRO.wrap(buffer, index, index + length);
                    onReplyEnd(end);
                    break;
                case AbortFW.TYPE_ID:
                    AbortFW abort = abortRO.wrap(buffer, index, index + length);
                    onReplyAbort(abort);
                    break;
                case FlushFW.TYPE_ID:
                    FlushFW flush = flushRO.wrap(buffer, index, index + length);
                    onReplyFlush(flush);
                    break;
                }
            }

            private void onInitialReset(
                ResetFW reset)
            {
                long traceId = reset.traceId();

                source.doInitialReset(traceId);
            }

            private void onInitialWindow(
                WindowFW window)
            {
                long traceId = window.traceId();

                initialMax = window.maximum();
                initialAck = window.acknowledge();
                initialPad = window.padding();
                initialBud = window.budgetId();
                initialCap = window.capabilities();

                source.doInitialWindow(traceId, initialAck, initialMax, initialBud, initialPad, initialCap);
            }

            private void onInitialChallenge(
                ChallengeFW challenge)
            {
                long traceId = challenge.traceId();

                source.doInitialChallenge(traceId);
            }

            private void onReplyBegin(
                BeginFW begin)
            {
                long traceId = begin.traceId();

                source.doReplyBegin(traceId);
            }

            private void onReplyData(
                DataFW data)
            {
                long sequence = data.sequence();
                long traceId = data.traceId();
                int reserved = data.reserved();
                int flags = data.flags();
                OctetsFW payload = data.payload();

                replySeq = sequence + reserved;

                source.doReplyData(traceId, flags, reserved, payload);
            }

            private void onReplyEnd(
                EndFW end)
            {
                long traceId = end.traceId();

                source.doReplyEnd(traceId);
            }

            private void onReplyAbort(
                AbortFW abort)
            {
                long traceId = abort.traceId();

                source.doReplyAbort(traceId);
            }

            private void onReplyFlush(
                FlushFW flush)
            {
                long traceId = flush.traceId();
                int reserved = flush.reserved();

                source.doReplyFlush(traceId, reserved);
            }

            private void doInitialBegin(
                long traceId)
            {
                target = newStream(this::onMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
            }

            private void doInitialData(
                long traceId,
                int flags,
                int reserved,
                OctetsFW payload)
            {
                doData(target, originId, routedId, initialId, initialSeq, initialAck, initialMax, initialBud,
                        traceId, flags, reserved, payload);

                initialSeq += reserved;
            }

            private void doInitialEnd(
                long traceId)
            {
                doEnd(target, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
            }

            private void doInitialAbort(
                long traceId)
            {
                doAbort(target, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
            }

            private void doInitialFlush(
                long traceId,
                int reserved)
            {
                doFlush(target, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, initialBud, reserved);
            }

            private void doReplyReset(
                long traceId)
            {
                doReset(target, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId);
            }

            private void doReplyWindow(
                long traceId,
                long acknowledge,
                int maximum,
                long budgetId,
                int padding,
                int capabilities)
            {
                replyAck = acknowledge;
                replyMax = maximum;
                replyBud = budgetId;
                replyPad = padding;
                replyCap = capabilities;

                doWindow(target, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, replyBud, replyPad, replyCap);
            }

            private void doReplyChallenge(
                long traceId)
            {
                doChallenge(target, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId);
            }
        }
    }

    private MessageConsumer newStream(
        MessageConsumer source,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();
        BindingHandler streamFactory = context.streamFactory();

        BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .affinity(0L)
                .build();

        MessageConsumer stream =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), source);

        stream.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return stream;
    }

    private void doBegin(
        MessageConsumer stream,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .affinity(0L)
                .build();

        stream.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer stream,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long budgetId,
        long traceId,
        int flags,
        int reserved,
        OctetsFW payload)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .flags(flags)
                .budgetId(budgetId)
                .reserved(reserved)
                .payload(payload)
                .build();

        stream.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer stream,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .build();

        stream.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer stream,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .build();

        stream.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doFlush(
        MessageConsumer stream,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long budgetId,
        int reserved)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .budgetId(budgetId)
                .reserved(reserved)
                .build();

        stream.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doReset(
        MessageConsumer stream,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .build();

        stream.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doWindow(
        MessageConsumer stream,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long budgetId,
        int padding,
        int capabilities)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .budgetId(budgetId)
                .padding(padding)
                .capabilities(capabilities)
                .build();

        stream.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doChallenge(
        MessageConsumer stream,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .build();

        stream.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
    }
}
