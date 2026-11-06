/*
 * Copyright 2021-2026 Aklivity Inc
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

package io.aklivity.zilla.runtime.binding.a2a.internal.push;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.net.InetAddress;
import java.util.List;

import org.junit.Test;

public class A2aPushPolicyTest
{
    private static final A2aPushPolicy ANY = new A2aPushPolicy(null);
    private static final A2aPushPolicy LISTED = new A2aPushPolicy(List.of("https://*.example.com/hooks/*"));

    @Test
    public void shouldPermitPublicUrl()
    {
        assertThat(ANY.permits("https://client.example.com/hooks/1"), equalTo(true));
        assertThat(ANY.permits("http://client.example.com:8080/hooks/1?x=1"), equalTo(true));
    }

    @Test
    public void shouldNotPermitUnsupportedOrMalformedUrl()
    {
        assertThat(ANY.permits("ftp://client.example.com/hooks/1"), equalTo(false));
        assertThat(ANY.permits("file:///etc/passwd"), equalTo(false));
        assertThat(ANY.permits("not a url"), equalTo(false));
        assertThat(ANY.permits("https://user:pass@client.example.com/hooks/1"), equalTo(false));
        assertThat(ANY.permits("http://2130706433/"), equalTo(false));
        assertThat(ANY.permits("http://0x7f.0.0.1/"), equalTo(false));
    }

    @Test
    public void shouldNotPermitLoopbackLinkLocalAndPrivateLiterals()
    {
        assertThat(ANY.permits("http://127.0.0.1/hooks"), equalTo(false));
        assertThat(ANY.permits("http://localhost/hooks"), equalTo(false));
        assertThat(ANY.permits("http://hooks.localhost/hooks"), equalTo(false));
        assertThat(ANY.permits("http://169.254.169.254/latest/meta-data"), equalTo(false));
        assertThat(ANY.permits("http://10.0.0.5/hooks"), equalTo(false));
        assertThat(ANY.permits("http://172.16.0.1/hooks"), equalTo(false));
        assertThat(ANY.permits("http://192.168.1.1/hooks"), equalTo(false));
        assertThat(ANY.permits("http://100.64.0.1/hooks"), equalTo(false));
        assertThat(ANY.permits("http://0.0.0.0/hooks"), equalTo(false));
        assertThat(ANY.permits("http://[::1]/hooks"), equalTo(false));
        assertThat(ANY.permits("http://[fe80::1]/hooks"), equalTo(false));
        assertThat(ANY.permits("http://[fd00::1]/hooks"), equalTo(false));
        assertThat(ANY.permits("http://[::ffff:10.0.0.1]/hooks"), equalTo(false));
        assertThat(ANY.permits("http://93.184.216.34/hooks"), equalTo(true));
        assertThat(ANY.permits("http://[2606:2800:220:1::1]/hooks"), equalTo(true));
    }

    @Test
    public void shouldPermitOnlyListedUrls()
    {
        assertThat(LISTED.permits("https://a.example.com/hooks/1"), equalTo(true));
        assertThat(LISTED.permits("https://a.b.example.com/hooks/x/y"), equalTo(true));
        assertThat(LISTED.permits("http://a.example.com/hooks/1"), equalTo(false));
        assertThat(LISTED.permits("https://a.example.org/hooks/1"), equalTo(false));
        assertThat(LISTED.permits("https://a.example.com/other/1"), equalTo(false));
        assertThat(LISTED.permits("https://evil.com/a.example.com/hooks/1"), equalTo(false));
    }

    @Test
    public void shouldPermitPrivateTargetOnlyWhenExplicitlyListed()
    {
        final A2aPushPolicy explicit = new A2aPushPolicy(List.of("http://10.0.0.5:8080/hooks/*", "http://localhost/hooks/*"));
        final A2aPushPolicy wildcard = new A2aPushPolicy(List.of("http://*/hooks/*"));

        assertThat(explicit.permits("http://10.0.0.5:8080/hooks/1"), equalTo(true));
        assertThat(explicit.permits("http://10.0.0.5:9090/hooks/1"), equalTo(false));
        assertThat(explicit.permits("http://10.0.0.6:8080/hooks/1"), equalTo(false));
        assertThat(explicit.permits("http://localhost/hooks/1"), equalTo(true));
        assertThat(wildcard.permits("http://10.0.0.5/hooks/1"), equalTo(false));
        assertThat(wildcard.permits("http://client.example.com/hooks/1"), equalTo(true));
    }

    @Test
    public void shouldCheckResolvedAddressesAtDelivery() throws Exception
    {
        final InetAddress pub = InetAddress.getByName("93.184.216.34");
        final InetAddress loopback = InetAddress.getByName("127.0.0.1");
        final A2aPushPolicy explicit = new A2aPushPolicy(List.of("http://hooks.internal/hooks/*"));

        assertThat(ANY.permits("https://client.example.com/hooks/1", new InetAddress[] {pub}), equalTo(true));
        assertThat(ANY.permits("https://client.example.com/hooks/1", new InetAddress[] {pub, loopback}), equalTo(false));
        assertThat(ANY.permits("https://client.example.com/hooks/1", new InetAddress[] {}), equalTo(false));
        assertThat(LISTED.permits("https://a.example.com/hooks/1", new InetAddress[] {loopback}), equalTo(false));
        assertThat(explicit.permits("http://hooks.internal/hooks/1", new InetAddress[] {loopback}), equalTo(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidAllowPattern()
    {
        new A2aPushPolicy(List.of("hooks.example.com"));
    }
}
