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
package io.aklivity.zilla.specs.engine.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class NetworkIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/engine/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/handshake.authorized/client",
        "${net}/handshake.authorized/server" })
    public void shouldHandshakeAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/value.envelope/client",
        "${net}/value.envelope/server" })
    public void shouldExchangeValueEnvelope() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/value.envelope.bootstrap/client",
        "${net}/value.envelope.bootstrap/server" })
    public void shouldExchangeValueEnvelopeBootstrap() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.modify.via.file/client",
        "${net}/reconfigure.modify.via.file/server" })
    public void shouldReconfigureWhenModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.modify.complex.chain.via.file/client",
        "${net}/reconfigure.modify.complex.chain.via.file/server" })
    public void shouldReconfigureWhenModifiedUsingComplexSymlinkChain() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.create.via.file/client",
        "${net}/reconfigure.create.via.file/server" })
    public void shouldReconfigureWhenCreated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.delete.via.file/client",
        "${net}/reconfigure.delete.via.file/server" })
    public void shouldReconfigureWhenDeleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.not.modify.parse.failed.via.file/server",
        "${net}/reconfigure.not.modify.parse.failed.via.file/client"
    })
    public void shouldNotReconfigureWhenModifiedButParseFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.modify.via.http/client",
        "${net}/reconfigure.modify.via.http/server" })
    public void shouldReconfigureWhenModifiedViaHttp() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("CONFIG_CHANGED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.create.via.http/client",
        "${net}/reconfigure.create.via.http/server" })
    public void shouldReconfigureWhenCreatedViaHttp() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("CONFIG_CREATED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.delete.via.http/client",
        "${net}/reconfigure.delete.via.http/server" })
    public void shouldReconfigureWhenDeletedViaHttp() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("CONFIG_DELETED");
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.modify.no.etag.via.http/server",
        "${net}/reconfigure.modify.no.etag.via.http/client"
    })
    public void shouldReconfigureWhenModifiedViaHttpEtagNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.server.error.via.http/server",
        "${net}/reconfigure.server.error.via.http/client"
    })
    public void shouldNotReconfigureViaHttpWhenServerError() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SERVER_ERROR");
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.identity.identity/client",
        "${net}/exchange.value.pipeline.identity.identity/server" })
    public void shouldExchangeValuePipelineIdentityIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.transform.identity/client",
        "${net}/exchange.value.pipeline.transform.identity/server" })
    public void shouldExchangeValuePipelineTransformIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.identity.transform/client",
        "${net}/exchange.value.pipeline.identity.transform/server" })
    public void shouldExchangeValuePipelineIdentityTransform() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.transform.transform/client",
        "${net}/exchange.value.pipeline.transform.transform/server" })
    public void shouldExchangeValuePipelineTransformTransform() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.transform.transform.shrink.grow/client",
        "${net}/exchange.value.pipeline.transform.transform.shrink.grow/server" })
    public void shouldExchangeValuePipelineTransformTransformShrinkGrow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.fragmented.identity/client",
        "${net}/exchange.value.pipeline.fragmented.identity/server" })
    public void shouldExchangeValuePipelineFragmentedIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.fragmented.transform.identity/client",
        "${net}/exchange.value.pipeline.fragmented.transform.identity/server" })
    public void shouldExchangeValuePipelineFragmentedTransformIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.fragmented.identity.transform/client",
        "${net}/exchange.value.pipeline.fragmented.identity.transform/server" })
    public void shouldExchangeValuePipelineFragmentedIdentityTransform() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.fragmented.reject/client",
        "${net}/exchange.value.pipeline.fragmented.reject/server" })
    public void shouldExchangeValuePipelineFragmentedReject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.overflow.identity/client",
        "${net}/exchange.value.pipeline.overflow.identity/server" })
    public void shouldExchangeValuePipelineOverflowIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.overflow.transform/client",
        "${net}/exchange.value.pipeline.overflow.transform/server" })
    public void shouldExchangeValuePipelineOverflowTransform() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.suspend.second/client",
        "${net}/exchange.value.pipeline.suspend.second/server" })
    public void shouldExchangeValuePipelineSuspendSecond() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.suspend.first/client",
        "${net}/exchange.value.pipeline.suspend.first/server" })
    public void shouldExchangeValuePipelineSuspendFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.nondeterministic/client",
        "${net}/exchange.value.pipeline.nondeterministic/server" })
    public void shouldExchangeValuePipelineNondeterministic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/exchange.value.pipeline.identity.run/client",
        "${net}/exchange.value.pipeline.identity.run/server" })
    public void shouldExchangeValuePipelineIdentityRun() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.value.pipeline.first/client",
        "${net}/reject.value.pipeline.first/server" })
    public void shouldRejectValuePipelineFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.value.pipeline.second/client",
        "${net}/reject.value.pipeline.second/server" })
    public void shouldRejectValuePipelineSecond() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.value.pipeline.assertion/client",
        "${net}/reject.value.pipeline.assertion/server" })
    public void shouldRejectValuePipelineAssertion() throws Exception
    {
        k3po.finish();
    }
}
