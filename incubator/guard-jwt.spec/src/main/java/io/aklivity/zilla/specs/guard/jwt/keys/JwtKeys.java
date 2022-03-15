/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.specs.guard.jwt.keys;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.KeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public final class JwtKeys
{
    public static final KeyPair RFC7515_RS256;
    public static final KeyPair RFC7515_ES256;

    static
    {
        // RFC 7515, section A.2.1
        Map<String, String> rsa256 = new HashMap<>();
        rsa256.put("kty", "RSA");
        rsa256.put("n", "ofgWCuLjybRlzo0tZWJjNiuSfb4p4fAkd_wWJcyQoTbji9k0l8W26mPddx" +
                        "HmfHQp-Vaw-4qPCJrcS2mJPMEzP1Pt0Bm4d4QlL-yRT-SFd2lZS-pCgNMs" +
                        "D1W_YpRPEwOWvG6b32690r2jZ47soMZo9wGzjb_7OMg0LOL-bSf63kpaSH" +
                        "SXndS5z5rexMdbBYUsLA9e-KXBdQOS-UTo7WTBEMa2R2CapHg665xsmtdV" +
                        "MTBQY4uDZlxvb3qCo5ZwKh9kG4LT6_I5IhlJH7aGhyxXFvUK-DWNmoudF8" +
                        "NAco9_h9iaGNj8q2ethFkMLs91kzk2PAcDTW9gb54h4FRWyuXpoQ");
        rsa256.put("e", "AQAB");
        rsa256.put("d", "Eq5xpGnNCivDflJsRQBXHx1hdR1k6Ulwe2JZD50LpXyWPEAeP88vLNO97I" +
                        "jlA7_GQ5sLKMgvfTeXZx9SE-7YwVol2NXOoAJe46sui395IW_GO-pWJ1O0" +
                        "BkTGoVEn2bKVRUCgu-GjBVaYLU6f3l9kJfFNS3E0QbVdxzubSu3Mkqzjkn" +
                        "439X0M_V51gfpRLI9JYanrC4D4qAdGcopV_0ZHHzQlBjudU2QvXt4ehNYT" +
                        "CBr6XCLQUShb1juUO1ZdiYoFaFQT5Tw8bGUl_x_jTj3ccPDVZFD9pIuhLh" +
                        "BOneufuBiB4cS98l2SR_RQyGWSeWjnczT0QU91p1DhOVRuOopznQ");
        rsa256.put("p", "4BzEEOtIpmVdVEZNCqS7baC4crd0pqnRH_5IB3jw3bcxGn6QLvnEtfdUdi" +
                        "YrqBdss1l58BQ3KhooKeQTa9AB0Hw_Py5PJdTJNPY8cQn7ouZ2KKDcmnPG" +
                        "BY5t7yLc1QlQ5xHdwW1VhvKn-nXqhJTBgIPgtldC-KDV5z-y2XDwGUc");
        rsa256.put("q", "uQPEfgmVtjL0Uyyx88GZFF1fOunH3-7cepKmtH4pxhtCoHqpWmT8YAmZxa" +
                        "ewHgHAjLYsp1ZSe7zFYHj7C6ul7TjeLQeZD_YwD66t62wDmpe_HlB-TnBA" +
                        "-njbglfIsRLtXlnDzQkv5dTltRJ11BKBBypeeF6689rjcJIDEz9RWdc");
        rsa256.put("dp", "BwKfV3Akq5_MFZDFZCnW-wzl-CCo83WoZvnLQwCTeDv8uzluRSnm71I3Q" +
                        "CLdhrqE2e9YkxvuxdBfpT_PI7Yz-FOKnu1R6HsJeDCjn12Sk3vmAktV2zb" +
                        "34MCdy7cpdTh_YVr7tss2u6vneTwrA86rZtu5Mbr1C1XsmvkxHQAdYo0");
        rsa256.put("dq", "h_96-mK1R_7glhsum81dZxjTnYynPbZpHziZjeeHcXYsXaaMwkOlODsWa" +
                        "7I9xXDoRwbKgB719rrmI2oKr6N3Do9U0ajaHF-NKJnwgjMd2w9cjz3_-ky" +
                        "NlxAr2v4IKhGNpmM5iIgOS1VZnOZ68m6_pbLBSp3nssTdlqvd0tIiTHU");
        rsa256.put("qi", "IYd7DHOhrWvxkwPQsRM2tOgrjbcrfvtQJipd-DlcxyVuuM9sQLdgjVk2o" +
                        "y26F0EmpScGLq2MowX7fhd_QJQ3ydy5cY7YIBi87w93IKLEdfnbJtoOPLU" +
                        "W0ITrJReOgo1cq9SbsxYawBgfp_gh6A5603k2-ZQwVK0JKSHuLFkuQ3U");

        // RFC 7515, section A.3.1
        Map<String, String> es256 = new HashMap<>();
        es256.put("kty", "EC");
        es256.put("crv", "P-256");
        es256.put("x", "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU");
        es256.put("y", "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0");
        es256.put("d", "jpsQnnGQmL-YBIffH1136cspYG6-0iY7X1fCE9-E9LI");

        try
        {
            RFC7515_RS256 = initRSAKeyPair(rsa256);
            RFC7515_ES256 = initECKeyPair(es256);
        }
        catch (GeneralSecurityException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    private static KeyPair initRSAKeyPair(
        Map<String, String> params) throws GeneralSecurityException
    {
        Base64.Decoder base64 = Base64.getUrlDecoder();

        BigInteger n = new BigInteger(1, base64.decode(params.get("n")));
        BigInteger e = new BigInteger(1, base64.decode(params.get("e")));

        BigInteger d = new BigInteger(1, base64.decode(params.get("d")));
        BigInteger p = new BigInteger(1, base64.decode(params.get("p")));
        BigInteger q = new BigInteger(1, base64.decode(params.get("q")));
        BigInteger dp = new BigInteger(1, base64.decode(params.get("dp")));
        BigInteger dq = new BigInteger(1, base64.decode(params.get("dq")));
        BigInteger qi = new BigInteger(1, base64.decode(params.get("qi")));

        KeySpec publicKeySpec = new RSAPublicKeySpec(n, e);
        KeySpec privateKeySpec = new RSAPrivateCrtKeySpec(n, e, d, p, q, dp, dq, qi);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

        return new KeyPair(publicKey, privateKey);
    }

    private static KeyPair initECKeyPair(
        Map<String, String> params) throws GeneralSecurityException
    {
        Base64.Decoder base64 = Base64.getUrlDecoder();

        String crv = params.get("crv");
        BigInteger x = new BigInteger(1, base64.decode(params.get("x")));
        BigInteger y = new BigInteger(1, base64.decode(params.get("y")));
        BigInteger d = new BigInteger(1, base64.decode(params.get("d")));

        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(String.format("NIST %s", crv)));
        ECParameterSpec curve = parameters.getParameterSpec(ECParameterSpec.class);

        KeySpec publicKeySpec = new ECPublicKeySpec(new ECPoint(x, y), curve);
        KeySpec privateKeySpec = new ECPrivateKeySpec(d, curve);

        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

        return new KeyPair(publicKey, privateKey);
    }

    private JwtKeys()
    {
    }
}
