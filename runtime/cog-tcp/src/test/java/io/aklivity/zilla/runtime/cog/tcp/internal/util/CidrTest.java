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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CidrTest
{

    @Test
    public void shouldHonorHostRoute() throws Exception
    {
        Cidr cidr = new Cidr("127.127.127.127/32");
        assertTrue(cidr.matches("127.127.127.127"));
        assertFalse(cidr.matches("127.127.127.128"));
        assertFalse(cidr.matches("127.127.127.126"));
        assertFalse(cidr.matches("255.255.255.127"));
        assertFalse(cidr.matches("0.0.0.127"));
    }

    @Test
    public void shouldHonorArbitrary() throws Exception
    {
        Cidr cidr = new Cidr("127.127.255.0/20");
        assertTrue(cidr.matches("127.127.240.0"));
        assertFalse(cidr.matches("127.127.239.255"));
        assertTrue(cidr.matches("127.127.255.255"));
        assertFalse(cidr.matches("127.128.0.0"));
    }

    @Test
    public void shouldHonorClassC() throws Exception
    {
        Cidr cidr = new Cidr("127.127.127.0/24");
        assertTrue(cidr.matches("127.127.127.255"));
        assertTrue(cidr.matches("127.127.127.0"));
        assertFalse(cidr.matches("127.127.126.255"));
        assertFalse(cidr.matches("127.127.128.0"));
    }

    @Test
    public void shouldHonorClassB() throws Exception
    {
        Cidr cidr = new Cidr("127.127.127.127/16");
        assertTrue(cidr.matches("127.127.255.255"));
        assertTrue(cidr.matches("127.127.0.0"));
        assertFalse(cidr.matches("127.126.255.255"));
        assertFalse(cidr.matches("127.128.0.0"));
    }

    @Test
    public void shouldHonorClassA() throws Exception
    {
        Cidr cidr = new Cidr("127.127.127.127/8");
        assertTrue(cidr.matches("127.255.255.255"));
        assertTrue(cidr.matches("127.0.0.0"));
        assertFalse(cidr.matches("126.255.255.255"));
        assertFalse(cidr.matches("128.0.0.0"));
    }

    @Test
    public void shouldHonorDefaultRoute() throws Exception
    {
        Cidr cidr = new Cidr("0.0.0.0/0");
        assertTrue(cidr.matches("127.127.127.127"));
        assertTrue(cidr.matches("127.127.127.128"));
        assertTrue(cidr.matches("127.127.127.126"));
        assertTrue(cidr.matches("255.255.255.255"));
        assertTrue(cidr.matches("0.0.0.0"));
        assertTrue(cidr.matches("0.0.0.127"));
    }

    @Test
    public void shouldHonorHostRoute6() throws Exception
    {
        Cidr cidr = new Cidr("7f7f:7f7f:7f7f:7f7f:7f7f:7f7f:7f7f:7f7f/128");
        assertTrue(cidr.matches("7f7f:7f7f:7f7f:7f7f:7f7f:7f7f:7f7f:7f7f"));
        assertFalse(cidr.matches("7f7f:7f7f:7f7f:7f7f:7f7f:7f7f:7f7f:7f80"));
        assertFalse(cidr.matches("7f7f:7f7f:7f7f:7f7f:7f7f:7f7f:7f7f:7f7e"));
        assertFalse(cidr.matches("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ff7f"));
        assertFalse(cidr.matches("::7f"));
    }

    @Test
    public void shouldHonorArbitrary6() throws Exception
    {
        Cidr cidr = new Cidr("7f7f:7f7f:7f7f:7f7f:ffff:ffff::0/72");
        assertTrue(cidr.matches("7f7f:7f7f:7f7f:7f7f:ff00::0"));
        assertFalse(cidr.matches("7f7f:7f7f:7f7f:7f7f:feff:ffff:ffff:ffff"));
        assertTrue(cidr.matches("7f7f:7f7f:7f7f:7f7f:ffff:ffff:ffff:ffff"));
        assertFalse(cidr.matches("7f7f:7f7f:7f7f:7f80::0"));
    }

}
