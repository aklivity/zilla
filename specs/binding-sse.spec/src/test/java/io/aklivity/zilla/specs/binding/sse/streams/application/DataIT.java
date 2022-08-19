/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.sse.streams.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class DataIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/sse/streams/application/data");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/name.only/client",
        "${app}/name.only/server" })
    public void shouldReceiveDataNameOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/empty/client",
        "${app}/empty/server" })
    public void shouldReceiveEmptyData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/non.empty/client",
        "${app}/non.empty/server" })
    public void shouldReceiveNonEmptyData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/fragmented.10k/client",
        "${app}/fragmented.10k/server" })
    public void shouldReceiveDataFragmented10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/fragmented.100k/client",
        "${app}/fragmented.100k/server" })
    public void shouldReceiveDataFragmented100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/invalid.utf8/client",
        "${app}/invalid.utf8/server" })
    public void shouldRejectDataWithInvalidUTF8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/initial.whitespace/client",
        "${app}/initial.whitespace/server" })
    public void shouldReceiveDataWithInitialWhitespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/multi.line/client",
        "${app}/multi.line/server" })
    public void shouldReceiveMultiLineData() throws Exception
    {
        k3po.finish();
    }
}
