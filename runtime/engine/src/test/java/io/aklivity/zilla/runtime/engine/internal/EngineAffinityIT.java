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
package io.aklivity.zilla.runtime.engine.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_ROUTER;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.rules.RuleChain.outerRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.internal.binding.TestBinding;
import io.aklivity.zilla.runtime.engine.test.internal.router.TestRouter;

public class EngineAffinityIT
{
    private static final String ENGINE_DIRECTORY = "target/zilla-itests";
    private static final long ROUTED_ID = 1L;
    private static final int WORKERS = 3;
    private static final long WORKER_INDEX_MASK = 0x00ff_ffffL;

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory(ENGINE_DIRECTORY)
        .configure(ENGINE_WORKERS, WORKERS)
        .configure(ENGINE_ROUTER, TestRouter.NAME)
        .configurationRoot("io/aklivity/zilla/specs/engine/config")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(timeout);

    @After
    public void resetRouter()
    {
        TestRouter.affinityResolver = null;
    }

    @Test
    @Configuration("server.yaml")
    public void shouldResolveSameAffinityForSameKeyHash()
    {
        EngineContext context = contexts().get(0);

        assertThat(context.resolveAffinityId(42L), equalTo(context.resolveAffinityId(42L)));
    }

    @Test
    @Configuration("server.yaml")
    public void shouldResolveSameAffinityOnEveryWorker()
    {
        List<EngineContext> contexts = contexts();

        for (long keyHash = -50L; keyHash < 50L; keyHash++)
        {
            long expected = contexts.get(0).resolveAffinityId(keyHash);
            for (EngineContext context : contexts)
            {
                assertThat(context.resolveAffinityId(keyHash), equalTo(expected));
            }
        }
    }

    @Test
    @Configuration("server.yaml")
    public void shouldSpreadKeyHashesAcrossAllWorkers()
    {
        EngineContext context = contexts().get(0);

        Set<Long> workers = new TreeSet<>();
        for (long keyHash = 0L; keyHash < 1000L; keyHash++)
        {
            workers.add(context.resolveAffinityId(keyHash) & WORKER_INDEX_MASK);
        }

        assertThat(workers, equalTo(Set.of(0L, 1L, 2L)));
    }

    @Test
    @Configuration("server.yaml")
    public void shouldResolveValidAffinityForAnyKeyHash()
    {
        EngineContext context = contexts().get(0);
        long local = context.affinity() & ~WORKER_INDEX_MASK;

        long[] keyHashes = { Long.MIN_VALUE, -7L, -1L, 0L, 1L, 7L, Long.MAX_VALUE };
        for (long keyHash : keyHashes)
        {
            long affinityId = context.resolveAffinityId(keyHash);

            assertThat(affinityId & ~WORKER_INDEX_MASK, equalTo(local));
            assertThat(affinityId & WORKER_INDEX_MASK, lessThan((long) WORKERS));
        }
    }

    @Test
    @Configuration("server.yaml")
    public void shouldPinInitialIdToResolvedWorker()
    {
        EngineContext context = contexts().get(0);

        for (long keyHash = 0L; keyHash < 30L; keyHash++)
        {
            long affinityId = context.resolveAffinityId(keyHash);

            long initialId = context.supplyInitialId(ROUTED_ID, affinityId);

            assertThat((initialId >> 48) & 0xffL, equalTo(affinityId & WORKER_INDEX_MASK));
        }
    }

    @Test
    @Configuration("server.yaml")
    public void shouldPreferRouterAffinityWithKeyHashAndEngineDefault()
    {
        EngineContext context = contexts().get(0);
        long engineDefault = context.resolveAffinityId(5L);
        AtomicLong observedKeyHash = new AtomicLong();
        AtomicLong observedDefault = new AtomicLong();
        TestRouter.affinityResolver = (keyHash, affinity) ->
        {
            observedKeyHash.set(keyHash);
            observedDefault.set(affinity);
            return 0x0000_0000_0100_0002L;
        };

        long affinityId = context.resolveAffinityId(5L);

        assertThat(affinityId, equalTo(0x0000_0000_0100_0002L));
        assertThat(observedKeyHash.get(), equalTo(5L));
        assertThat(observedDefault.get(), equalTo(engineDefault));
    }

    private List<EngineContext> contexts()
    {
        List<EngineContext> contexts = new ArrayList<>(engine.binding(TestBinding.class).contexts());
        assertThat(contexts, hasSize(WORKERS));
        return contexts;
    }
}
