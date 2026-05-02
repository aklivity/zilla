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
package io.aklivity.zilla.specs.binding.http.streams.network.rfc7230;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class AffinityIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7230/affinity");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/request.with.header.affinity/client",
        "${net}/request.with.header.affinity/server"})
    public void shouldRequestWithHeaderAffinity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.header.affinity.match/client",
        "${net}/request.with.header.affinity.match/server"})
    public void shouldRequestWithHeaderAffinityMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.header.affinity.no.match/client",
        "${net}/request.with.header.affinity.no.match/server"})
    public void shouldRequestWithHeaderAffinityNoMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.header.affinity.missing/client",
        "${net}/request.with.header.affinity.missing/server"})
    public void shouldRequestWithHeaderAffinityMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.header.affinity.migrate/client",
        "${net}/request.with.header.affinity.migrate/server"})
    public void shouldRequestWithHeaderAffinityMigrate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.query.affinity/client",
        "${net}/request.with.query.affinity/server"})
    public void shouldRequestWithQueryAffinity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.query.affinity.match/client",
        "${net}/request.with.query.affinity.match/server"})
    public void shouldRequestWithQueryAffinityMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.query.affinity.no.match/client",
        "${net}/request.with.query.affinity.no.match/server"})
    public void shouldRequestWithQueryAffinityNoMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.query.affinity.missing/client",
        "${net}/request.with.query.affinity.missing/server"})
    public void shouldRequestWithQueryAffinityMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.query.affinity.migrate/client",
        "${net}/request.with.query.affinity.migrate/server"})
    public void shouldRequestWithQueryAffinityMigrate() throws Exception
    {
        k3po.finish();
    }
}
