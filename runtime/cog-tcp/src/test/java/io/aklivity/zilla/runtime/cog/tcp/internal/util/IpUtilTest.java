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
package io.aklivity.zilla.runtime.cog.tcp.internal.util;

import static java.net.InetAddress.getLocalHost;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.junit.Test;

public final class IpUtilTest
{

    @Test
    public void shouldMatchAddressesSameAddressAndPort() throws Exception
    {
        InetSocketAddress address1 = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        InetSocketAddress address2 = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        assertEquals(0, IpUtil.compareAddresses(address1, address2));
    }

    @Test
    public void shouldMatchAddressesFirstIsAny() throws Exception
    {
        InetSocketAddress address1 = new InetSocketAddress(8080);
        InetSocketAddress address2 = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        assertEquals(0, IpUtil.compareAddresses(address1, address2));
    }

    @Test
    public void shouldMatchAddressesSecondIsAny() throws Exception
    {
        InetSocketAddress address1 = new InetSocketAddress(getLocalHost(), 8080);
        InetSocketAddress address2 = new InetSocketAddress(8080);
        assertEquals(0, IpUtil.compareAddresses(address1, address2));
    }

    @Test
    public void shouldNotMatchAddressesDifferentPort() throws Exception
    {
        InetSocketAddress address1 = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        InetSocketAddress address2 = new InetSocketAddress(InetAddress.getLocalHost(), 8081);
        assertFalse(0 == IpUtil.compareAddresses(address1, address2));
    }

    @Test
    public void shouldNotMatchAddressesFirstIsAnyDifferentPort() throws Exception
    {
        InetSocketAddress address1 = new InetSocketAddress(8080);
        InetSocketAddress address2 = new InetSocketAddress(InetAddress.getLocalHost(), 8081);
        assertFalse(0 == IpUtil.compareAddresses(address1, address2));
    }

    @Test
    public void shouldNotMatchAddressesSecondIsAnyDifferentPort() throws Exception
    {
        InetSocketAddress address1 = new InetSocketAddress(InetAddress.getLocalHost(), 8081);
        InetSocketAddress address2 = new InetSocketAddress(8080);
        assertFalse(0 == IpUtil.compareAddresses(address1, address2));
    }

}
