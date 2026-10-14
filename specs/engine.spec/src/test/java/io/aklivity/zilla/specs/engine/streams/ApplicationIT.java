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

public class ApplicationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/engine/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/value.envelope/client",
        "${app}/value.envelope/server" })
    public void shouldExchangeValueEnvelope() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/value.envelope.bootstrap/client",
        "${app}/value.envelope.bootstrap/server" })
    public void shouldExchangeValueEnvelopeBootstrap() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.modify.via.file/client",
        "${app}/reconfigure.modify.via.file/server" })
    public void shouldReconfigureWhenModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.modify.complex.chain.via.file/client",
        "${app}/reconfigure.modify.complex.chain.via.file/server" })
    public void shouldReconfigureWhenModifiedUsingComplexSymlinkChain() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.create.via.file/client",
        "${app}/reconfigure.create.via.file/server" })
    public void shouldReconfigureWhenCreated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.delete.via.file/client",
        "${app}/reconfigure.delete.via.file/server" })
    public void shouldReconfigureWhenDeleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.not.modify.parse.failed.via.file/server",
        "${app}/reconfigure.not.modify.parse.failed.via.file/client"
    })
    public void shouldNotReconfigureWhenModifiedButParseFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.modify.via.http/client",
        "${app}/reconfigure.modify.via.http/server" })
    public void shouldReconfigureWhenModifiedViaHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.create.via.http/client",
        "${app}/reconfigure.create.via.http/server" })
    public void shouldReconfigureWhenCreatedViaHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.create.via.http.basic.auth/client",
        "${app}/reconfigure.create.via.http.basic.auth/server" })
    public void shouldReconfigureWhenCreatedViaHttpBasicAuth() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.delete.via.http/client",
        "${app}/reconfigure.delete.via.http/server" })
    public void shouldReconfigureWhenDeletedViaHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.modify.no.etag.via.http/server",
        "${app}/reconfigure.modify.no.etag.via.http/client"
    })
    public void shouldReconfigureWhenModifiedViaHttpEtagNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.server.error.via.http/server",
        "${app}/reconfigure.server.error.via.http/client"
    })
    public void shouldNotReconfigureViaHttpWhenServerError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.identity.identity/client",
        "${app}/exchange.value.pipeline.identity.identity/server" })
    public void shouldExchangeValuePipelineIdentityIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.transform.identity/client",
        "${app}/exchange.value.pipeline.transform.identity/server" })
    public void shouldExchangeValuePipelineTransformIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.identity.transform/client",
        "${app}/exchange.value.pipeline.identity.transform/server" })
    public void shouldExchangeValuePipelineIdentityTransform() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.transform.transform/client",
        "${app}/exchange.value.pipeline.transform.transform/server" })
    public void shouldExchangeValuePipelineTransformTransform() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.transform.transform.shrink.grow/client",
        "${app}/exchange.value.pipeline.transform.transform.shrink.grow/server" })
    public void shouldExchangeValuePipelineTransformTransformShrinkGrow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.fragmented.identity/client",
        "${app}/exchange.value.pipeline.fragmented.identity/server" })
    public void shouldExchangeValuePipelineFragmentedIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.fragmented.transform.identity/client",
        "${app}/exchange.value.pipeline.fragmented.transform.identity/server" })
    public void shouldExchangeValuePipelineFragmentedTransformIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.fragmented.identity.transform/client",
        "${app}/exchange.value.pipeline.fragmented.identity.transform/server" })
    public void shouldExchangeValuePipelineFragmentedIdentityTransform() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.fragmented.reject/client",
        "${app}/exchange.value.pipeline.fragmented.reject/server" })
    public void shouldExchangeValuePipelineFragmentedReject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.overflow.identity/client",
        "${app}/exchange.value.pipeline.overflow.identity/server" })
    public void shouldExchangeValuePipelineOverflowIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.overflow.transform/client",
        "${app}/exchange.value.pipeline.overflow.transform/server" })
    public void shouldExchangeValuePipelineOverflowTransform() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.suspend.second/client",
        "${app}/exchange.value.pipeline.suspend.second/server" })
    public void shouldExchangeValuePipelineSuspendSecond() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.suspend.first/client",
        "${app}/exchange.value.pipeline.suspend.first/server" })
    public void shouldExchangeValuePipelineSuspendFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.nondeterministic/client",
        "${app}/exchange.value.pipeline.nondeterministic/server" })
    public void shouldExchangeValuePipelineNondeterministic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/exchange.value.pipeline.identity.run/client",
        "${app}/exchange.value.pipeline.identity.run/server" })
    public void shouldExchangeValuePipelineIdentityRun() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.value.pipeline.first/client",
        "${app}/reject.value.pipeline.first/server" })
    public void shouldRejectValuePipelineFirst() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.value.pipeline.second/client",
        "${app}/reject.value.pipeline.second/server" })
    public void shouldRejectValuePipelineSecond() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.value.pipeline.assertion/client",
        "${app}/reject.value.pipeline.assertion/server" })
    public void shouldRejectValuePipelineAssertion() throws Exception
    {
        k3po.finish();
    }
}
