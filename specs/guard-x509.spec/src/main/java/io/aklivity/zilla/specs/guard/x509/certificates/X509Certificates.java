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
package io.aklivity.zilla.specs.guard.x509.certificates;

public final class X509Certificates
{
    public static final String INTERNAL_CA = """
        -----BEGIN CERTIFICATE-----
        MIIDZTCCAk2gAwIBAgIURP6ynUy856EcfUN21iEMwTYf3/swDQYJKoZIhvcNAQEL
        BQAwOTELMAkGA1UEBhMCVVMxFDASBgNVBAoMC0V4YW1wbGUgSW5jMRQwEgYDVQQD
        DAtJbnRlcm5hbCBDQTAgFw0yNjA4MDcyMzAxMTRaGA8yMTI2MDcxNDIzMDExNFow
        OTELMAkGA1UEBhMCVVMxFDASBgNVBAoMC0V4YW1wbGUgSW5jMRQwEgYDVQQDDAtJ
        bnRlcm5hbCBDQTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALmzNZ4s
        17lBim6L/jGslFykk/feaL7fRaQITNirftuO5VBzRysSpzlcF60vmbL1f324yUpW
        iRqX9aZVaBuvi08Eua5ooizUmqL31znxfXNitKsxsiNISBDWSMcTfidkrLt65rFk
        DTIqDYTIICoYV9SQNI3iWtUmBHsC7J2uf1ztokcvbDcoV4adEiA3E9Nx5G8klVp6
        D+sGVIWvvneWrrsI/I9Vr7pipsLL5C4XI30usfaUcSBO2f4sDtvzSNesM2ziomMN
        u0c+Tbb+OrIZJ40zSflXDYlLkuckUMQpYPk7uSa/250XwstaLTXvn3Kdw8mxEZn7
        Vk4wo3G+J8HQvc0CAwEAAaNjMGEwHQYDVR0OBBYEFE/R1KUYSrHq0sEiF4PHpQXf
        0XRrMB8GA1UdIwQYMBaAFE/R1KUYSrHq0sEiF4PHpQXf0XRrMA8GA1UdEwEB/wQF
        MAMBAf8wDgYDVR0PAQH/BAQDAgEGMA0GCSqGSIb3DQEBCwUAA4IBAQCGj3onF6Ax
        j2zJ6+Qvwe1eFcec5NH+2O9FxeI9Q/+4kEYn0TfxN6Efq9BHtzBNsTiZU7b5hOIO
        I1EAXbsa6TCkJFs4yZs2LxEaNRMBdHmUMYs5wO0M+1VeGal+VpEurdA+aAHqnrmd
        dRXgIYBZ9Baq3N9OjsdvVCgo8o0s/kOzCDaRr/iMZSYPCv3hQ+9nZIuH11b8K5X0
        q/GIAKX5giLsMyM41LRNvtLfpaQjkcdK37dIVkvSGbD9XAk+vJ/KUoCj6j1brLQb
        8We2w4cQU5L46YhyGN15KbiLKC1j1xQoxqG1lV/4+vrZfntI/dL+c1e/MnqgQHoW
        B6tpqa25/p3q
        -----END CERTIFICATE-----
        """;

    public static final String PARTNER_CA = """
        -----BEGIN CERTIFICATE-----
        MIIDczCCAlugAwIBAgIUU49NVeqJcid6LOB9qSY8hHiKJNMwDQYJKoZIhvcNAQEL
        BQAwQDELMAkGA1UEBhMCVVMxFDASBgNVBAoMC1BhcnRuZXIgSW5jMRswGQYDVQQD
        DBJQYXJ0bmVyIElzc3VpbmcgQ0EwIBcNMjYwODA3MjMwMTE0WhgPMjEyNjA3MTQy
        MzAxMTRaMEAxCzAJBgNVBAYTAlVTMRQwEgYDVQQKDAtQYXJ0bmVyIEluYzEbMBkG
        A1UEAwwSUGFydG5lciBJc3N1aW5nIENBMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A
        MIIBCgKCAQEA9JjDk1foZSPs8dHBEAk79JLcwFGPv+9U7q+EhI9t8Cu/AA2FD/eC
        hF7+4w8XKycN9HZv/1Q3lyeAkr5abMp0zbgDRbF6QQAhdERVPNow3qTgNypz19SN
        VaOgyfCntLgzaO6D09LvxnfUJgLrDaOccNsBy/J7OiXw0zfxVL3A87EC+2f4rfal
        Rahz/wWUpZqAttLiY2KSKC9wHY9HnmbsHBlvMB7+bEC+rCFpkf5srEQULV/a4con
        DMj2BpYXghlUM2DdkacjA89+G3TfRi8dtk9UpvDh3RraKtuqMegSb5D3izbGMVMo
        ecewZ6NcIwBf3IBv5qNSCWTkarlIKEbpFQIDAQABo2MwYTAdBgNVHQ4EFgQU9Eo9
        jNhtDKS6ThaquAjgd1aVFH4wHwYDVR0jBBgwFoAU9Eo9jNhtDKS6ThaquAjgd1aV
        FH4wDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYwDQYJKoZIhvcNAQEL
        BQADggEBAH6sgEvH3BagwC2Fesw57POL8mHDckZzHK5Vfkh8FqhghrRYQmeP/qTB
        NSco06rZUV3gnazDp9f7A4tR1O826F2a9b4rdGfPfrCtzc5JuAy/Gp7Zfm7RGALX
        0OXW/qkLk26HUfRpewESEfuVUD16AvHyRZcwF3HDE+qdrNTzSpeks2hxPwXJ3w4F
        SnVEsYrdnBF97ISbRBrkq9TmWSXyLBKW6HfA/aZ69umF8qr+NsUpJoP/SK2EYbX+
        jdBA9O1PKVZEKTPF5d3PMFAev4pelXu3xtP27thihW5RwjNY/3E5zJkbDTIKJtLU
        Uy+qBDK/VeX7/iorPnvmGe9X/sMrfH4=
        -----END CERTIFICATE-----
        """;

    public static final String PLATFORM = """
        -----BEGIN CERTIFICATE-----
        MIIEMDCCAxigAwIBAgIUD8pFwjuLz3rkovbAuHTvDrgZLSswDQYJKoZIhvcNAQEL
        BQAwOTELMAkGA1UEBhMCVVMxFDASBgNVBAoMC0V4YW1wbGUgSW5jMRQwEgYDVQQD
        DAtJbnRlcm5hbCBDQTAgFw0yNjA4MDcyMzAxMTRaGA8yMTI2MDcxNDIzMDExNFow
        azELMAkGA1UEBhMCVVMxFDASBgNVBAoMC0V4YW1wbGUgSW5jMREwDwYDVQQLDAhQ
        bGF0Zm9ybTEUMBIGA1UECwwLRW5naW5lZXJpbmcxHTAbBgNVBAMMFHBsYXRmb3Jt
        LmV4YW1wbGUuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2sDW
        5seIa8RyIvxeSVNZscRkB+p7WDd0l3Q+loHHcFl/VJkELyB08OAPpg+VOTybwN6Y
        8y1vcrO9uJD6V52auO1PDh4e2fYKSdFcFA8uNPg+MsCqjR3wlGH5I5ws6c02tiKf
        L4pn3m9vIWwhwKbqd+cxj/FqIXNsg/2DAclkO1eOV4Ng8p+mw3L6VDfNJjVYA8ix
        yCfrXq45VsCNq6Hmy9OLd6i1cvYmtoyTmFqZfiLkoXL+PuzB6aM1QvpEnxFU+4iu
        DL7XW9vCB1CATqd1ZoFUsNboROJI+8yfl0Nqff1vpK/HxLxnN8SGyk+Vx6XOkKkj
        cUyDvFu0UQz8JRfwvwIDAQABo4H7MIH4MAkGA1UdEwQCMAAwDgYDVR0PAQH/BAQD
        AgWgMBMGA1UdJQQMMAoGCCsGAQUFBwMCMIGFBgNVHREEfjB8gh1wbGF0Zm9ybS5p
        bnRlcm5hbC5leGFtcGxlLmNvbYIbZXZlbnRzLmludGVybmFsLmV4YW1wbGUuY29t
        hihzcGlmZmU6Ly9leGFtcGxlLmNvbS9ucy9wcm9kL3NhL3BsYXRmb3JtgRRwbGF0
        Zm9ybUBleGFtcGxlLmNvbTAdBgNVHQ4EFgQUTdJ6ixVJAC7DfOo3gTQqiSZM828w
        HwYDVR0jBBgwFoAUT9HUpRhKserSwSIXg8elBd/RdGswDQYJKoZIhvcNAQELBQAD
        ggEBAFxumPHXwGPT5Kqu08J/Ky2QymgsDLdNtImh7uFVoPBM2tZVnLHnlu+xyXcE
        p/4XMwQACqA0A+SSh+kXaZS54XoqsB/Uz1ADsxoYQRDoD3LJuONBEDUcsdjiGNq0
        ekq4qhub3t4r61QBYtMNZwNneLqv5Z7O2IUXHnC13mnzRrMfmvGDcwTRaXo/qLCF
        pKZc2ALJ8dLXfsQsHnySV2/F3k5bBATcoxAmZmIOjauEfbEHwnlGfkFBoGztFwiV
        mjrEU9lximH3AMT0NmzkKT9CJOTLExtTSN5a1C2/yaBCROuJU26VSMMDK8s7wlfi
        CYYlj6BfhJjXtReHz0cPfdjmAYw=
        -----END CERTIFICATE-----
        """;

    public static final String PARTNER = """
        -----BEGIN CERTIFICATE-----
        MIIDzTCCArWgAwIBAgIUMS8kGwWIa20NG+OrWhwMamqmvO8wDQYJKoZIhvcNAQEL
        BQAwQDELMAkGA1UEBhMCVVMxFDASBgNVBAoMC1BhcnRuZXIgSW5jMRswGQYDVQQD
        DBJQYXJ0bmVyIElzc3VpbmcgQ0EwIBcNMjYwODA3MjMwMTE1WhgPMjEyNjA3MTQy
        MzAxMTVaMFcxCzAJBgNVBAYTAlVTMRQwEgYDVQQKDAtQYXJ0bmVyIEluYzEUMBIG
        A1UECwwLSW50ZWdyYXRpb24xHDAaBgNVBAMME3BhcnRuZXIuZXhhbXBsZS5uZXQw
        ggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDYwlSeucRbzXK99KAS7OIC
        /9Axl3m11/o2SVD0cbJRqw89/N3hM9jMlDkU93C6OKnV1SdIa5Xk8o4+LafKCYIr
        rqLxzbbdMEzIh39NDXKWy8iVC6PrGYqerlKM1SfZYrdFLqylQQ0B1B1n4hU7BtPb
        potla+IZyF/CdtBy4YDWCibv0ltGj8rW1/OS1peRkE1ZIwh2h0cGQ9v/5s2FJ1n4
        wb0gIF7gw77iJdoRAnXKdUKI4N1UNTGqIn817oqUuwbrh/Uc4KuKDPmQjlk1UIGM
        cY9v9j99fQ218AO98yp8Mtfd2C6Nyc3KrHDUUt5EEaoxC9vgo/M9CPa0/HjopkED
        AgMBAAGjgaUwgaIwCQYDVR0TBAIwADAOBgNVHQ8BAf8EBAMCBaAwEwYDVR0lBAww
        CgYIKwYBBQUHAwIwMAYDVR0RBCkwJ4ITcGFydG5lci5leGFtcGxlLm5ldIYQdXJu
        OnBhcnRuZXI6YWNtZTAdBgNVHQ4EFgQUg5daS9hwYlVGL3PQPEJy5LAxDeUwHwYD
        VR0jBBgwFoAU9Eo9jNhtDKS6ThaquAjgd1aVFH4wDQYJKoZIhvcNAQELBQADggEB
        AMP/v4y8+cQdGVZKNB1AEgPCxC74pnBwBQDMAiO2jrw/MXWGIItM6dQ+loGY7Y+a
        7eV39/5HEDVHKcncAtMA9dRLOvq4EGOcomXC6nI/SATGztzTUtL5rNus0wmMfOmL
        /0GAFCDKiB7iNAKed3xRIy2N6RrIb0D8omgtQ0Jrhq3GaSnBTSWPHJoovafcIWFG
        N2xW5TqUFScJmaOd/bKgy0IXnMRyAEzoI+mjulge+D9qNMaFAt0trun/nWQe/IyC
        MFNtmBh0jk6YumsKpRdFxk+p/4Ao4dsQQeOzMHc0Kjcxk0c9pA2yufwBIBjpsTub
        jxMx+VghrIIz+/79/NeXZkw=
        -----END CERTIFICATE-----
        """;

    public static final String PLATFORM_CHAIN = PLATFORM + INTERNAL_CA;

    public static final String PARTNER_CHAIN = PARTNER + PARTNER_CA;

    private X509Certificates()
    {
    }
}
