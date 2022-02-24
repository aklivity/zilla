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
package io.aklivity.zilla.specs.binding.http.streams.network.rfc7230;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class CrossOriginIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7230/cross.origin");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/allow.credentials.cookie/client",
        "${net}/allow.credentials.cookie/server",
    })
    public void shouldAllowCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/allow.methods.explicit/client",
        "${net}/allow.methods.explicit/server",
    })
    public void shouldAllowMethodsExplicit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/allow.methods.wildcard/client",
        "${net}/allow.methods.wildcard/server",
    })
    public void shouldAllowMethodsWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/allow.headers.explicit/client",
        "${net}/allow.headers.explicit/server",
    })
    public void shouldAllowHeadersExplicit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/allow.headers.wildcard/client",
        "${net}/allow.headers.wildcard/server",
    })
    public void shouldAllowHeadersWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/allow.origin.explicit/client",
        "${net}/allow.origin.explicit/server",
    })
    public void shouldAllowOriginExplicit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/allow.origin.wildcard/client",
        "${net}/allow.origin.wildcard/server",
    })
    public void shouldAllowOriginWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/cache.allow.methods.explicit/client",
        "${net}/cache.allow.methods.explicit/server",
    })
    public void shouldCacheAllowMethodsExplicit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/cache.allow.methods.wildcard/client",
        "${net}/cache.allow.methods.wildcard/server",
    })
    public void shouldCacheAllowMethodsWildcard() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${net}/cache.allow.headers.explicit/client",
        "${net}/cache.allow.headers.explicit/server",
    })
    public void shouldCacheAllowHeadersExplicit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/cache.allow.headers.wildcard/client",
        "${net}/cache.allow.headers.wildcard/server",
    })
    public void shouldCacheAllowHeadersWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/expose.headers.explicit/client",
        "${net}/expose.headers.explicit/server",
    })
    public void shouldExposeHeadersExplicit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/expose.headers.wildcard/client",
        "${net}/expose.headers.wildcard/server",
    })
    public void shouldExposeHeadersWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.methods.unexpected/client",
        "${net}/reject.methods.unexpected/server",
    })
    public void shouldRejectMethodsUnexpected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.headers.unexpected/client",
        "${net}/reject.headers.unexpected/server",
    })
    public void shouldRejectHeadersUnexpected() throws Exception
    {
        k3po.finish();
    }
}
