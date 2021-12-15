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
package io.aklivity.zilla.runtime.cog.http.internal.util;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class HttpUtilTest
{
    @Test
    public void shouldAppendHeader()
    {
        StringBuilder message = new StringBuilder("...");
        HttpUtil.appendHeader(message, "Header", "header-value");
        assertEquals("...Header: header-value\r\n", message.toString());
    }

    @Test
    public void shouldInitCapInitialHeaderNameCharacter()
    {
        StringBuilder message = new StringBuilder();
        HttpUtil.appendHeader(message, "host", "value");
        assertEquals("Host: value\r\n", message.toString());
    }

    @Test
    public void shouldInitCapCharacterFollowingHyphen()
    {
        StringBuilder message = new StringBuilder();
        HttpUtil.appendHeader(message, "content-length", "14");
        assertEquals("Content-Length: 14\r\n", message.toString());
    }

    @Test
    public void shouldInitCapCharacterFollowingHyphenWithHyphenAtStart()
    {
        StringBuilder message = new StringBuilder();
        HttpUtil.appendHeader(message, "-name", "value");
        assertEquals("-Name: value\r\n", message.toString());
    }

    @Test
    public void shouldHandleHaderNameWithHyphenAtEnd()
    {
        StringBuilder message = new StringBuilder();
        HttpUtil.appendHeader(message, "name-", "value");
        assertEquals("Name-: value\r\n", message.toString());
    }

    @Test
    public void shouldHandleSingleHyhenHeaderName()
    {
        StringBuilder message = new StringBuilder();
        HttpUtil.appendHeader(message, "-", "value");
        assertEquals("-: value\r\n", message.toString());
    }

    @Test
    public void shouldHandleAllHyphensHeaderName()
    {
        StringBuilder message = new StringBuilder();
        HttpUtil.appendHeader(message, "---", "value");
        assertEquals("---: value\r\n", message.toString());
    }

    @Test
    public void shouldAcceptPathWithAllValidCharacters()
    {
        byte[] ascii = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/?#[]@!$&'()*+,;=%25"
            .getBytes(US_ASCII);
        assertTrue(HttpUtil.isPathValid(new UnsafeBuffer(ascii)));
    }

    @Test
    public void shouldAcceptValidPathWithDifferentLength()
    {
        byte[] ascii0 = "".getBytes(US_ASCII);
        byte[] ascii1 = "/path".getBytes(US_ASCII);
        byte[] ascii2 = "/pathof8".getBytes(US_ASCII);
        byte[] ascii3 = "/pathof010".getBytes(US_ASCII);
        byte[] ascii4 = "/pathof000000016".getBytes(US_ASCII);
        byte[] ascii5 = "/pathof0000000017".getBytes(US_ASCII);

        assertTrue(HttpUtil.isPathValid(new UnsafeBuffer(ascii0)));
        assertTrue(HttpUtil.isPathValid(new UnsafeBuffer(ascii1)));
        assertTrue(HttpUtil.isPathValid(new UnsafeBuffer(ascii2)));
        assertTrue(HttpUtil.isPathValid(new UnsafeBuffer(ascii3)));
        assertTrue(HttpUtil.isPathValid(new UnsafeBuffer(ascii4)));
        assertTrue(HttpUtil.isPathValid(new UnsafeBuffer(ascii5)));
    }

    @Test
    public void shouldRejectInvalidAsciiSpaceCharacterInPath()
    {
        byte[] ascii0 = "/pathwith ".getBytes(US_ASCII);
        byte[] ascii1 = " /pathwith".getBytes(US_ASCII);
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii0)));
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii1)));
    }

    @Test
    public void shouldRejectInvalidAsciiDoubleQuotesCharacterInPath()
    {
        byte[] ascii0 = "/pathwith\"".getBytes(US_ASCII);
        byte[] ascii1 = "\"/pathwith".getBytes(US_ASCII);
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii0)));
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii1)));
    }

    @Test
    public void shouldRejectInvalidAsciiLessThanCharacterInPath()
    {
        byte[] ascii0 = "/pathwith<".getBytes(US_ASCII);
        byte[] ascii1 = "</pathwith".getBytes(US_ASCII);
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii0)));
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii1)));
    }

    @Test
    public void shouldRejectInvalidAsciiGreatThanCharacterInPath()
    {
        byte[] ascii0 = "/pathwith>".getBytes(US_ASCII);
        byte[] ascii1 = ">/pathwith".getBytes(US_ASCII);
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii0)));
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii1)));
    }

    @Test
    public void shouldRejectInvalidAsciiBackslashCharacterInPath()
    {
        byte[] ascii0 = "/pathwith\\".getBytes(US_ASCII);
        byte[] ascii1 = "\\/pathwith".getBytes(US_ASCII);
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii0)));
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii1)));
    }

    @Test
    public void shouldRejectInvalidAsciiCaretCharacterInPath()
    {
        byte[] ascii0 = "/pathwith^".getBytes(US_ASCII);
        byte[] ascii1 = "^/pathwith".getBytes(US_ASCII);
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii0)));
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii1)));
    }

    @Test
    public void shouldRejectInvalidAsciiGraveCharacterInPath()
    {
        byte[] ascii0 = "/pathwith`".getBytes(US_ASCII);
        byte[] ascii1 = "`/pathwith".getBytes(US_ASCII);
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii0)));
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii1)));
    }

    @Test
    public void shouldRejectInvalidAsciiOpenBraceCharacterInPath()
    {
        byte[] ascii0 = "/pathwith{".getBytes(US_ASCII);
        byte[] ascii1 = "{/pathwith".getBytes(US_ASCII);
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii0)));
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii1)));
    }

    @Test
    public void shouldRejectInvalidAsciiCloseBraceCharacterInPath()
    {
        byte[] ascii0 = "/pathwith}".getBytes(US_ASCII);
        byte[] ascii1 = "}/pathwith".getBytes(US_ASCII);
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii0)));
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii1)));
    }

    @Test
    public void shouldRejectInvalidAsciiVerticalBarCharacterInPath()
    {
        byte[] ascii0 = "/pathwith|".getBytes(US_ASCII);
        byte[] ascii1 = "|/pathwith".getBytes(US_ASCII);
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii0)));
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii1)));
    }

    @Test
    public void shouldRejectInvalidAsciiDeleteCharacterInPath()
    {
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(new byte[0x7F])));
    }

    @Test
    public void shouldRejectInvalidAsciiPercentCharacterNotFollowedByDigitsInPath()
    {
        byte[] ascii0 = "/path%2ith".getBytes(US_ASCII);
        byte[] ascii1 = "/path%a5ith".getBytes(US_ASCII);
        byte[] ascii2 = "/path%".getBytes(US_ASCII);
        byte[] ascii3 = "/pat%2j".getBytes(US_ASCII);
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii0)));
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii1)));
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii2)));
        assertFalse(HttpUtil.isPathValid(new UnsafeBuffer(ascii3)));
    }

    @Test
    public void shouldAcceptValidAsciiPercentCharacterInPath()
    {
        byte[] ascii0 = "where=(UPPER(hazard_name)%20LIKE".getBytes(US_ASCII);
        assertTrue(HttpUtil.isPathValid(new UnsafeBuffer(ascii0)));
    }

}

