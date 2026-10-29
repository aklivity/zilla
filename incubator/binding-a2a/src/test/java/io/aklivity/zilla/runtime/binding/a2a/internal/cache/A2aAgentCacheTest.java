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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.Closeable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aBindingConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.A2aConfiguration;
import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyBindingConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public class A2aAgentCacheTest
{
    private static final long CALLER = 1L;
    private static final long ANONYMOUS = 0L;

    private final EngineContext context = mock(EngineContext.class);
    private final StoreHandler store = mock(StoreHandler.class);
    private final GuardHandler guard = mock(GuardHandler.class);
    private final Signaler signaler = mock(Signaler.class);
    private final TestClock clock = new TestClock();
    private final List<String> hydrated = new ArrayList<>();
    private final List<String> aborted = new ArrayList<>();

    private A2aAgentCardSupplier supplier;
    private String storedCard;
    private String lockToken = "token";

    @Before
    public void init()
    {
        supplier = new A2aAgentCardSupplier(card("1.0.0"));
        when(context.supplyStore(7L)).thenReturn(store);
        when(context.supplyGuard(9L)).thenReturn(guard);
        when(context.signaler()).thenReturn(signaler);
        when(context.clock()).thenReturn(clock);
        when(guard.verify(CALLER, List.of("agents:forecast"))).thenReturn(true);
        when(guard.verify(ANONYMOUS, List.of("agents:forecast"))).thenReturn(false);

        doAnswer(invocation ->
        {
            invocation.<BiConsumer<String, String>>getArgument(2).accept(invocation.getArgument(0), lockToken);
            return null;
        }).when(store).lock(anyString(), any(), any());
        doAnswer(invocation ->
        {
            storedCard = invocation.getArgument(1);
            invocation.<Consumer<String>>getArgument(3).accept(invocation.getArgument(0));
            return null;
        }).when(store).put(anyString(), anyString(), any(), any());
        doAnswer(invocation ->
        {
            invocation.<BiConsumer<String, String>>getArgument(1).accept(invocation.getArgument(0), storedCard);
            return null;
        }).when(store).get(anyString(), any());
        doAnswer(invocation ->
        {
            invocation.<Consumer<String>>getArgument(2).accept(invocation.getArgument(1));
            return null;
        }).when(store).unlock(anyString(), anyString(), any());
        when(store.watch(anyString(), any())).thenReturn(mock(Closeable.class));
    }

    @Test
    public void shouldWarmOnStart()
    {
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));

        cache.start();

        assertThat(hydrated, contains("weather"));
        assertThat(cache.entryOf("weather").card(), equalTo(card("1.0.0")));
        assertThat(storedCard, equalTo(card("1.0.0")));
    }

    @Test
    public void shouldNotRefreshBeforeTtl()
    {
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));
        cache.start();

        final List<String> served = new ArrayList<>();
        clock.advance(Duration.ofMinutes(4));
        cache.request(cache.entryOf("weather"), () -> served.add("served"));

        assertThat(hydrated, hasSize(1));
        assertThat(served, hasSize(1));
    }

    @Test
    public void shouldRefreshAfterTtl()
    {
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));
        cache.start();

        supplier.card = card("1.1.0");
        clock.advance(Duration.ofMinutes(5));
        cache.request(cache.entryOf("weather"), () -> {});

        assertThat(hydrated, hasSize(2));
        assertThat(cache.entryOf("weather").card(), equalTo(card("1.1.0")));
    }

    @Test
    public void shouldKeepLastGoodCardWhenRefreshFails()
    {
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));
        cache.start();

        supplier.card = null;
        clock.advance(Duration.ofMinutes(5));
        cache.request(cache.entryOf("weather"), () -> {});

        assertThat(hydrated, hasSize(2));
        assertThat(cache.entryOf("weather").card(), equalTo(card("1.0.0")));

        supplier.card = card("1.1.0");
        cache.request(cache.entryOf("weather"), () -> {});
        assertThat(hydrated, hasSize(2));

        clock.advance(Duration.ofMinutes(5));
        cache.request(cache.entryOf("weather"), () -> {});
        assertThat(hydrated, hasSize(3));
        assertThat(cache.entryOf("weather").card(), equalTo(card("1.1.0")));
    }

    @Test
    public void shouldRetainFailureWithoutCard()
    {
        supplier.card = null;
        supplier.failure = new byte[] {1, 2, 3};
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));

        cache.start();

        assertThat(cache.entryOf("weather").card(), nullValue());
        assertThat(cache.entryOf("weather").failure(), equalTo(new byte[] {1, 2, 3}));
    }

    @Test
    public void shouldAdoptCardHydratedByPeer()
    {
        lockToken = null;
        storedCard = card("2.0.0");
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));

        cache.start();

        assertThat(hydrated, empty());
        assertThat(cache.entryOf("weather").card(), equalTo(card("2.0.0")));
    }

    @Test
    public void shouldAdoptCardFromStoreWatch()
    {
        lockToken = null;
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));
        cache.start();

        final List<String> served = new ArrayList<>();
        cache.request(cache.entryOf("weather"), () -> served.add("served"));
        assertThat(served, empty());

        org.mockito.ArgumentCaptor<BiConsumer<String, String>> watcher = org.mockito.ArgumentCaptor.forClass(BiConsumer.class);
        org.mockito.Mockito.verify(store).watch(eq("a2a/3/weather"), watcher.capture());
        watcher.getValue().accept("a2a/3/weather", card("3.0.0"));

        assertThat(served, hasSize(1));
        assertThat(cache.entryOf("weather").card(), equalTo(card("3.0.0")));
    }

    @Test
    public void shouldIgnoreUnparseableCard()
    {
        supplier.card = "not json";
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));

        cache.start();

        assertThat(cache.entryOf("weather").card(), nullValue());
    }

    @Test
    public void shouldListAgentsWithPermittedSkills()
    {
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));
        cache.start();

        final List<A2aCachedAgent> permitted = cache.list(CALLER);
        final List<A2aCachedAgent> anonymous = cache.list(ANONYMOUS);

        assertThat(permitted, hasSize(1));
        assertThat(permitted.get(0).agent, equalTo("weather"));
        assertThat(permitted.get(0).name, equalTo("Weather Agent"));
        assertThat(permitted.get(0).description, equalTo("Answers weather questions"));
        assertThat(permitted.get(0).skills, hasSize(2));
        assertThat(permitted.get(0).supportedInterfaces, hasSize(1));
        assertThat(permitted.get(0).securitySchemes.containsKey("oauth2"), equalTo(true));
        assertThat(anonymous, hasSize(1));
        assertThat(anonymous.get(0).skills, hasSize(1));
        assertThat(anonymous.get(0).skills.getJsonObject(0).getString("id"), equalTo("weather"));
    }

    @Test
    public void shouldNotListAgentWhenEverySkillRestricted()
    {
        supplier.card = card("1.0.0").replace("{\"id\":\"weather\",\"name\":\"Weather\"},", "");
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));
        cache.start();

        assertThat(cache.list(ANONYMOUS), empty());
        assertThat(cache.list(CALLER), hasSize(1));
    }

    @Test
    public void shouldNotListAgentWhenRouteUnauthorized()
    {
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));
        cache.start();

        assertThat(cache.list(2L), empty());
    }

    @Test
    public void shouldNotListAgentWithoutCard()
    {
        supplier.card = null;
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));
        cache.start();

        assertThat(cache.list(CALLER), empty());
    }

    @Test
    public void shouldRenderRestrictedSkillsByAuthorization()
    {
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));
        cache.start();
        final A2aAgentEntry entry = cache.entryOf("weather");

        assertThat(new String(entry.render(CALLER, guard)), equalTo(card("1.0.0")));
        assertThat(new String(entry.render(ANONYMOUS, guard)).contains("forecast"), equalTo(false));
        assertThat(new String(entry.render(ANONYMOUS, null)), equalTo(card("1.0.0")));
        assertThat(entry.requiredRoles(), contains("agents:forecast"));
    }

    @Test
    public void shouldReturnNullForUnknownEntry()
    {
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));

        assertThat(cache.entryOf("unknown"), nullValue());
        assertThat(cache.entryOf(null), nullValue());
    }

    @Test
    public void shouldStopHydrationInFlight()
    {
        supplier.deferred = true;
        final A2aAgentCache cache = cache(Duration.ofMinutes(5));
        cache.start();

        cache.stop();

        assertThat(cache.entryOf("weather").hydration, nullValue());
        assertThat(aborted, contains("weather"));
    }

    private A2aAgentCache cache(
        Duration ttl)
    {
        final BindingConfig config = A2aBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .kind(KindConfig.PROXY)
            .options()
                .cache()
                    .store("memory0")
                    .ttl(ttl)
                    .build()
                .build()
            .route()
                .exit("app1")
                .when()
                    .agent(List.of("weather"))
                    .build()
                .guarded()
                    .name("test0")
                    .role("agents:weather")
                    .build()
                .build()
            .build();
        config.id = 3L;
        config.resolveId = name -> name.equals("memory0") ? 7L : 9L;
        config.routes.get(0).id = 11L;
        config.routes.get(0).guarded.get(0).id = 9L;
        config.routes.get(0).authorized = (authorization, identity) -> authorization == CALLER || authorization == ANONYMOUS;

        final A2aProxyBindingConfig binding = new A2aProxyBindingConfig(config, context);

        return new A2aAgentCache(config, binding, new A2aConfiguration(new Configuration()), context,
            (entry, authorization, sink) ->
            {
                hydrated.add(entry.agent);
                supplier.supply(sink);
                return () -> aborted.add(entry.agent);
            });
    }

    private static String card(
        String version)
    {
        return CARD_JSON.formatted(version);
    }

    private static final String CARD_JSON =
        "{\"name\":\"Weather Agent\",\"description\":\"Answers weather questions\"," +
        "\"supportedInterfaces\":[{\"url\":\"http://localhost:9090/a2a\",\"protocolBinding\":\"JSONRPC\"," +
        "\"protocolVersion\":\"1.0\"}],\"securitySchemes\":{\"oauth2\":{}},\"version\":\"%s\"," +
        "\"skills\":[{\"id\":\"weather\",\"name\":\"Weather\"},{\"id\":\"forecast\",\"name\":\"Forecast\"," +
        "\"securityRequirements\":[{\"schemes\":{\"oauth2\":{\"list\":[\"agents:forecast\"]}}}]}]}";

    private static final class A2aAgentCardSupplier
    {
        private String card;
        private byte[] failure;
        private boolean deferred;

        A2aAgentCardSupplier(
            String card)
        {
            this.card = card;
        }

        void supply(
            A2aCardSink sink)
        {
            if (deferred)
            {
                return;
            }

            if (card != null)
            {
                sink.onCard(card);
            }
            else
            {
                sink.onFailure(failure);
            }
        }
    }

    private static final class TestClock extends Clock
    {
        private long now = 1_000_000L;

        void advance(
            Duration duration)
        {
            now += duration.toMillis();
        }

        @Override
        public ZoneId getZone()
        {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(
            ZoneId zone)
        {
            return this;
        }

        @Override
        public Instant instant()
        {
            return Instant.ofEpochMilli(now);
        }

        @Override
        public long millis()
        {
            return now;
        }
    }
}
