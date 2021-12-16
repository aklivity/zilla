/*
 * Copyright 2021-2021 Aklivity Inc.
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

import static java.lang.Long.compareUnsigned;
import static java.math.BigInteger.ONE;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.LangUtil;

public class Cidr
{
    private static final Pattern CIDR_PATTERN = Pattern.compile("([0-9a-fA-F:.]+)/(\\d{1,3})");

    private long low0;
    private long low1;
    private long high0;
    private long high1;

    public Cidr(
        String notation)
    {
        final Matcher matcher = CIDR_PATTERN.matcher(notation);

        if (matcher.matches())
        {
            try
            {
                byte[] address = InetAddress.getByName(matcher.group(1)).getAddress();
                BigInteger network = new BigInteger(address).mod(ONE.shiftLeft(address.length << 3));
                int bits = Integer.parseInt(matcher.group(2));
                int shift = (address.length << 3) - bits;
                BigInteger low = network.andNot(ONE.shiftLeft(shift).subtract(ONE));
                BigInteger high = network.or(ONE.shiftLeft(shift).subtract(ONE));

                low0 = low.shiftRight(Long.SIZE).longValue();
                low1 = low.longValue();
                high0 = high.shiftRight(Long.SIZE).longValue();
                high1 = high.longValue();
            }
            catch (Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }
        else
        {
            throw new IllegalArgumentException("Could not parse [" + notation + "]");
        }
    }

    public boolean matches(
        InetAddress inet)
    {
        byte[] address = inet.getAddress();

        BigInteger candidate = new BigInteger(address).mod(ONE.shiftLeft(address.length << 3));
        long candidate0 = candidate.shiftRight(Long.SIZE).longValue();
        long candidate1 = candidate.longValue();

        return compareUnsigned(low0, candidate0) <= 0 && compareUnsigned(low1, candidate1) <= 0 &&
               compareUnsigned(candidate0, high0) <= 0 && compareUnsigned(candidate1, high1) <= 0;
    }

    boolean matches(
        String address) throws UnknownHostException
    {
        return matches(InetAddress.getByName(address));
    }
}
