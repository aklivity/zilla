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
package io.aklivity.zilla.runtime.binding.filesystem.internal;

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.agrona.BitUtil;

public final class FileSystemDigest
{
    private final byte[] buffer;
    private final MessageDigest md5;

    public FileSystemDigest(
        byte[] buffer)
    {
        this.buffer = buffer;
        this.md5 = initMessageDigest("MD5");
    }

    public String digest(
        InputStream input) throws IOException
    {
        md5.reset();
        for (int bytesRead = input.read(buffer); bytesRead != -1; bytesRead = input.read(buffer))
        {
            md5.update(buffer, 0, bytesRead);
        }
        return BitUtil.toHex(md5.digest());
    }

    private static MessageDigest initMessageDigest(
        String algorithm)
    {
        MessageDigest messageDigest = null;
        try
        {
            messageDigest = MessageDigest.getInstance(algorithm);
        }
        catch (NoSuchAlgorithmException ex)
        {
            rethrowUnchecked(ex);
        }
        return messageDigest;
    }
}
