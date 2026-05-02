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
package io.aklivity.zilla.runtime.engine;

import java.net.InetAddress;
import java.nio.channels.SelectableChannel;
import java.nio.file.Path;
import java.time.Clock;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.binding.function.MessageReader;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.event.EventFormatter;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

/**
 * Per-thread service context supplied to binding, guard, vault, catalog, exporter, and model plugins.
 * <p>
 * An {@code EngineContext} is created once per I/O thread and passed to each plugin's
 * {@code supply(EngineContext)} method. All methods on this interface are safe to call only
 * from the owning I/O thread unless otherwise noted.
 * </p>
 * <p>
 * It provides access to:
 * <ul>
 *   <li>Id generation — stream ids, budget ids, trace ids, binding ids</li>
 *   <li>Stream messaging — sender and receiver {@link MessageConsumer} lookup</li>
 *   <li>Flow control — {@link BudgetCreditor} and {@link BudgetDebitor}</li>
 *   <li>Buffer allocation — a shared {@link BufferPool} and write buffer</li>
 *   <li>Scheduling — the {@link Signaler} for time-based and task-based signals</li>
 *   <li>Plugin resolution — guards, vaults, catalogs, models, and converters by id</li>
 *   <li>Metrics — counter, gauge, and histogram suppliers</li>
 *   <li>Namespace management — attaching and detaching composite namespaces</li>
 * </ul>
 * </p>
 *
 * @see Binding#supply(EngineContext)
 * @see Guard#supply(EngineContext)
 * @see Vault#supply(EngineContext)
 * @see Catalog#supply(EngineContext)
 * @see Exporter#supply(EngineContext)
 * @see Model#supply(EngineContext)
 */
public interface EngineContext
{
    /**
     * Returns the zero-based index of the I/O thread that owns this context.
     *
     * @return the thread index
     */
    int index();

    /**
     * Returns the {@link Signaler} for scheduling time-based and task-based signals on
     * this I/O thread.
     *
     * @return the signaler
     */
    Signaler signaler();

    /**
     * Resolves a protocol type name to its integer type id, registering the name if
     * it has not been seen before.
     *
     * @param name  the protocol type name (e.g., {@code "http"})
     * @return the corresponding integer type id
     */
    int supplyTypeId(
        String name);

    /**
     * Allocates a new initial (inbound) stream id for the given binding.
     *
     * @param bindingId  the binding id to associate with the new stream
     * @return a new unique initial stream id
     */
    long supplyInitialId(
        long bindingId);

    /**
     * Allocates a new initial (inbound) stream id for the given binding, selecting
     * a deterministic worker by {@code Math.floorMod(hash, mask.cardinality())}
     * over the binding's affinity mask. Used to pin per-key application streams to
     * a specific worker so per-worker session state remains valid across requests
     * for the same logical key.
     *
     * @param bindingId  the binding id to associate with the new stream
     * @param hash       opaque integer hash of the affinity key; same hash yields
     *                   the same worker for a given binding affinity mask
     * @return a new unique initial stream id pinned to the worker selected by hash
     */
    long supplyInitialId(
        long bindingId,
        int hash);

    /**
     * Returns the reply (outbound) stream id paired with the given initial stream id.
     *
     * @param initialId  the initial stream id
     * @return the corresponding reply stream id
     */
    long supplyReplyId(
        long initialId);

    /**
     * Allocates a new promise stream id derived from the given initial stream id,
     * used for server-push or async response scenarios.
     *
     * @param initialId  the initial stream id
     * @return a new promise stream id
     */
    long supplyPromiseId(
            long initialId);

    /**
     * Allocates a new authorization context id for use in authorized stream interactions.
     *
     * @return a new authorized context id
     */
    long supplyAuthorizedId();

    /**
     * Allocates a new unique budget id for flow control.
     *
     * @return a new budget id
     */
    long supplyBudgetId();

    /**
     * Returns a new trace id for correlating frames across a request/response lifecycle.
     *
     * @return a new trace id
     */
    long supplyTraceId();

    /**
     * Returns the {@link MessageConsumer} for sending frames to the stream identified by
     * the given stream id.
     *
     * @param streamId  the stream id to look up
     * @return the sender for that stream, or a no-op consumer if the stream is not found
     */
    MessageConsumer supplySender(
        long streamId);

    /**
     * Returns the {@link MessageConsumer} for delivering frames received on the given stream id
     * to its registered handler.
     *
     * @param streamId  the stream id to look up
     * @return the receiver for that stream
     */
    MessageConsumer supplyReceiver(
        long streamId);

    /**
     * Returns an {@link EventFormatter} for formatting structured engine events into
     * human-readable strings for logging or export.
     *
     * @return the event formatter
     */
    EventFormatter supplyEventFormatter();

    /**
     * Reports an unexpected exception to the engine's error handling mechanism.
     *
     * @param ex  the exception to report
     */
    void report(
        Throwable ex);

    /**
     * Attaches a composite namespace configuration to this engine context, making its
     * bindings and resources available for routing.
     *
     * @param composite  the namespace configuration to attach
     */
    void attachComposite(
        NamespaceConfig composite);

    /**
     * Detaches a previously attached composite namespace configuration.
     *
     * @param composite  the namespace configuration to detach
     */
    void detachComposite(
        NamespaceConfig composite);

    /**
     * Removes the sender registration for the given reply stream id, releasing routing
     * table resources when a stream is closed.
     *
     * @param replyId  the reply stream id to deregister
     */
    void detachSender(
        long replyId);

    /**
     * Forcibly detaches all active streams associated with the given binding id.
     * Used during binding teardown to ensure clean shutdown.
     *
     * @param bindingId  the binding id whose streams should be detached
     */
    void detachStreams(
        long bindingId);

    /**
     * Returns the {@link BudgetCreditor} for this thread, used to issue flow control credits
     * to upstream senders.
     *
     * @return the budget creditor
     */
    BudgetCreditor creditor();

    /**
     * Returns a {@link BudgetDebitor} for the given budget id, used to claim send capacity
     * before writing frames downstream.
     *
     * @param budgetId  the budget id to obtain a debitor for
     * @return the budget debitor
     */
    BudgetDebitor supplyDebitor(
        long budgetId);

    /**
     * Returns the per-thread write buffer for staging outbound frames before sending.
     * The buffer is exclusively owned by this thread.
     *
     * @return the mutable write buffer
     */
    MutableDirectBuffer writeBuffer();

    /**
     * Returns the shared {@link BufferPool} for this thread, used to temporarily hold
     * partial payloads that cannot be forwarded immediately.
     *
     * @return the buffer pool
     */
    BufferPool bufferPool();

    /**
     * Returns a {@link java.util.function.LongSupplier} that reads the current value of the
     * counter metric identified by {@code (bindingId, metricId, attributesId)}.
     *
     * @param bindingId     the namespaced binding id
     * @param metricId      the metric label id
     * @param attributesId  the attributes label id
     * @return a supplier reading the counter value
     */
    LongSupplier supplyCounter(
        long bindingId,
        int metricId,
        int attributesId);

    /**
     * Returns a {@link java.util.function.LongSupplier} that reads the current value of the
     * gauge metric identified by {@code (bindingId, metricId, attributesId)}.
     *
     * @param bindingId     the namespaced binding id
     * @param metricId      the metric label id
     * @param attributesId  the attributes label id
     * @return a supplier reading the gauge value
     */
    LongSupplier supplyGauge(
        long bindingId,
        int metricId,
        int attributesId);

    /**
     * Returns an array of {@link java.util.function.LongSupplier}s, one per histogram bucket,
     * for the histogram metric identified by {@code (bindingId, metricId, attributesId)}.
     *
     * @param bindingId     the namespaced binding id
     * @param metricId      the metric label id
     * @param attributesId  the attributes label id
     * @return an array of bucket value suppliers
     */
    LongSupplier[] supplyHistogram(
        long bindingId,
        int metricId,
        int attributesId);

    /**
     * Returns the {@link MessageConsumer} to which frames that cannot be routed should be
     * delivered, allowing them to be counted or logged as dropped.
     *
     * @return the dropped-frame handler
     */
    MessageConsumer droppedFrameHandler();

    /**
     * Returns the I/O thread index that owns the given stream id, used when a plugin needs
     * to dispatch work to the thread responsible for a particular stream.
     *
     * @param streamId  the stream id to look up
     * @return the owning thread index
     */
    int supplyClientIndex(
        long streamId);

    /**
     * Resolves a hostname to its {@link java.net.InetAddress} array, using the engine's
     * configured DNS resolver.
     *
     * @param host  the hostname to resolve
     * @return the resolved addresses, or an empty array if resolution fails
     */
    InetAddress[] resolveHost(
        String host);

    /**
     * Returns a {@link PollerKey} wrapping the given NIO channel, registering it with this
     * thread's I/O poller for event notifications.
     *
     * @param channel  the NIO channel to register
     * @return the poller key for the channel
     */
    PollerKey supplyPollerKey(
        SelectableChannel channel);

    /**
     * Returns the namespaced binding id for the given namespace and binding configuration pair.
     *
     * @param namespace  the namespace configuration
     * @param binding    the binding configuration
     * @return the combined namespaced binding id
     */
    long supplyBindingId(
        NamespaceConfig namespace,
        BindingConfig binding);

    /**
     * Resolves the namespace name component of a namespaced id.
     *
     * @param namespacedId  the namespaced id
     * @return the namespace name string
     */
    String supplyNamespace(
        long namespacedId);

    /**
     * Resolves the local (binding) name component of a namespaced id.
     *
     * @param namespacedId  the namespaced id
     * @return the local name string
     */
    String supplyLocalName(
        long namespacedId);

    /**
     * Resolves the fully-qualified name ({@code namespace.localName}) for a namespaced id.
     *
     * @param namespacedId  the namespaced id
     * @return the qualified name string
     */
    String supplyQName(
        long namespacedId);

    /**
     * Resolves an event type name to its integer event id, registering it if necessary.
     *
     * @param name  the event type name
     * @return the integer event id
     */
    int supplyEventId(
        String name);

    /**
     * Resolves an integer event id back to its event type name.
     *
     * @param eventId  the event id
     * @return the event type name
     */
    String supplyEventName(
        int eventId);

    /**
     * Returns the engine-wide {@link BindingHandler} stream factory, used by bindings to
     * open new streams to other bindings by routing id.
     *
     * @return the stream factory
     */
    BindingHandler streamFactory();

    /**
     * Returns the {@link GuardHandler} for the given guard id, previously registered via
     * the guard's {@link GuardContext}.
     *
     * @param guardId  the guard id
     * @return the guard handler, or {@code null} if not found
     */
    GuardHandler supplyGuard(
        long guardId);

    /**
     * Returns the {@link StoreHandler} for the given store id, previously registered via
     * the store's {@link io.aklivity.zilla.runtime.engine.store.StoreContext}.
     *
     * @param storeId  the store id
     * @return the store handler, or {@code null} if not found
     */
    StoreHandler supplyStore(
        long storeId);

    /**
     * Returns the externally-reachable hostname for this engine instance, configured via
     * {@code zilla.engine.service.hostname}. Used by bindings (e.g. http) to compose a
     * per-instance authority for cross-instance redirect responses. The port portion of
     * the authority is composed from each network connection's destination port at use site.
     *
     * @return the configured service hostname, or {@code null} if unset
     */
    String serviceHostname();

    /**
     * Returns the engine-level shared store handle, resolved from the qualified name
     * configured via {@code zilla.engine.store.name} (default {@code sys:state}). When
     * {@code zilla.engine.store.type} is unset and the user has not declared a store
     * binding under the configured name, this returns {@code null}.
     *
     * @return the engine store handle, or {@code null} if no store is configured
     */
    StoreHandler store();

    /**
     * Returns the {@link VaultHandler} for the given vault id.
     *
     * @param vaultId  the vault id
     * @return the vault handler, or {@code null} if not found
     */
    VaultHandler supplyVault(
        long vaultId);

    /**
     * Returns the {@link CatalogHandler} for the given catalog id.
     *
     * @param catalogId  the catalog id
     * @return the catalog handler, or {@code null} if not found
     */
    CatalogHandler supplyCatalog(
        long catalogId);

    /**
     * Returns a {@link ValidatorHandler} configured for the given model configuration.
     *
     * @param config  the model configuration
     * @return the validator handler, or {@code null} if the model does not support validation
     */
    ValidatorHandler supplyValidator(
        ModelConfig config);

    /**
     * Returns a {@link ConverterHandler} for converting inbound (read) payloads according
     * to the given model configuration.
     *
     * @param config  the model configuration
     * @return the read converter handler
     */
    ConverterHandler supplyReadConverter(
        ModelConfig config);

    /**
     * Returns a {@link ConverterHandler} for converting outbound (write) payloads according
     * to the given model configuration.
     *
     * @param config  the model configuration
     * @return the write converter handler
     */
    ConverterHandler supplyWriteConverter(
        ModelConfig config);

    /**
     * Returns a {@link LongConsumer} that records CPU utilization samples for this thread.
     *
     * @return the utilization metric writer
     */
    LongConsumer supplyUtilizationMetric();

    /**
     * Resolves a path string relative to the engine's configured data directory.
     *
     * @param location  the relative or absolute path string
     * @return the resolved absolute {@link java.nio.file.Path}
     */
    Path resolvePath(
        String location);

    /**
     * Resolves a path string relative to the engine's local (working) directory.
     *
     * @param location  the relative or absolute path string
     * @return the resolved absolute {@link java.nio.file.Path}
     */
    Path resolveLocalPath(
            String location);

    /**
     * Resolves a metric name to its {@link Metric} descriptor.
     *
     * @param name  the fully-qualified metric name (e.g., {@code "http.request.size"})
     * @return the {@link Metric} descriptor, or {@code null} if not found
     */
    Metric resolveMetric(
        String name);

    /**
     * Notifies the engine that an exporter has been attached and is ready to consume events.
     *
     * @param exporterId  the exporter id
     */
    void onExporterAttached(
        long exporterId);

    /**
     * Notifies the engine that an exporter has been detached and should no longer receive events.
     *
     * @param exporterId  the exporter id
     */
    void onExporterDetached(
        long exporterId);

    /**
     * Returns a {@link LongConsumer} that writes a metric value for the given binding, metric,
     * and attributes, using the appropriate recording mechanism for the metric's kind.
     *
     * @param kind          the metric kind (counter, gauge, or histogram)
     * @param bindingId     the namespaced binding id
     * @param metricId      the metric label id
     * @param attributesId  the attributes label id encoding user-defined dimensions
     * @return a consumer that records metric values
     */
    LongConsumer supplyMetricWriter(
        Metric.Kind kind,
        long bindingId,
        int metricId,
        int attributesId);

    /**
     * Returns a {@link MessageConsumer} for writing structured event frames to the engine's
     * event ring buffer, to be consumed by attached exporters.
     *
     * @return the event writer
     */
    MessageConsumer supplyEventWriter();

    /**
     * Returns a {@link MessageReader} for reading structured event frames from the engine's
     * event ring buffer.
     *
     * @return the event reader
     */
    MessageReader supplyEventReader();

    /**
     * Returns the engine's configured {@link java.time.Clock}, used for consistent time
     * measurements across all components on this thread.
     *
     * @return the clock
     */
    Clock clock();

    /**
     * Dispatches a task for execution.
     * <p>
     * The default implementation executes the task inline on the calling thread.
     * Override to schedule the task on a specific thread or executor.
     * </p>
     *
     * @param task  the task to execute
     */
    default void dispatch(
        Runnable task)
    {
        task.run();
    }
}
