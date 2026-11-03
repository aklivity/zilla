import { createPrivateKey } from "node:crypto";
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { SignJWT } from "jose";

const PRIVATE_KEY = process.env.PRIVATE_KEY ?? "/private.pem";

export const mintJwt = async (scope) =>
    new SignJWT({ scope })
        .setProtectedHeader({ alg: "RS256", kid: "example", typ: "JWT" })
        .setIssuer("https://auth.example.com")
        .setAudience("https://api.example.com")
        .setExpirationTime("1d")
        .sign(createPrivateKey(readFileSync(PRIVATE_KEY)));

if (import.meta.url === pathToFileURL(process.argv[1]).href)
{
    console.log(await mintJwt(process.argv[2] ?? ""));
}
