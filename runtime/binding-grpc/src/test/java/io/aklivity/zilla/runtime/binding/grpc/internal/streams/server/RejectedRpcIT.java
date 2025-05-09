/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.streams.server;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class RejectedRpcIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/grpc/streams/network/rejected.rpc");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/binding/grpc/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/method.not.post/client"
    })
    public void shouldRejectMethodNotPost() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/unrecognized.rpc/client"
    })
    public void shouldRejectUnrecognizedMethod() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/unsupported.content.type/client"
    })
    public void shouldRejectUnsupportedContentType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.when.yaml")
    @Specification({
        "${net}/missing.te.trailers/client",
    })
    public void shouldRejectMissingTeTrailer() throws Exception
    {
        k3po.finish();
    }

}
