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
package io.aklivity.zilla.runtime.common.jwt;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECKey;
import java.security.interfaces.EdECKey;
import java.security.interfaces.RSAKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Map;
import java.util.stream.Stream;

public enum JwsAlgorithm
{
    RS256("RS256", Family.RSA, "SHA256withRSA", null, 0),
    RS384("RS384", Family.RSA, "SHA384withRSA", null, 0),
    RS512("RS512", Family.RSA, "SHA512withRSA", null, 0),
    PS256("PS256", Family.PSS, "RSASSA-PSS", "SHA-256", 32),
    PS384("PS384", Family.PSS, "RSASSA-PSS", "SHA-384", 48),
    PS512("PS512", Family.PSS, "RSASSA-PSS", "SHA-512", 64),
    ES256("ES256", Family.ECDSA, "SHA256withECDSAinP1363Format", null, 256),
    ES384("ES384", Family.ECDSA, "SHA384withECDSAinP1363Format", null, 384),
    ES512("ES512", Family.ECDSA, "SHA512withECDSAinP1363Format", null, 521),
    EDDSA("EdDSA", Family.EDDSA, "EdDSA", null, 0);

    static final int MIN_RSA_MODULUS_BITS = 2048;
    private static final Map<String, JwsAlgorithm> BY_JOSE_NAME =
        Stream.of(values()).collect(toMap(JwsAlgorithm::joseName, identity()));

    private final String joseName;
    private final Family family;
    private final String signatureName;
    private final AlgorithmParameterSpec parameters;
    private final int size;

    JwsAlgorithm(
        String joseName,
        Family family,
        String signatureName,
        String digest,
        int size)
    {
        this.joseName = joseName;
        this.family = family;
        this.signatureName = signatureName;
        this.parameters = family == Family.PSS
            ? new PSSParameterSpec(digest, "MGF1", new MGF1ParameterSpec(digest), size, 1)
            : null;
        this.size = size;
    }

    public String joseName()
    {
        return joseName;
    }

    public static JwsAlgorithm of(
        String joseName)
    {
        return joseName != null ? BY_JOSE_NAME.get(joseName) : null;
    }

    byte[] sign(
        PrivateKey key,
        byte[] input) throws JwtException
    {
        if (!compatible(key))
        {
            throw new JwtException("Key is not valid for algorithm " + joseName);
        }

        byte[] signed;
        try
        {
            Signature signature = newSignature();
            signature.initSign(key);
            signature.update(input);
            signed = signature.sign();
        }
        catch (GeneralSecurityException ex)
        {
            throw new JwtException("Unable to sign using algorithm " + joseName, ex);
        }

        return signed;
    }

    boolean verify(
        PublicKey key,
        byte[] input,
        byte[] signed) throws JwtException
    {
        if (!compatible(key))
        {
            throw new JwtException("Key is not valid for algorithm " + joseName);
        }

        boolean verified = false;

        if (family != Family.ECDSA || signed.length == 2 * ((size + 7) / 8))
        {
            try
            {
                Signature signature = newSignature();
                signature.initVerify(key);
                signature.update(input);
                verified = signature.verify(signed);
            }
            catch (SignatureException ex)
            {
                verified = false;
            }
            catch (GeneralSecurityException ex)
            {
                throw new JwtException("Unable to verify using algorithm " + joseName, ex);
            }
        }

        return verified;
    }

    private Signature newSignature() throws GeneralSecurityException
    {
        Signature signature = Signature.getInstance(signatureName);

        if (parameters != null)
        {
            signature.setParameter(parameters);
        }

        return signature;
    }

    private boolean compatible(
        Key key)
    {
        return switch (family)
        {
        case RSA, PSS -> key instanceof RSAKey rsa && rsa.getModulus().bitLength() >= MIN_RSA_MODULUS_BITS;
        case ECDSA -> key instanceof ECKey ec && ec.getParams().getCurve().getField().getFieldSize() == size;
        case EDDSA -> key instanceof EdECKey;
        };
    }

    private enum Family
    {
        RSA,
        PSS,
        ECDSA,
        EDDSA
    }
}
