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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7540.server;

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_SERVER_CONCURRENT_STREAMS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

@Ignore ("TODO")
public class AccessControlIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/cross.origin")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7540/cross.origin");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(HTTP_SERVER_CONCURRENT_STREAMS, 100)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v2")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.access.control.cross.origin.json")
    @Specification({
        "${net}/allow.credentials.cookie/client",
        "${app}/allow.credentials.cookie/server" })
    public void shouldAllowCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.allow.credentials.json")
    @Specification({
        "${net}/allow.methods.explicit/client",
        "${app}/allow.methods.explicit/server",
    })
    public void shouldAllowMethodsExplicitWhenCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.json")
    @Specification({
        "${net}/allow.methods.wildcard/client",
        "${app}/allow.methods.wildcard/server",
    })
    public void shouldAllowMethodsWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.allow.credentials.json")
    @Specification({
        "${net}/allow.headers.explicit/client",
        "${app}/allow.headers.explicit/server",
    })
    public void shouldAllowHeadersExplicitWhenCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.json")
    @Specification({
        "${net}/allow.headers.wildcard/client",
        "${app}/allow.headers.wildcard/server",
    })
    public void shouldAllowHeadersWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.allow.json")
    @Specification({
        "${net}/allow.origin.explicit/client",
        "${app}/allow.origin.explicit/server",
    })
    public void shouldAllowOriginExplicitWhenNotWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.allow.credentials.json")
    @Specification({
        "${net}/allow.origin.explicit/client",
        "${app}/allow.origin.explicit/server",
    })
    public void shouldAllowOriginExplicitWhenCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.json")
    @Specification({
        "${net}/allow.origin.wildcard/client",
        "${app}/allow.origin.wildcard/server",
    })
    public void shouldAllowOriginWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.allow.json")
    @Specification({
        "${net}/cache.allow.methods.explicit/client",
        "${app}/cache.allow.methods.explicit/server",
    })
    public void shouldCacheAllowMethodsExplicitWhenNotWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.allow.credentials.json")
    @Specification({
        "${net}/cache.allow.methods.explicit/client",
        "${app}/cache.allow.methods.explicit/server",
    })
    public void shouldCacheAllowMethodsExplicitWhenCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.json")
    @Specification({
        "${net}/cache.allow.methods.wildcard/client",
        "${app}/cache.allow.methods.wildcard/server",
    })
    public void shouldCacheAllowMethodsWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.allow.json")
    @Specification({
        "${net}/cache.allow.headers.explicit/client",
        "${app}/cache.allow.headers.explicit/server",
    })
    public void shouldCacheAllowHeadersExplicitWhenNotWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.allow.credentials.json")
    @Specification({
        "${net}/cache.allow.headers.explicit/client",
        "${app}/cache.allow.headers.explicit/server",
    })
    public void shouldCacheAllowHeadersExplicitWhenCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.json")
    @Specification({
        "${net}/cache.allow.headers.wildcard/client",
        "${app}/cache.allow.headers.wildcard/server",
    })
    public void shouldCacheAllowHeadersWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.expose.json")
    @Specification({
        "${net}/expose.headers.explicit/client",
        "${app}/expose.headers.explicit/server",
    })
    public void shouldExposeHeadersExplicitWhenNotWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.allow.credentials.json")
    @Specification({
        "${net}/expose.headers.explicit/client",
        "${app}/expose.headers.explicit/server",
    })
    public void shouldExposeHeadersExplicitWhenCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.json")
    @Specification({
        "${net}/expose.headers.wildcard/client",
        "${app}/expose.headers.wildcard/server",
    })
    public void shouldExposeHeadersWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.allow.json")
    @Specification({
        "${net}/reject.origin.not.allowed/client",
    })
    public void shouldRejectOriginNotAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.allow.json")
    @Specification({
        "${net}/reject.method.not.allowed/client",
    })
    public void shouldRejectMethodNotAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.allow.json")
    @Specification({
        "${net}/reject.header.not.allowed/client",
    })
    public void shouldRejectHeaderNotAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.same.origin.json")
    @Specification({
        "${net}/allow.origin.same.origin/client",
        "${app}/allow.origin.same.origin/server",
    })
    public void shouldAllowOriginWhenSameOrigin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.same.origin.allow.json")
    @Specification({
        "${net}/reject.origin.not.allowed/client",
    })
    public void shouldRejectOriginNotSameOrigin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.same.origin.json")
    @Specification({
        "${net}/allow.origin.omitted/client",
        "${app}/allow.origin.omitted/server",
    })
    public void shouldAllowOriginOmittedWhenSameOrigin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.access.control.cross.origin.json")
    @Specification({
        "${net}/allow.origin.omitted/client",
        "${app}/allow.origin.omitted/server",
    })
    public void shouldAllowOriginOmittedWhenCrossOrigin() throws Exception
    {
        k3po.finish();
    }
}
