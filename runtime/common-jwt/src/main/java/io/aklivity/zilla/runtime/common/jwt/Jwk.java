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

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.NamedParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Map;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

public final class Jwk
{
    private static final Map<String, ECParameterSpec> CURVES;

    static
    {
        try
        {
            CURVES = Map.of(
                "P-256", curve("secp256r1"),
                "P-384", curve("secp384r1"),
                "P-521", curve("secp521r1"));
        }
        catch (GeneralSecurityException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    private final String keyType;
    private final String keyId;
    private final String algorithm;
    private final String use;
    private final PublicKey publicKey;

    public static Jwk parse(
        JsonObject json) throws JwtException
    {
        String keyType = member(json, "kty");
        if (keyType == null)
        {
            throw new JwtException("Missing key type");
        }

        PublicKey publicKey = switch (keyType)
        {
        case "RSA" -> rsa(json);
        case "EC" -> ec(json);
        case "OKP" -> okp(json);
        default -> throw new JwtException("Unsupported key type: " + keyType);
        };

        return new Jwk(keyType, member(json, "kid"), member(json, "alg"), member(json, "use"), publicKey);
    }

    public static Jwk of(
        PublicKey publicKey) throws JwtException
    {
        Jwk jwk;

        if (publicKey instanceof RSAPublicKey rsa)
        {
            if (rsa.getModulus().bitLength() < JwsAlgorithm.MIN_RSA_MODULUS_BITS)
            {
                throw new JwtException("RSA key is too weak");
            }

            jwk = new Jwk("RSA", null, null, null, rsa(rsa.getModulus(), rsa.getPublicExponent()));
        }
        else if (publicKey instanceof ECPublicKey ec)
        {
            String name = curveName(ec.getParams());
            if (name == null)
            {
                throw new JwtException("Unsupported curve");
            }

            jwk = new Jwk("EC", null, null, null, ec(name, ec.getW().getAffineX(), ec.getW().getAffineY()));
        }
        else if (publicKey instanceof EdECPublicKey ed)
        {
            String name = ed.getParams().getName();
            if (!"Ed25519".equals(name) && !"Ed448".equals(name))
            {
                throw new JwtException("Unsupported curve: " + name);
            }

            jwk = new Jwk("OKP", null, null, null, okp(name, ed.getPoint()));
        }
        else
        {
            throw new JwtException("Unsupported key type: " + (publicKey != null ? publicKey.getAlgorithm() : null));
        }

        return jwk;
    }

    public String keyType()
    {
        return keyType;
    }

    public String keyId()
    {
        return keyId;
    }

    public String algorithm()
    {
        return algorithm;
    }

    public String use()
    {
        return use;
    }

    public PublicKey publicKey()
    {
        return publicKey;
    }

    private Jwk(
        String keyType,
        String keyId,
        String algorithm,
        String use,
        PublicKey publicKey)
    {
        this.keyType = keyType;
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.use = use;
        this.publicKey = publicKey;
    }

    private static PublicKey rsa(
        JsonObject json) throws JwtException
    {
        return rsa(integer(json, "n"), integer(json, "e"));
    }

    private static PublicKey rsa(
        BigInteger modulus,
        BigInteger exponent) throws JwtException
    {
        PublicKey key;
        try
        {
            key = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        }
        catch (GeneralSecurityException ex)
        {
            throw new JwtException("Invalid RSA key", ex);
        }

        return key;
    }

    private static PublicKey ec(
        JsonObject json) throws JwtException
    {
        String name = required(json, "crv");
        ECParameterSpec curve = CURVES.get(name);
        if (curve == null)
        {
            throw new JwtException("Unsupported curve: " + name);
        }

        return ec(name, integer(json, "x"), integer(json, "y"));
    }

    private static PublicKey ec(
        String name,
        BigInteger x,
        BigInteger y) throws JwtException
    {
        ECParameterSpec curve = CURVES.get(name);
        if (!onCurve(curve.getCurve(), x, y))
        {
            throw new JwtException("Point is not on curve " + name);
        }

        PublicKey key;
        try
        {
            key = KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), curve));
        }
        catch (GeneralSecurityException ex)
        {
            throw new JwtException("Invalid EC key", ex);
        }

        return key;
    }

    private static PublicKey okp(
        JsonObject json) throws JwtException
    {
        String name = required(json, "crv");
        int length = switch (name)
        {
        case "Ed25519" -> 32;
        case "Ed448" -> 57;
        default -> throw new JwtException("Unsupported curve: " + name);
        };

        byte[] encoded = Base64Url.decode(required(json, "x"));
        if (encoded.length != length)
        {
            throw new JwtException("Invalid length for curve " + name);
        }

        boolean xOdd = (encoded[length - 1] & 0x80) != 0;
        encoded[length - 1] &= 0x7f;

        byte[] bigEndian = new byte[length];
        for (int i = 0; i < length; i++)
        {
            bigEndian[i] = encoded[length - 1 - i];
        }

        return okp(name, new EdECPoint(xOdd, new BigInteger(1, bigEndian)));
    }

    private static PublicKey okp(
        String name,
        EdECPoint point) throws JwtException
    {
        EdECPublicKeySpec spec = new EdECPublicKeySpec(new NamedParameterSpec(name), point);

        PublicKey key;
        try
        {
            key = KeyFactory.getInstance("EdDSA").generatePublic(spec);
        }
        catch (GeneralSecurityException ex)
        {
            throw new JwtException("Invalid OKP key", ex);
        }

        return key;
    }

    private static String curveName(
        ECParameterSpec params)
    {
        String name = null;

        for (Map.Entry<String, ECParameterSpec> entry : CURVES.entrySet())
        {
            ECParameterSpec curve = entry.getValue();
            if (curve.getCurve().equals(params.getCurve()) &&
                curve.getGenerator().equals(params.getGenerator()) &&
                curve.getOrder().equals(params.getOrder()) &&
                curve.getCofactor() == params.getCofactor())
            {
                name = entry.getKey();
            }
        }

        return name;
    }

    private static boolean onCurve(
        EllipticCurve curve,
        BigInteger x,
        BigInteger y)
    {
        BigInteger p = ((ECFieldFp) curve.getField()).getP();

        BigInteger left = y.pow(2).mod(p);
        BigInteger right = x.pow(3).add(curve.getA().multiply(x)).add(curve.getB()).mod(p);

        return x.compareTo(p) < 0 && y.compareTo(p) < 0 && left.equals(right);
    }

    private static BigInteger integer(
        JsonObject json,
        String name) throws JwtException
    {
        return new BigInteger(1, Base64Url.decode(required(json, name)));
    }

    private static String required(
        JsonObject json,
        String name) throws JwtException
    {
        String value = member(json, name);
        if (value == null)
        {
            throw new JwtException("Missing key member: " + name);
        }

        return value;
    }

    private static String member(
        JsonObject json,
        String name) throws JwtException
    {
        JsonValue value = json.get(name);

        String member = null;
        if (value instanceof JsonString string)
        {
            member = string.getString();
        }
        else if (value != null && value.getValueType() != JsonValue.ValueType.NULL)
        {
            throw new JwtException("Invalid key member: " + name);
        }

        return member;
    }

    private static ECParameterSpec curve(
        String name) throws GeneralSecurityException
    {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(name));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }
}
