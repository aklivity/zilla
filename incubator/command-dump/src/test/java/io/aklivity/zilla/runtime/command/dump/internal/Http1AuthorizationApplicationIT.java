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

import io.aklivity.zilla.runtime.command.dump.internal.test.DumpRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class Http1AuthorizationApplicationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/runtime/command/dump/binding/http/streams/application/rfc7230/authorization");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final DumpRule dump = new DumpRule()
        .addConfigurationRoot("io/aklivity/zilla/runtime/command/dump/binding/http/config/v1.1")
        .addLabels("server") // 4
        .bindings("02000000 01000000  03000000 01000000  04000000 01000000  00000000 01000000  03000000 01000000");

    @Rule
    public final TestRule chain = outerRule(dump).around(k3po).around(timeout);

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${app}/challenge.credentials.header/client",
        "${app}/challenge.credentials.header/server",
    })
    public void shouldChallengeCredentialsHeader() throws Exception
    {
        k3po.finish();
    }
}
