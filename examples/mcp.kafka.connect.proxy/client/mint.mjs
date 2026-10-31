import { readFileSync } from "node:fs";
import { createPrivateKey } from "node:crypto";
import { SignJWT } from "jose";

const scope = process.argv[2] ?? "";
const key = createPrivateKey(readFileSync(process.env.PRIVATE_KEY ?? "/private.pem"));

const token = await new SignJWT({ scope })
    .setProtectedHeader({ alg: "RS256", kid: "example", typ: "JWT" })
    .setIssuer("https://auth.example.com")
    .setAudience("https://api.example.com")
    .setExpirationTime("1d")
    .sign(key);

console.log(token);
