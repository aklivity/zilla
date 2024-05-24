/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.dump.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.command.dump.internal.test.DumpRule;

public class AmqpLinkNetworkIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/runtime/command/dump/binding/amqp/streams/network/link");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final DumpRule dump = new DumpRule()
        .labels("test", "net0", "amqp", "server")
        .bindings("test.net0", "test.amqp", "test.server", "test.0", "test.amqp");

    @Rule
    public final TestRule chain = outerRule(dump).around(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/attach.as.receiver.only/client",
        "${net}/attach.as.receiver.only/server"})
    public void shouldExchangeAttachAsReceiver() throws Exception
    {
        k3po.finish();
    }
}
