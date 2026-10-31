import { readFileSync } from "node:fs";

import { SignJWT, importPKCS8 } from "jose";

const scope = process.argv[2] ?? "";
const key = await importPKCS8(readFileSync(process.env.PRIVATE_KEY ?? "/private.pem", "utf8"), "RS256");

const token = await new SignJWT(scope ? { scope } : {})
    .setProtectedHeader({ alg: "RS256", kid: "example" })
    .setIssuer("https://auth.example.com")
    .setAudience("https://api.example.com")
    .setExpirationTime("1d")
    .sign(key);

console.log(token);
