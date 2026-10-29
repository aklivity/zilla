/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.a2a.internal.cache;

import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;

import java.io.Closeable;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.agrona.CloseHelper;

import io.aklivity.zilla.config.binding.a2a.A2aCacheConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.A2aConfiguration;
import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyBindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyRouteConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class A2aAgentCache implements A2aAgentCards
{
    private static final Duration STORE_TTL_FOREVER = null;
    private static final long NEVER = 0L;

    private final A2aProxyBindingConfig binding;
    private final A2aCardHydrater hydrater;
    private final StoreHandler store;
    private final Signaler signaler;
    private final Clock clock;
    private final EngineContext context;
    private final Duration ttl;
    private final Duration leaseTtl;
    private final GuardHandler guard;
    private final String credentials;
    private final Map<String, A2aAgentEntry> entries;
    private final List<Closeable> watches;

    private long authorization;

    public A2aAgentCache(
        BindingConfig config,
        A2aProxyBindingConfig binding,
        A2aConfiguration configuration,
        EngineContext context,
        A2aCardHydrater hydrater)
    {
        final A2aCacheConfig cache = binding.cache;

        this.binding = binding;
        this.hydrater = hydrater;
        this.context = context;
        this.store = context.supplyStore(config.resolveId.applyAsLong(cache.store));
        this.signaler = context.signaler();
        this.clock = context.clock();
        this.ttl = cache.ttl;
        this.leaseTtl = configuration.cacheLeaseTtl();
        this.guard = Optional.ofNullable(cache.authorization)
            .map(a -> a.name)
            .map(config.resolveId::applyAsLong)
            .map(context::supplyGuard)
            .orElse(null);
        this.credentials = Optional.ofNullable(cache.authorization)
            .map(a -> a.credentials)
            .orElse(null);
        this.entries = new LinkedHashMap<>();
        this.watches = new ArrayList<>();

        for (A2aProxyRouteConfig route : binding.routes)
        {
            for (String agent : route.agents)
            {
                entries.putIfAbsent(agent, new A2aAgentEntry(binding.id, agent, route));
            }
        }
    }

    public A2aAgentEntry entryOf(
        String agent)
    {
        return agent != null ? entries.get(agent) : null;
    }

    public void start()
    {
        for (A2aAgentEntry entry : entries.values())
        {
            watches.add(store.watch(entry.key, (k, v) -> onStoreChanged(entry, v)));
        }

        warm(entries.values().iterator());
    }

    public void stop()
    {
        watches.forEach(CloseHelper::quietClose);
        watches.clear();

        for (A2aAgentEntry entry : entries.values())
        {
            if (entry.hydration != null)
            {
                entry.hydration.cancel();
            }
        }
    }

    public void request(
        A2aAgentEntry entry,
        Runnable ready)
    {
        entry.waiters.add(ready);

        if (entry.hydration == null)
        {
            if (entry.attemptedAt == NEVER || clock.millis() - entry.attemptedAt >= ttl.toMillis())
            {
                hydrate(entry);
            }
            else
            {
                settle(entry);
            }
        }
    }

    @Override
    public List<A2aCachedAgent> list(
        long authorization)
    {
        final List<A2aCachedAgent> agents = new ArrayList<>();

        for (A2aAgentEntry entry : entries.values())
        {
            if (entry.card() != null && entry.route.authorized(authorization))
            {
                final var visible = entry.visibleCard(authorization, binding.guard);
                if (visible != null)
                {
                    agents.add(new A2aCachedAgent(entry.agent, visible));
                }
            }
        }

        return agents;
    }

    private void warm(
        Iterator<A2aAgentEntry> pending)
    {
        if (pending.hasNext())
        {
            request(pending.next(), () -> warm(pending));
        }
    }

    private void hydrate(
        A2aAgentEntry entry)
    {
        final Hydration hydration = new Hydration(entry);
        entry.hydration = hydration;
        store.lock(entry.lockKey, leaseTtl, (k, token) -> hydration.onLock(token));
    }

    private void onStoreChanged(
        A2aAgentEntry entry,
        String card)
    {
        if (card != null)
        {
            entry.adopt(card);
            entry.attemptedAt = clock.millis();

            if (entry.hydration != null && entry.hydration.peer())
            {
                entry.hydration.finish();
            }
        }
    }

    private void settle(
        A2aAgentEntry entry)
    {
        final List<Runnable> ready = new ArrayList<>(entry.waiters);
        entry.waiters.clear();
        ready.forEach(Runnable::run);
    }

    private long hydrationAuthorization()
    {
        if (guard != null)
        {
            final long now = clock.millis();
            if (authorization == GuardHandler.NOT_AUTHORIZED || guard.expiresAt(authorization) <= now)
            {
                authorization = guard.reauthorize(context.supplyTraceId(), binding.id, 0L, credentials);
            }
        }

        return authorization;
    }

    final class Hydration implements A2aCardSink
    {
        private final A2aAgentEntry entry;

        private String token;
        private Runnable abort = () -> {};
        private long leaseId = NO_CANCEL_ID;

        Hydration(
            A2aAgentEntry entry)
        {
            this.entry = entry;
        }

        boolean peer()
        {
            return token == null;
        }

        void onLock(
            String token)
        {
            this.token = token;

            if (token != null)
            {
                leaseId = signaler.signalAt(clock.millis() + leaseTtl.toMillis(), 0, id -> abort.run());
                abort = hydrater.hydrate(entry, hydrationAuthorization(), this);
            }
            else
            {
                store.get(entry.key, this::onPeerValue);
            }
        }

        @Override
        public void onCard(
            String card)
        {
            entry.adopt(card);
            store.put(entry.key, card, STORE_TTL_FOREVER, k -> finish());
        }

        @Override
        public void onFailure(
            byte[] resetEx)
        {
            entry.failure = resetEx;
            finish();
        }

        void cancel()
        {
            abort.run();
            signaler.cancel(leaseId);
            leaseId = NO_CANCEL_ID;
            entry.hydration = null;
        }

        void finish()
        {
            if (entry.hydration == this)
            {
                signaler.cancel(leaseId);
                leaseId = NO_CANCEL_ID;
                entry.attemptedAt = clock.millis();
                entry.hydration = null;

                if (token != null)
                {
                    store.unlock(entry.lockKey, token, k -> {});
                }

                settle(entry);
            }
        }

        private void onPeerValue(
            String key,
            String card)
        {
            if (card != null)
            {
                entry.adopt(card);
                finish();
            }
            else
            {
                leaseId = signaler.signalAt(clock.millis() + leaseTtl.toMillis(), 0, id -> finish());
            }
        }
    }
}
