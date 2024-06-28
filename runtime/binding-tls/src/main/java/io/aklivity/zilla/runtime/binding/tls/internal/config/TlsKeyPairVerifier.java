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
package io.aklivity.zilla.runtime.binding.tls.internal.config;

import static org.agrona.LangUtil.rethrowUnchecked;

import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.concurrent.ThreadLocalRandom;

public class TlsKeyPairVerifier
{
    private static final String ALGORITHM = "SHA256withRSA";

    public boolean verify(
        KeyStore.PrivateKeyEntry entry)
    {
        boolean valid = false;
        try
        {
            PrivateKey privateKey = entry.getPrivateKey();
            PublicKey publicKey = entry.getCertificate().getPublicKey();

            // create a challenge
            byte[] challenge = new byte[10000];
            ThreadLocalRandom.current().nextBytes(challenge);

            // sign using the private key
            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initSign(privateKey);
            sig.update(challenge);
            byte[] signature = sig.sign();

            // verify signature using the public key
            sig.initVerify(publicKey);
            sig.update(challenge);
            valid = sig.verify(signature);
        }
        catch (InvalidKeyException | SignatureException ex)
        {
            // key invalid
        }
        catch (NoSuchAlgorithmException ex)
        {
            rethrowUnchecked(ex);
        }
        return valid;
    }
}
