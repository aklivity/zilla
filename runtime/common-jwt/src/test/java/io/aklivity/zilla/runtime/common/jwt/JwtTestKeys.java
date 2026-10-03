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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

final class JwtTestKeys
{
    static final String RFC7515_RS256_N =
        "ofgWCuLjybRlzo0tZWJjNiuSfb4p4fAkd_wWJcyQoTbji9k0l8W26mPddx" +
        "HmfHQp-Vaw-4qPCJrcS2mJPMEzP1Pt0Bm4d4QlL-yRT-SFd2lZS-pCgNMs" +
        "D1W_YpRPEwOWvG6b32690r2jZ47soMZo9wGzjb_7OMg0LOL-bSf63kpaSH" +
        "SXndS5z5rexMdbBYUsLA9e-KXBdQOS-UTo7WTBEMa2R2CapHg665xsmtdV" +
        "MTBQY4uDZlxvb3qCo5ZwKh9kG4LT6_I5IhlJH7aGhyxXFvUK-DWNmoudF8" +
        "NAco9_h9iaGNj8q2ethFkMLs91kzk2PAcDTW9gb54h4FRWyuXpoQ";
    static final String RFC7515_RS256_E = "AQAB";
    static final String RFC7515_RS256_D =
        "Eq5xpGnNCivDflJsRQBXHx1hdR1k6Ulwe2JZD50LpXyWPEAeP88vLNO97I" +
        "jlA7_GQ5sLKMgvfTeXZx9SE-7YwVol2NXOoAJe46sui395IW_GO-pWJ1O0" +
        "BkTGoVEn2bKVRUCgu-GjBVaYLU6f3l9kJfFNS3E0QbVdxzubSu3Mkqzjkn" +
        "439X0M_V51gfpRLI9JYanrC4D4qAdGcopV_0ZHHzQlBjudU2QvXt4ehNYT" +
        "CBr6XCLQUShb1juUO1ZdiYoFaFQT5Tw8bGUl_x_jTj3ccPDVZFD9pIuhLh" +
        "BOneufuBiB4cS98l2SR_RQyGWSeWjnczT0QU91p1DhOVRuOopznQ";

    static final String RFC7515_ES256_X = "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU";
    static final String RFC7515_ES256_Y = "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0";
    static final String RFC7515_ES256_D = "jpsQnnGQmL-YBIffH1136cspYG6-0iY7X1fCE9-E9LI";

    static final String RFC8037_ED25519_X = "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo";

    static final String RFC7515_PAYLOAD =
        "{\"iss\":\"joe\",\r\n \"exp\":1300819380,\r\n \"http://example.com/is_root\":true}";
    static final String RFC7515_RS256_TOKEN =
        "eyJhbGciOiJSUzI1NiJ9." +
        "eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ." +
        "cC4hiUPoj9Eetdgtv3hF80EGrhuB__dzERat0XF9g2VtQgr9PJbu3XOiZj5RZmh7AAuHIm4Bh-0Qc_lF5YKt_O8W2Fp5jujGbds9uJdbF9CUAr7t1" +
        "dnZcAcQjbKBYNX4BAynRFdiuB--f_nZLgrnbyTyWzO75vRK5h6xBArLIARNPvkSjtQBMHlb1L07Qe7K0GarZRmB_eSN9383LcOLn6_dO--xi12jz" +
        "DwusC-eOkHWEsqtFZESc6BfI7noOPqvhJ1phCnvWh6IeYI2w9QOYEUipUTI8np6LbgGY9Fs98rqVt5AXLIhWkWywlVmtVrBp0igcN_IoypGlUPQG" +
        "e77Rw";
    static final String RFC7515_ES256_TOKEN =
        "eyJhbGciOiJFUzI1NiJ9." +
        "eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ." +
        "DtEhU3ljbEg8L38VWAfUAqOyKAM6-Xx-F4GawxaepmXFCgfTjDxw5djxLa8ISlSApmWQxfKTUJqPP3-Kg6NU1Q";
    static final String RFC8037_ED25519_TOKEN =
        "eyJhbGciOiJFZERTQSJ9.RXhhbXBsZSBvZiBFZDI1NTE5IHNpZ25pbmc." +
        "hgyY0il_MGCjP0JzlnLWG1PPOt7-09PGcvMg3AIbQR6dWbhijcNR4ki4iylGjg5BhVsPt9g7sVvpAr_MuM0KAg";

    static final KeyPair RFC7515_RS256;
    static final KeyPair RFC7515_ES256;
    static final KeyPair RSA_2048;
    static final KeyPair RSA_2048_OTHER;
    static final KeyPair RSA_1024;
    static final KeyPair EC_P256;
    static final KeyPair EC_P384;
    static final KeyPair EC_P521;
    static final KeyPair ED25519;
    static final KeyPair ED448;

    static
    {
        try
        {
            RFC7515_RS256 = rfc7515Rsa();
            RFC7515_ES256 = rfc7515Ec();
            RSA_2048 = generate("RSA", 2048, null);
            RSA_2048_OTHER = generate("RSA", 2048, null);
            RSA_1024 = generate("RSA", 1024, null);
            EC_P256 = generate("EC", 0, "secp256r1");
            EC_P384 = generate("EC", 0, "secp384r1");
            EC_P521 = generate("EC", 0, "secp521r1");
            ED25519 = generate("Ed25519", 0, null);
            ED448 = generate("Ed448", 0, null);
        }
        catch (GeneralSecurityException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    static JsonObject jwk(
        PublicKey key,
        String kid,
        String alg)
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        if (key instanceof RSAPublicKey rsa)
        {
            builder.add("kty", "RSA");
            builder.add("n", base64Url(rsa.getModulus(), 0));
            builder.add("e", base64Url(rsa.getPublicExponent(), 0));
        }
        else if (key instanceof ECPublicKey ec)
        {
            int length = (ec.getParams().getCurve().getField().getFieldSize() + 7) / 8;
            builder.add("kty", "EC");
            builder.add("crv", curveName(ec));
            builder.add("x", base64Url(ec.getW().getAffineX(), length));
            builder.add("y", base64Url(ec.getW().getAffineY(), length));
        }
        else if (key instanceof EdECPublicKey ed)
        {
            String curve = ed.getParams().getName();
            int length = "Ed25519".equals(curve) ? 32 : 57;
            byte[] encoded = littleEndian(ed.getPoint().getY(), length);
            if (ed.getPoint().isXOdd())
            {
                encoded[length - 1] |= (byte) 0x80;
            }
            builder.add("kty", "OKP");
            builder.add("crv", curve);
            builder.add("x", Base64.getUrlEncoder().withoutPadding().encodeToString(encoded));
        }

        if (kid != null)
        {
            builder.add("kid", kid);
        }

        if (alg != null)
        {
            builder.add("alg", alg);
        }

        return builder.build();
    }

    static String standardBase64(
        String urlSafe)
    {
        String standard = urlSafe.replace('-', '+').replace('_', '/');
        return standard + "=".repeat((4 - standard.length() % 4) % 4);
    }

    private static String base64Url(
        BigInteger value,
        int length)
    {
        byte[] bytes = value.toByteArray();

        if (length > 0 && bytes.length < length)
        {
            byte[] padded = new byte[length];
            System.arraycopy(bytes, 0, padded, length - bytes.length, bytes.length);
            bytes = padded;
        }
        else if (bytes.length > 1 && bytes[0] == 0)
        {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] littleEndian(
        BigInteger value,
        int length)
    {
        byte[] bigEndian = value.toByteArray();
        byte[] encoded = new byte[length];

        for (int i = 0; i < bigEndian.length && i < length; i++)
        {
            encoded[i] = bigEndian[bigEndian.length - 1 - i];
        }

        return encoded;
    }

    private static String curveName(
        ECPublicKey key)
    {
        return switch (key.getParams().getCurve().getField().getFieldSize())
        {
        case 256 -> "P-256";
        case 384 -> "P-384";
        default -> "P-521";
        };
    }

    private static KeyPair generate(
        String algorithm,
        int size,
        String curve) throws GeneralSecurityException
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);

        if (curve != null)
        {
            generator.initialize(new ECGenParameterSpec(curve));
        }
        else if (size > 0)
        {
            generator.initialize(size);
        }

        return generator.generateKeyPair();
    }

    private static KeyPair rfc7515Rsa() throws GeneralSecurityException
    {
        Base64.Decoder decoder = Base64.getUrlDecoder();
        BigInteger n = new BigInteger(1, decoder.decode(RFC7515_RS256_N));
        BigInteger e = new BigInteger(1, decoder.decode(RFC7515_RS256_E));
        BigInteger d = new BigInteger(1, decoder.decode(RFC7515_RS256_D));

        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = factory.generatePublic(new RSAPublicKeySpec(n, e));
        PrivateKey privateKey = factory.generatePrivate(new RSAPrivateKeySpec(n, d));

        return new KeyPair(publicKey, privateKey);
    }

    private static KeyPair rfc7515Ec() throws GeneralSecurityException
    {
        Base64.Decoder decoder = Base64.getUrlDecoder();
        BigInteger x = new BigInteger(1, decoder.decode(RFC7515_ES256_X));
        BigInteger y = new BigInteger(1, decoder.decode(RFC7515_ES256_Y));
        BigInteger d = new BigInteger(1, decoder.decode(RFC7515_ES256_D));

        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = parameters.getParameterSpec(ECParameterSpec.class);

        KeyFactory factory = KeyFactory.getInstance("EC");
        PublicKey publicKey = factory.generatePublic(new ECPublicKeySpec(new ECPoint(x, y), curve));
        PrivateKey privateKey = factory.generatePrivate(new ECPrivateKeySpec(d, curve));

        return new KeyPair(publicKey, privateKey);
    }

    private JwtTestKeys()
    {
    }
}
