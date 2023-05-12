/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.sse.streams.network;

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
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/sse/streams/network/data");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/name.only/request",
        "${net}/name.only/response" })
    public void shouldReceiveDataNameOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/empty/request",
        "${net}/empty/response" })
    public void shouldReceiveEmptyData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/non.empty/request",
        "${net}/non.empty/response" })
    public void shouldReceiveNonEmptyData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/multiple/request",
        "${net}/multiple/response" })
    public void shouldReceiveMultipleData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/fragmented.10k/request",
        "${net}/fragmented.10k/response" })
    public void shouldReceiveDataFragmented10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/fragmented.100k/request",
        "${net}/fragmented.100k/response" })
    public void shouldReceiveDataFragmented100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/invalid.utf8/request",
        "${net}/invalid.utf8/response" })
    public void shouldRejectDataWithInvalidUTF8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/initial.whitespace/request",
        "${net}/initial.whitespace/response" })
    public void shouldReceiveDataWithInitialWhitespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/multi.line/request",
        "${net}/multi.line/response" })
    public void shouldReceiveMultiLineData() throws Exception
    {
        k3po.finish();
    }
}
