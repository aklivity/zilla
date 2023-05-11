/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.manager.internal.commands.encrypt;

import static io.aklivity.zilla.manager.internal.settings.ZpmSecrets.decryptSecret;
import static io.aklivity.zilla.manager.internal.settings.ZpmSecrets.encryptSecret;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;
import static org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Random;
import java.util.Scanner;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.sonatype.plexus.components.cipher.PlexusCipherException;

import com.github.rvesse.airline.annotations.Command;

import io.aklivity.zilla.manager.internal.ZpmCommand;
import io.aklivity.zilla.manager.internal.settings.ZpmSecurity;

@Command(
    name = "encrypt",
    description = "Encrypt passwords")
public class ZpmEncrypt extends ZpmCommand
{
    private static final String SECRET_CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/";

    @Override
    public void invoke()
    {
        try
        {
            ZpmSecurity security = readSecurity(settingsDir);

            if (security.secret == null)
            {
                final String secret = generateSecret(32);
                security.secret = encryptSecret(secret, SYSTEM_PROPERTY_SEC_LOCATION);
                writeSecurity(security);
            }

            assert security.secret != null;

            final String secret = decryptSecret(security.secret, SYSTEM_PROPERTY_SEC_LOCATION);
            try (Scanner scanner = new Scanner(System.in))
            {
                String password = scanner.nextLine().trim();
                String encrypted = encryptSecret(password, secret);

                System.out.println(encrypted);
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private ZpmSecurity readSecurity(
        Path settingsDir) throws IOException, PlexusCipherException
    {
        Path securityFile = settingsDir.resolve("security.json");

        ZpmSecurity security = new ZpmSecurity();

        Jsonb builder = JsonbBuilder.newBuilder()
                .withConfig(new JsonbConfig().withFormatting(true))
                .build();

        if (Files.exists(securityFile))
        {
            try (InputStream in = newInputStream(securityFile))
            {
                security = builder.fromJson(in, ZpmSecurity.class);
            }
        }

        return security;
    }

    private void writeSecurity(
        ZpmSecurity security) throws IOException
    {
        Path securityFile = settingsDir.resolve("security.json");

        Jsonb builder = JsonbBuilder.newBuilder()
                .withConfig(new JsonbConfig().withFormatting(true))
                .build();

        createDirectories(settingsDir);
        try (OutputStream out = newOutputStream(securityFile))
        {
            builder.toJson(security, out);
        }
    }

    private String generateSecret(
        int length) throws PlexusCipherException
    {
        Random random = new SecureRandom();
        char[] secret = new char[length];

        for (int i = 0; i < secret.length; i++)
        {
            int charIndex = random.nextInt(SECRET_CHARS.length());
            secret[i] = SECRET_CHARS.charAt(charIndex);
        }

        return new String(secret);
    }
}
