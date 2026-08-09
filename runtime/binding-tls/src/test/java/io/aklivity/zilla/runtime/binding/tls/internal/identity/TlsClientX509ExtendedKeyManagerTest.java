/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tls.internal.identity;

import static io.aklivity.zilla.runtime.common.x509.X509Fields.SUBJECT_CN;
import static io.aklivity.zilla.runtime.common.x509.X509Fields.SUBJECT_DN;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509ExtendedKeyManager;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.tls.internal.TlsConfiguration;
import io.aklivity.zilla.runtime.common.x509.X509Fields;
import io.aklivity.zilla.runtime.engine.Configuration;

public class TlsClientX509ExtendedKeyManagerTest
{
    private static final Pattern KEY_ENTRY_PATTERN = Pattern.compile(
        "(?<key>-----BEGIN PRIVATE KEY-----[^-]+-----END PRIVATE KEY-----[^-]*)" +
        "(?<chain>(?:-----BEGIN CERTIFICATE-----[^-]+-----END CERTIFICATE-----[^-]*)+)");

    private static final String[] RSA = new String[] { "RSA" };

    // subject CN=alpha, O=Aklivity
    private static final String ALPHA = """
        -----BEGIN PRIVATE KEY-----
        MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCgo9fDALIEBRH9
        vf73QQWL88adW6sVaGUHcD/m15ns4y1yUuIzAaNuQVb5PxYBguhK5bg33qU+2336
        fBdp7JTypd2NY/3Bi49R/NpjYKwpxfB44pInw1dZk2RtKral8+4f21DYb3m2bwa0
        ZG7vQiykPyCFQPYXhVqGuTHajlIJne83Hw9WWQhz+x7yzNbumOvjBaqQ8rK6tg9p
        xY9KwXovjpMEArZ9Olke6F/4KQKiswMwmWLxnjFIHSGWHnKPLAr4SgTDgyrhMmaY
        Ax9F7+YkRAP57JaEs0ShyWpmI/nimEFGKDVbWYGRj+kL0cpIi/iSGRrqg9KBIek+
        zIbtPuU3AgMBAAECggEAPHCo85PFZsHJnSDpV3o9bgTQi9A7tJhMutm/EUm5fmmT
        ZdpNhUduiENJSAo2a2cno1Z/U3YP0nCfG6mo3Y/IEuvZSiN/DF1IFV7/hDiRTj5N
        Vg5ZU2Cp8ugD9xcFFbeLtqy75FM9BikL6r+HBbpuNXYS9FAWx47DGsF/Qm4d68PJ
        7F7dBIIi/XQREzEXlin2HlATPHrLXroZHtoSJo5H5kjj2O11q8/PhS8D0hvU/dvQ
        X5BaVrBskw6uJPesdCP/FD98vA22ouWjNZZ8fDpW4JQgDKGGo7xRvZk8w0+xmp5a
        pnjjBePqrFt50Heji+zhteG/5XYvGqu3rniF0+ejEQKBgQDMlXAzEOQtyNYeg1Lj
        3a3BeE7MdQJCjqjthkP5YpRzWlCs+FzTymXWoDJqVoY11+HMBBKX6tX8THb87cwT
        QE8nukWi6EO9x6VSUfPTvsFXiR9VObvqRBPJVCcBKmpFWzTd36OeDXD3DD6PuUI1
        PC/pCQ34ZXgISuQQC776s2I3+wKBgQDJAyQvBcgqLagJxCaC7Vq/r9CpR5W+CtBG
        Rzk7s44n/L9hwnY/IH2OjG4loE+GUoCA1JqfrlxdujqtP0Hu5LZbkUEbXgPeIgpu
        QJoaGil6qbriHPfkCEe3qktSqDZ81iA+s6lD/aqTR8WFojJZ+qS3x0y7FNYWkrym
        ncHcFINW9QKBgQDFzBntlYA3b/ztBWUn4FU1P99XuC1WMl3LQEwKGrCSkph7k6kU
        L5SknMABpeQevLmXqbxcWvVG+Jr2UedEGaIuM99N8ShFnXgOjiOi7sQ5oN7FZIt1
        pRpIRe9sJhkXM2Z6udWUY0R75r+575C+rZFYyqNSLLCPu/CiPukHtFXa7wKBgCrv
        59e/hW01CXLkmlFXQA44EPtH1j2c87Y/WoNdLF1wSLUqI7YCS6+aYaDZ6ILJtrdO
        MqUvSn2e3Q/KtlvAMQ/ILD8bSvo1DUNVu1UeB4QzIz9Pbsgf2Knrz2Edm135IzQh
        jmN+mOQS7adI6TZ9KpgzEznXDpAN0gxJIHLRUujNAoGAZP/Zy8PKGa2JhwlIwoKV
        Hwj80MhoUx2JJjGN1o2xXkOrJlwyadeKmwefmzC+IU+9eH9F3Z1PzCSVWiCNFMUB
        lzZGuRha2OlFJPsQDSgi7IItPB5cfWiSOuMWp8LUMypvsTP5peh0XyfqlQkzcZ/j
        jK0UW97avlGOpC8dKTsTaD4=
        -----END PRIVATE KEY-----
        -----BEGIN CERTIFICATE-----
        MIIDTjCCAjagAwIBAgIUcznhiAqbR3wfBoNqZfjUTRNAYzswDQYJKoZIhvcNAQEL
        BQAwNzELMAkGA1UEBhMCVVMxFDASBgNVBAoMC0V4YW1wbGUgSW5jMRIwEAYDVQQD
        DAlDb21tb24gQ0EwIBcNMjYwODA5MDI1MjUwWhgPMjEyNjA3MTYwMjUyNTBaMCMx
        ETAPBgNVBAoMCEFrbGl2aXR5MQ4wDAYDVQQDDAVhbHBoYTCCASIwDQYJKoZIhvcN
        AQEBBQADggEPADCCAQoCggEBAKCj18MAsgQFEf29/vdBBYvzxp1bqxVoZQdwP+bX
        mezjLXJS4jMBo25BVvk/FgGC6ErluDfepT7bffp8F2nslPKl3Y1j/cGLj1H82mNg
        rCnF8HjikifDV1mTZG0qtqXz7h/bUNhvebZvBrRkbu9CLKQ/IIVA9heFWoa5MdqO
        Ugmd7zcfD1ZZCHP7HvLM1u6Y6+MFqpDysrq2D2nFj0rBei+OkwQCtn06WR7oX/gp
        AqKzAzCZYvGeMUgdIZYeco8sCvhKBMODKuEyZpgDH0Xv5iREA/nsloSzRKHJamYj
        +eKYQUYoNVtZgZGP6QvRykiL+JIZGuqD0oEh6T7Mhu0+5TcCAwEAAaNkMGIwEwYD
        VR0lBAwwCgYIKwYBBQUHAwIwCwYDVR0PBAQDAgeAMB0GA1UdDgQWBBTjDsDfCAFn
        vFtx/htSPm9qcf3bLzAfBgNVHSMEGDAWgBQvrzqfjMhIhNhoTgQr+PD6fBdqSzAN
        BgkqhkiG9w0BAQsFAAOCAQEAF91Xd9/0EOGG9DMm8vgB7pn+5nqRjvSRboVJHN2h
        poudELQnyk0wNE5B+6uL+pYEvK1T1VF4Bfilqva8ufSAY6vBdTYPdYL9ZZRTyes4
        1W8dcm7CX7bgfRQrKGjItQ5WFnSQSvxoUlBVgtDqwDsZjv38Ogx+C0huCWXLl0TU
        86dAnhFW8U2cBRsZwyNb3Rv+LZ2IRj1V9iLcDflYEUfegI0gJ42O6J6Ul4vKD7zt
        FmUQRJFLgYEpGJw+mPkNDMENvflGZkyJsfDgpDOH6F7iDvgWbnSg/1topjhwr1kc
        HxT+OfSuaUetHO5W18sFPzHPVsVLzMuDhn4vOatLRHcSNQ==
        -----END CERTIFICATE-----
        """;

    // subject CN=beta, O=Aklivity
    private static final String BETA = """
        -----BEGIN PRIVATE KEY-----
        MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCMSFHkwkhRA/br
        md4xOFX6qC0T4qBXGk6W81tNuHMOBbDGVD/VkTMUaZGVPxeu+4Z8OkCzIKbYCEp7
        pyGkzmXRSEUgR863VPIGQItpys6vTaQBlgaBBCua8Kyjm91Tc68For5oapD9of4K
        kCST0hCxJIOekJE/bcPY+MrKEeHQoh/Y8pkzohyKGYqpJWVz4V7AXqgt65msjMyG
        YAMrysZskTc5l5Y3KuFPBq1C2VZY8MQl1Bkb9TRDpdVXqHRkpH60I4g4IrhEQQpF
        4RoV81ZNmPvZ8OdvF7cra+tyggj2pvQaedN6OxcJsdXgeAFRZFDuXZkyvqtCKl7a
        wj47f14LAgMBAAECggEABqh2DpP0B7hW81axt4dTeVqbDRsP/iJXAY4aUsWC3HMD
        sV4VS4chNAJQsRM2Bg2VE/dV08qkcJNwKY9EpwROy9CQy1ftJUFoXUiZ9VL1XVej
        hZm/xEbU5P0wTHGc3xWaBHv1apJIQGl8W7b8rXFmbmnrv05gPnDwvy8LvOx9WcAO
        Hy3GuY84lv0EBNfmbT8oWWz0c38KhEyfIQ7d5j4sxlVnWhkE2Zqbs4ByuIdaY62+
        tdhBa/9eUJAqTXORZuY/ONyhWNsMBGWzMzSocz2haRpuBWBhmdcn3gjasBc/EMs2
        BmVUzKqhfYb11Xn6Ki2mraJfwqLP1PEVbZwg4wxvtQKBgQDFJFvGqaZ/DwlxHRt0
        Wzg+69kCvCkkuswvnkHI0CUaVlknHf2ZMyai375dtUN/t8ylOA9acPD2e6kaLtzi
        oM4Ya/zojxEMGgOp5EBIOpjtIg3XTc6Z4MN8IXA7Y6l8Dve+iB2qfuwA51cWYyo+
        61x8VNqaSbpXfWyDxq9XRottNwKBgQC2KipwU0Gu1ET6aEkLi1dkAqsakGBkntzu
        P1E3rJW+z00d7zXUzRVg84eAEmoaCDaQyTqz3Zu7v/bmEfeSYotfEkvjpJehGZjO
        TaP5vY2hnCVIsaFriWkzU/7WieSDMKnVWh5V1h09YRKUqBM7KeyZsG2wMAmY4Zhr
        S+k3bvrfzQKBgQC+ucpAjOkTi8yqrmpdS9NCrJIq3zc+3amleApU6Wq77EYcXht3
        F9GNScG2jyNacMuAHJhd+15kWIblWelBKCm73t2zcJYhixl4R+cY+ffxkzyozBHR
        NW5qrT8TdfwGlcSBhNaOmHRi+215rz6z2KiFcOJ82k95TVKqupHwccN6SQKBgQCA
        EODJGx8gRAdCbDJ+NTX5D48vG7VIyA7WSBlXJLQZ8y9qDhQwSQXwpQTr2wZQytlh
        rLbiYLftpKV1VchRV4pYCkrj/YDEN3SgBSF0n/iEV3w2wPqU2YPmG/Ua6tKGEJ41
        aifNfLwNvMMNU6RBgdXerpROu1bj7iXzuOh6mkExOQKBgCHpGApNvkKlPNq9SL92
        ZO6GnOCgRs1dRFk9/tVE9BP1V/rrgCj27EkXlKYd/Hirc108aSzvmiiNF6vxzFxe
        Qn01c4ItmXnkiv70a2krijvO4lIfgltasGly2H5EAwoekru1adfjM3+gerpVrJPf
        8ujdcsjSVTVt/xUO3D7su+CN
        -----END PRIVATE KEY-----
        -----BEGIN CERTIFICATE-----
        MIIDTTCCAjWgAwIBAgIUcznhiAqbR3wfBoNqZfjUTRNAYzwwDQYJKoZIhvcNAQEL
        BQAwNzELMAkGA1UEBhMCVVMxFDASBgNVBAoMC0V4YW1wbGUgSW5jMRIwEAYDVQQD
        DAlDb21tb24gQ0EwIBcNMjYwODA5MDI1MjUwWhgPMjEyNjA3MTYwMjUyNTBaMCIx
        ETAPBgNVBAoMCEFrbGl2aXR5MQ0wCwYDVQQDDARiZXRhMIIBIjANBgkqhkiG9w0B
        AQEFAAOCAQ8AMIIBCgKCAQEAjEhR5MJIUQP265neMThV+qgtE+KgVxpOlvNbTbhz
        DgWwxlQ/1ZEzFGmRlT8XrvuGfDpAsyCm2AhKe6chpM5l0UhFIEfOt1TyBkCLacrO
        r02kAZYGgQQrmvCso5vdU3OvBaK+aGqQ/aH+CpAkk9IQsSSDnpCRP23D2PjKyhHh
        0KIf2PKZM6IcihmKqSVlc+FewF6oLeuZrIzMhmADK8rGbJE3OZeWNyrhTwatQtlW
        WPDEJdQZG/U0Q6XVV6h0ZKR+tCOIOCK4REEKReEaFfNWTZj72fDnbxe3K2vrcoII
        9qb0GnnTejsXCbHV4HgBUWRQ7l2ZMr6rQipe2sI+O39eCwIDAQABo2QwYjATBgNV
        HSUEDDAKBggrBgEFBQcDAjALBgNVHQ8EBAMCB4AwHQYDVR0OBBYEFEM7bh5gPKwG
        wUKiTPDtZRZsYZ2uMB8GA1UdIwQYMBaAFC+vOp+MyEiE2GhOBCv48Pp8F2pLMA0G
        CSqGSIb3DQEBCwUAA4IBAQBshs/bnTRr7+yHAPlNaNA45CMsytQVWN5k26T5ZLPU
        3OAt9cBlO/EIa2lcaM0bYFa+MdFrhCyncAAibFLAvzl7cLu726KUn2sN5ERK1nkf
        WRrT2QIDmIN7ZsRXQ8mNxR+Q9n220bq9LFCB8kVJDjs5Idrjr1I0/FqC8KkhlYBc
        mogdJo14ZwmYSUltMBNVweeEjrK5pDAqVL6q8j64MEYzZ2kMSO4IoEGabuxFxwOz
        UznAC4hw1+n4XMP6YBWD/RxrYOs97ILKlotPakFbGsQ282gGEFVNy5r36PAPnPKE
        LJD+aom/reaujfnncke/ESM+bchmlBt6VItHHuBmxqfR
        -----END CERTIFICATE-----
        """;

    // subject CN=beta, O=Other -- shares a common name with BETA, so a selection on
    // subject.cn alone cannot tell the two apart
    private static final String BETA_OTHER = """
        -----BEGIN PRIVATE KEY-----
        MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCtts8Of130VbuO
        emFQy+O4eNBTMJtMRVXdAwi/7ed+bXYKXQyHu8Wk5XZ1X/UwhJRkXGoHgTUQlD+G
        qCWBvM4luTDmCsqYXLbh3B7GPBCVOqcRvAasjsEi4EV+g8HdZ8XalHTPr0wd+QHg
        7LpgEEor/MvkWFeBbm1lB0HCbCjqhVs699CeskmqQu0XQVD/myD+U6llf0pydtBc
        t2ZMu96Ah7uF49wd1MvObYoXZcYHI9Y6oY0obpZbQl9Nm9ouBQ/fj/Xhlp6Hko0S
        5J2A8DC+utfbFNFk5+Vr0eRxscR5ywnq7tVk1yWTHVgdHWf4b1cs4T9J4iPvyeSb
        VFDSGgRBAgMBAAECggEADOzUcKd06exkGWiNK5oUDkJ8+6ea8nyZNCnY4ZD1v8As
        v34nY8UKomeJXhfe8jZ3JRWENR09wcdKB76klcfizcc+9mrYoqgJWnpKluTatgPI
        cQcctwOjJrFpsk1i5BKn08jmfRp1ryg7Jc1vW5zyq92yHQIetqxs+s6kq67pB3P3
        m/uzRt6lUZEAZSrGn8XQAEoiYZztyFN+jcqIKcmFrp44cU4F1A4NUXyXSiwS/GuS
        GwdFF03wmOnWXQz6bs7Z76RBN9sYx1Zzh1yP/HWc3iNtyMnmSpr7nAIQ/vIh4K4A
        nZr5e7XKTKHpFGJOb+Xeddw96I3Yxaocm5gewwT8jQKBgQDsofn2fXkJw9/qsSul
        zETc/PIxroxzXr4yZ2duczZ3+ZtWF/cds3AUxrrS969u/XZ0hSZNkES3zmUsR/TA
        kDN7gx+EKiUf55LWkupXFEHOV6gvL6NFlY0X8cxaOccd9J5d32OLPafjZu29TRe4
        GBPLWJHaSnM8/5A1bRs9B3Ks7QKBgQC77olDeqxpAklW1ctXMvJRmO0YBYYbOI+p
        I4U7LS6lnFyEvnKOxLLXNcwcShl6CsN+5LKx0OMyfcYf2kHXwQ6cP96bvIulNyih
        SXBrdjEGZb6tVKpit0GXd+mVc+CUca1ija9uSgeiVEjRzacQ+fK2YZNGdDEHEUDm
        28LAViheJQKBgBbjV+0ql8GFC6yEtIcV6fdCFB8QFg+2s0jmzY9WX4ddQlQif0mF
        KKspybpMMNDYfVOp6VmJQcxRj2GoGBlkGUayGSNMfEjIumA816PSlsbhnafqwK0j
        WQFe5vg3LHZOSd8kk1lNfma1dvtfcJLi6U864uitWNYmlglE42SUytfFAoGBAIWu
        4g+RRPGRwc+2V2YZBIyMxyNOUp4sduzvKof01PjTqHB49Q6f10QFrcL15veMjpJG
        ZuLcX7F8DKJ95FZwq070lAqebYvCF/HbRs/6jFcrqx6rWmTMTSlNEGjSvA98acTw
        WrmOia2sXPx5WP1Xf5LDCDuWzYVkfZF7BCdDYoXNAoGAD67I1pBLJzzlvrh74w7h
        vih6P4TjDDLW/+hKCwqoClu/tqGRhPdBtnZ4vjP9wGRMXgLIthUk5nhA3ZDQ4DCZ
        2Lvnh7AbKG/x+5H4QFbSRvXGdS0NyUhrbcIMc7xCZS1AYOxjybuCnqKQVqMD1C+f
        CE0HCKaEKWSuyUaLqNpIX9M=
        -----END PRIVATE KEY-----
        -----BEGIN CERTIFICATE-----
        MIIDSjCCAjKgAwIBAgIUcznhiAqbR3wfBoNqZfjUTRNAYz4wDQYJKoZIhvcNAQEL
        BQAwNzELMAkGA1UEBhMCVVMxFDASBgNVBAoMC0V4YW1wbGUgSW5jMRIwEAYDVQQD
        DAlDb21tb24gQ0EwIBcNMjYwODA5MDI1MjUwWhgPMjEyNjA3MTYwMjUyNTBaMB8x
        DjAMBgNVBAoMBU90aGVyMQ0wCwYDVQQDDARiZXRhMIIBIjANBgkqhkiG9w0BAQEF
        AAOCAQ8AMIIBCgKCAQEArbbPDn9d9FW7jnphUMvjuHjQUzCbTEVV3QMIv+3nfm12
        Cl0Mh7vFpOV2dV/1MISUZFxqB4E1EJQ/hqglgbzOJbkw5grKmFy24dwexjwQlTqn
        EbwGrI7BIuBFfoPB3WfF2pR0z69MHfkB4Oy6YBBKK/zL5FhXgW5tZQdBwmwo6oVb
        OvfQnrJJqkLtF0FQ/5sg/lOpZX9KcnbQXLdmTLvegIe7hePcHdTLzm2KF2XGByPW
        OqGNKG6WW0JfTZvaLgUP34/14Zaeh5KNEuSdgPAwvrrX2xTRZOfla9HkcbHEecsJ
        6u7VZNclkx1YHR1n+G9XLOE/SeIj78nkm1RQ0hoEQQIDAQABo2QwYjATBgNVHSUE
        DDAKBggrBgEFBQcDAjALBgNVHQ8EBAMCB4AwHQYDVR0OBBYEFDWB9PwlKvbo2Apl
        98dAZlS3wLHgMB8GA1UdIwQYMBaAFC+vOp+MyEiE2GhOBCv48Pp8F2pLMA0GCSqG
        SIb3DQEBCwUAA4IBAQAXZffH2sQs74qmJEseOeKKzWqXWaZ7xTNfUSGe3Xf/rMNQ
        3qK/ffhvkXWftzD2IgvjJ6mYhgwBIkkpk9H3MYFbIB1Nlhe2IRHKN6cvQbsrsjrB
        n0gRdBzkykeAFrGGqMuCRBUonoZjpsoSo5CxhEE5Mk9ybGlflhAxlFBt3Aj3ET1e
        LJbe0wydULdaDu+ZO1oTlldoU8ZFDthnYw43N4aQHsaoxjzo5VySiw4owLyApCvk
        LP1HtOp3qj9j0qWfy/BldbypTNF2Fv+Zwhe8I7uHa6ZMCTmO9wxNHccqyP2wrpuC
        cwjYPccFAB27vTh148l1+Y2mskkrmxlupumv50jQ
        -----END CERTIFICATE-----
        """;

    @Test
    public void shouldChooseKeyMatchingSubjectCommonName()
    {
        TlsClientX509ExtendedKeyManager keys = newKeyManager(Map.of("alpha", ALPHA, "beta", BETA));

        String alias = keys.chooseEngineClientAlias(RSA, null, newEngine(Map.of(SUBJECT_CN, "beta")));

        assertThat(field(keys, alias, SUBJECT_CN), equalTo("beta"));
    }

    @Test
    public void shouldChooseOtherKeyMatchingSubjectCommonName()
    {
        TlsClientX509ExtendedKeyManager keys = newKeyManager(Map.of("alpha", ALPHA, "beta", BETA));

        String alias = keys.chooseEngineClientAlias(RSA, null, newEngine(Map.of(SUBJECT_CN, "alpha")));

        assertThat(field(keys, alias, SUBJECT_CN), equalTo("alpha"));
    }

    @Test
    public void shouldChooseKeyMatchingSubjectDistinguishedName()
    {
        TlsClientX509ExtendedKeyManager keys = newKeyManager(Map.of("alpha", ALPHA, "beta", BETA));

        String alias = keys.chooseEngineClientAlias(RSA, null, newEngine(Map.of(SUBJECT_DN, "cn=beta,o=aklivity")));

        assertThat(field(keys, alias, SUBJECT_CN), equalTo("beta"));
    }

    @Test
    public void shouldChooseKeyMatchingEveryProperty()
    {
        TlsClientX509ExtendedKeyManager keys = newKeyManager(Map.of("beta", BETA, "other", BETA_OTHER));

        String alias = keys.chooseEngineClientAlias(RSA, null,
            newEngine(Map.of(SUBJECT_CN, "beta", SUBJECT_DN, "cn=beta,o=other")));

        assertThat(field(keys, alias, SUBJECT_DN), equalTo("cn=beta,o=other"));
    }

    @Test
    public void shouldNotChooseKeyWhenOnePropertyDoesNotMatch()
    {
        TlsClientX509ExtendedKeyManager keys = newKeyManager(Map.of("alpha", ALPHA, "beta", BETA));

        String alias = keys.chooseEngineClientAlias(RSA, null,
            newEngine(Map.of(SUBJECT_CN, "beta", SUBJECT_DN, "cn=alpha,o=aklivity")));

        assertThat(alias, nullValue());
    }

    @Test
    public void shouldNotChooseKeyWhenNoCandidateMatches()
    {
        TlsClientX509ExtendedKeyManager keys = newKeyManager(Map.of("alpha", ALPHA, "beta", BETA));

        String alias = keys.chooseEngineClientAlias(RSA, null, newEngine(Map.of(SUBJECT_CN, "gamma")));

        assertThat(alias, nullValue());
    }

    @Test
    public void shouldDistinguishKeysByDifferingCommonName()
    {
        TlsClientX509ExtendedKeyManager keys = newKeyManager(Map.of("alpha", ALPHA, "beta", BETA));

        assertThat(keys.indistinguishableSubjects(Set.of(SUBJECT_CN)), empty());
    }

    @Test
    public void shouldNotDistinguishKeysBySharedCommonName()
    {
        TlsClientX509ExtendedKeyManager keys = newKeyManager(Map.of("beta", BETA, "other", BETA_OTHER));

        assertThat(keys.indistinguishableSubjects(Set.of(SUBJECT_CN)),
            containsInAnyOrder("cn=beta,o=aklivity", "cn=beta,o=other"));
    }

    @Test
    public void shouldDistinguishKeysSharingCommonNameByDistinguishedName()
    {
        TlsClientX509ExtendedKeyManager keys = newKeyManager(Map.of("beta", BETA, "other", BETA_OTHER));

        assertThat(keys.indistinguishableSubjects(Set.of(SUBJECT_CN, SUBJECT_DN)), empty());
    }

    private static String field(
        TlsClientX509ExtendedKeyManager keys,
        String alias,
        String name)
    {
        Map<String, List<String>> fields = X509Fields.resolve(keys.getCertificateChain(alias)[0]);
        return fields.get(name).get(0);
    }

    private static SSLEngine newEngine(
        Map<String, String> certificate)
    {
        SSLSession session = mock(SSLSession.class);
        when(session.getValue(TlsClientX509ExtendedKeyManager.CERTIFICATE_FIELDS_KEY)).thenReturn(certificate);

        SSLEngine engine = mock(SSLEngine.class);
        when(engine.getSession()).thenReturn(session);

        return engine;
    }

    private static TlsClientX509ExtendedKeyManager newKeyManager(
        Map<String, String> entries)
    {
        TlsClientX509ExtendedKeyManager keys = null;

        try
        {
            char[] password = "test".toCharArray();

            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, password);

            KeyStore.PasswordProtection protection = new KeyStore.PasswordProtection(password);
            CertificateFactory x509 = CertificateFactory.getInstance("X509");
            KeyFactory rsa = KeyFactory.getInstance("RSA");

            for (Map.Entry<String, String> entry : entries.entrySet())
            {
                Matcher matchEntry = KEY_ENTRY_PATTERN.matcher(entry.getValue());
                assertThat(matchEntry.matches(), equalTo(true));

                InputStream encodedChain = new ByteArrayInputStream(matchEntry.group("chain").getBytes(US_ASCII));
                Certificate[] chain = x509.generateCertificates(encodedChain).toArray(Certificate[]::new);

                String base64 = matchEntry.group("key")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("[^a-zA-Z0-9+/=]", "");
                PrivateKey key = rsa.generatePrivate(new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(base64)));

                store.setEntry(entry.getKey(), new KeyStore.PrivateKeyEntry(key, chain), protection);
            }

            KeyManagerFactory factory = KeyManagerFactory.getInstance("PKIX");
            factory.init(store, password);

            keys = new TlsClientX509ExtendedKeyManager(new TlsConfiguration(new Configuration()),
                (X509ExtendedKeyManager) factory.getKeyManagers()[0]);
        }
        catch (Exception ex)
        {
            throw new AssertionError(ex);
        }

        return keys;
    }
}
