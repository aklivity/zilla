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
package io.aklivity.zilla.runtime.guard.jwt.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.TestClock;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class JwtGuardIT
{
    private static final long CLOCK_MILLIS = 1_800_000_000_000L;

    private final K3poRule k3po = new K3poRule()
            .addScriptRoot("keys", "io/aklivity/zilla/specs/guard/jwt/config/keys")
            .addScriptRoot("net", "io/aklivity/zilla/specs/engine/streams/network")
            .addScriptRoot("app", "io/aklivity/zilla/specs/engine/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
            .directory("target/zilla-itests")
            .countersBufferCapacity(8192)
            .configurationRoot("io/aklivity/zilla/specs/guard/jwt/config")
            .clock(TestClock.of(CLOCK_MILLIS))
            .external("app0")
            .clean();

    @Rule
    public final TestRule chain = outerRule(k3po).around(engine).around(timeout);

    @Test
    @Configuration("guard.yaml")
    public void shouldInitialize() throws Exception
    {
    }

    @Test
    @Configuration("guard-keys-dynamic.yaml")
    @Specification({
        "${keys}/issuer"
    })
    public void shouldInitializeGuardWithDynamicKeys() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("guard-keys-implicit.yaml")
    @Specification({
        "${keys}/issuer"
    })
    public void shouldInitializeGuardWithImplicitKeys() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.rs256.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeRs256() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.rs384.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeRs384() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.rs512.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeRs512() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.ps256.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizePs256() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.ps384.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizePs384() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.ps512.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizePs512() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.es256.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeEs256() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.eddsa.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeEddsa() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.key.base64.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeKeyBase64() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.audience.array.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeAudienceArray() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.expiration.fractional.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeExpirationFractional() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.expiration.none.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeExpirationNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.not.before.reached.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeNotBeforeReached() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.identity.custom.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeIdentityCustom() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.roles.custom.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeRolesCustom() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.roles.scope.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeRolesScope() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.attributes.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeAttributes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.attributes.typed.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeAttributesTyped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.credentials.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.signature.tampered.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectSignatureTampered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.payload.tampered.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectPayloadTampered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.signature.invalid.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectSignatureInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.algorithm.differs.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectAlgorithmDiffers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.algorithm.none.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectAlgorithmNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.key.algorithm.none.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectKeyAlgorithmNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.key.type.ec.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectKeyTypeEc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.key.type.rsa.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectKeyTypeRsa() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.key.id.missing.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectKeyIdMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.key.id.unknown.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectKeyIdUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.header.critical.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectHeaderCritical() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.header.duplicate.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectHeaderDuplicate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.claims.duplicate.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectClaimsDuplicate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.claims.malformed.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectClaimsMalformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.claims.not.object.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectClaimsNotObject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.claims.not.json.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectClaimsNotJson() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.audience.missing.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectAudienceMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.audience.differs.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectAudienceDiffers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.issuer.differs.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectIssuerDiffers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.expired.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectExpired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.not.yet.valid.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectNotYetValid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.roles.insufficient.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectRolesInsufficient() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.credentials.malformed.yaml")
    @Specification({
        "${net}/exchange.rejected/client"
    })
    public void shouldRejectCredentialsMalformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.release.on.end.yaml")
    @Specification({
        "${net}/exchange.closed/client",
        "${app}/exchange.closed.authorized/server"
    })
    public void shouldDeauthorizeOnEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.expiration.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeExpiration() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.challenge.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.challenge.subject.missing.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeChallengeSubjectMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("authorize.session.unknown.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldAuthorizeSessionUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reauthorize.expiration.later.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldReauthorizeWhenExpirationLater() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reauthorize.expiration.earlier.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldReauthorizeWhenExpirationEarlier() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reauthorize.scope.broader.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldReauthorizeWhenScopeBroader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reauthorize.scope.narrower.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldReauthorizeWhenScopeNarrower() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reauthorize.subject.differs.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldReauthorizeWhenSubjectDiffers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reauthorize.context.differs.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldReauthorizeWhenContextDiffers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reauthorize.credentials.missing.yaml")
    @Specification({
        "${net}/handshake.authorized/client",
        "${app}/handshake.authorized/server"
    })
    public void shouldNotReauthorizeWhenCredentialsMissing() throws Exception
    {
        k3po.finish();
    }
}
