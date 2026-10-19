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
package io.aklivity.zilla.runtime.common.jwt.bench;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;

import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.NumericDate;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.common.jwt.Jwk;
import io.aklivity.zilla.runtime.common.jwt.Jws;
import io.aklivity.zilla.runtime.common.jwt.JwsAlgorithm;
import io.aklivity.zilla.runtime.common.jwt.JwtClaims;
import io.aklivity.zilla.runtime.common.jwt.JwtException;
import io.aklivity.zilla.runtime.common.jwt.JwtTestKeys;

/**
 * Measures the token authorization sequence a guard performs per presented credential: compact parse,
 * signature verification, then claims parse and read. The {@code authorize} benchmark runs the whole
 * sequence through {@code common-jwt}; {@code authorizeJose4j} runs the same sequence through jose4j as a
 * baseline. The remaining benchmarks isolate one stage each so allocation (under {@code -prof gc}) can be
 * attributed: {@code parse}, {@code verify} (over an already parsed token) and {@code claims}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(SECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
public class JwsVerifyBM
{
    @Param({"RS256", "ES256"})
    public String algorithm;

    private String token;
    private String payload;
    private Jws parsed;
    private Jwk jwk;
    private JsonWebSignature jose4jSignature;
    private PublicKey publicKey;

    @Setup
    public void init() throws Exception
    {
        JwsAlgorithm jws = JwsAlgorithm.of(algorithm);
        KeyPair pair = jws == JwsAlgorithm.RS256 ? JwtTestKeys.RSA_2048 : JwtTestKeys.EC_P256;

        long exp = Instant.now().getEpochSecond() + 3600L;
        this.payload = "{\"iss\":\"https://idp.example.com\",\"aud\":\"gateway\",\"sub\":\"alice@example.com\"," +
            "\"nbf\":" + (exp - 7200L) + ",\"exp\":" + exp + ",\"scope\":\"read:stream write:stream\"," +
            "\"groups\":[\"admins\",\"operators\"],\"user\":{\"id\":\"12345\",\"tier\":\"gold\"}}";
        this.token = Jws.sign(jws, pair.getPrivate(), "test", payload);
        this.parsed = Jws.parse(token);
        this.jwk = Jwk.parse(JwtTestKeys.jwk(pair.getPublic(), "test", algorithm));
        this.publicKey = pair.getPublic();
        this.jose4jSignature = new JsonWebSignature();
    }

    @Benchmark
    public long authorize() throws JwtException
    {
        Jws jws = Jws.parse(token);
        String kid = jws.keyId();
        String alg = jws.algorithm();
        String verified = jws.verifiedPayload(jwk);

        JwtClaims claims = JwtClaims.parse(verified);
        String sub = claims.getSubject();
        Instant nbf = claims.getNotBefore();
        Instant exp = claims.getExpirationTime();
        String iss = claims.getIssuer();
        List<String> aud = claims.getAudience();
        Object scope = claims.getClaimValue("scope");

        return kid.length() + alg.length() + sub.length() + iss.length() + aud.size() + scope.toString().length() +
            nbf.toEpochMilli() + exp.toEpochMilli();
    }

    @Benchmark
    public long authorizeJose4j() throws Exception
    {
        jose4jSignature.setCompactSerialization(token);
        String kid = jose4jSignature.getKeyIdHeaderValue();
        String alg = jose4jSignature.getAlgorithmHeaderValue();
        jose4jSignature.setKey(null);
        jose4jSignature.setKey(publicKey);
        jose4jSignature.verifySignature();
        String verified = jose4jSignature.getPayload();

        org.jose4j.jwt.JwtClaims claims = org.jose4j.jwt.JwtClaims.parse(verified);
        String sub = claims.getSubject();
        NumericDate nbf = claims.getNotBefore();
        NumericDate exp = claims.getExpirationTime();
        String iss = claims.getIssuer();
        List<String> aud = claims.getAudience();
        Object scope = claims.getClaimValue("scope");

        return kid.length() + alg.length() + sub.length() + iss.length() + aud.size() + scope.toString().length() +
            nbf.getValueInMillis() + exp.getValueInMillis();
    }

    @Benchmark
    public Jws parse() throws JwtException
    {
        return Jws.parse(token);
    }

    @Benchmark
    public String verify() throws JwtException
    {
        return parsed.verifiedPayload(jwk);
    }

    @Benchmark
    public long claims() throws JwtException
    {
        JwtClaims claims = JwtClaims.parse(payload);
        String sub = claims.getSubject();
        Instant nbf = claims.getNotBefore();
        Instant exp = claims.getExpirationTime();
        String iss = claims.getIssuer();
        List<String> aud = claims.getAudience();
        Object scope = claims.getClaimValue("scope");

        return sub.length() + iss.length() + aud.size() + scope.toString().length() + nbf.toEpochMilli() + exp.toEpochMilli();
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JwsVerifyBM.class.getSimpleName())
            .build();

        new Runner(opt).run();
    }
}
