#!/bin/bash
#
# Copyright 2021-2021 Aklivity Inc.
#
# Aklivity licenses this file to you under the Apache License,
# version 2.0 (the "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at:
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
#


SERVER_CA_ALIAS=serverca
SERVER_CA_PASS=generated

SERVER_CERT_ALIAS=localhost
SERVER_CERT_PASS=generated
SERVER_CERT_SAN=dns:localhost

SERVER_TRUST_PASS=generated

CLIENT_CA_ALIAS=clientca
CLIENT_CA_PASS=generated

CLIENT_CERT_ALIAS=client1
CLIENT_CERT_PASS=generated

CLIENT_TRUST_PASS=generated

clean()
{
  echo "Clean files from previous run"
  rm -rf server
  rm -rf client
  rm -rf ${SERVER_CA_ALIAS}.jks ${SERVER_CA_ALIAS}.crt ${SERVER_CA_ALIAS}.p12 ${SERVER_CA_ALIAS}.key
  rm -rf ${SERVER_CERT_ALIAS}.jks ${SERVER_CERT_ALIAS}.crt ${SERVER_CERT_ALIAS}.p12 ${SERVER_CERT_ALIAS}.key ${SERVER_CERT_ALIAS}.csr
  rm -rf ${CLIENT_CA_ALIAS}.jks ${CLIENT_CA_ALIAS}.crt ${CLIENT_CA_ALIAS}.p12 ${CLIENT_CA_ALIAS}.key
  rm -rf ${CLIENT_CERT_ALIAS}.jks ${CLIENT_CERT_ALIAS}.crt ${CLIENT_CERT_ALIAS}.p12 ${CLIENT_CERT_ALIAS}.key ${CLIENT_CERT_ALIAS}.csr
  rm -rf cacerts.jks
}

function print_cert()
{
  openssl x509 -noout -text -in $1
}

function print_req()
{
  openssl req -noout -text -in $1
}

function print_key()
{
  openssl rsa -noout -text -in $1
}

create_server_signers()
{
  mkdir -p server

  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Generate ca keypair: server/signers"
  echo "------------------------------------------------------------------------------"
  keytool -genkeypair -keystore server/signers -storepass ${SERVER_CA_PASS} -keypass ${SERVER_CA_PASS} -alias ${SERVER_CA_ALIAS} -dname "C=US, ST=California, O=Aklivity, OU=Development, CN=${SERVER_CA_ALIAS}" -validity 3650 -keyalg RSA -ext bc:c

  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Export ca certificate in pem format: server/${SERVER_CA_ALIAS}.crt"
  echo "------------------------------------------------------------------------------"
  keytool -keystore server/signers -storepass ${SERVER_CA_PASS} -alias ${SERVER_CA_ALIAS} -exportcert -rfc > server/${SERVER_CA_ALIAS}.crt

  print_cert server/${SERVER_CA_ALIAS}.crt
}

create_client_signers()
{
  mkdir -p client

  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Generate ca keypair: client/signers"
  echo "------------------------------------------------------------------------------"
  keytool -genkeypair -keystore client/signers -storepass ${CLIENT_CA_PASS} -keypass ${CLIENT_CA_PASS} -alias ${CLIENT_CA_ALIAS} -dname "C=US, ST=California, O=Aklivity, OU=Development, CN=${SERVER_CA_ALIAS}" -validity 3650 -keyalg RSA -ext bc:c

  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Export ca certificate in pem format: client/${CLIENT_CA_ALIAS}.crt"
  echo "------------------------------------------------------------------------------"
  keytool -keystore client/signers -storepass ${CLIENT_CA_PASS} -alias ${CLIENT_CA_ALIAS} -exportcert -rfc > client/${CLIENT_CA_ALIAS}.crt

  print_cert client/${CLIENT_CA_ALIAS}.crt
}

create_server_keys()
{
  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Generate certificate keypair: server/keys"
  echo "------------------------------------------------------------------------------"
  keytool -genkeypair -keystore server/keys -storepass ${SERVER_CERT_PASS} -keypass ${SERVER_CERT_PASS} -alias ${SERVER_CERT_ALIAS} -dname "C=US, ST=California, O=Aklivty, OU=Development, CN=${SERVER_CERT_ALIAS}" -validity 3650 -keyalg RSA

  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Create certificate signing request: server/${SERVER_CERT_ALIAS}.csr"
  echo "------------------------------------------------------------------------------"
  keytool -keystore server/keys -storepass ${SERVER_CERT_PASS} -alias ${SERVER_CERT_ALIAS} -certreq -rfc > server/${SERVER_CERT_ALIAS}.csr

  print_req server/${SERVER_CERT_ALIAS}.csr

  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Create signed certificate: server/${SERVER_CERT_ALIAS}.crt"
  echo "------------------------------------------------------------------------------"
  keytool -keystore server/signers -storepass ${SERVER_CA_PASS} -keypass ${SERVER_CA_PASS} -gencert -alias ${SERVER_CA_ALIAS} -ext ku:c=dig,keyenc -ext SAN="${SERVER_CERT_SAN}" -rfc -validity 1800 < server/${SERVER_CERT_ALIAS}.csr > server/${SERVER_CERT_ALIAS}.crt

  print_cert server/${SERVER_CERT_ALIAS}.crt

  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Import signed certificate: server/${SERVER_CERT_ALIAS}.crt"
  echo "------------------------------------------------------------------------------"
  keytool -keystore server/keys -storepass ${SERVER_CERT_PASS} -keypass ${SERVER_CERT_PASS} -importcert -alias ${SERVER_CA_ALIAS} -rfc -noprompt < server/${SERVER_CA_ALIAS}.crt
  keytool -keystore server/keys -storepass ${SERVER_CERT_PASS} -keypass ${SERVER_CERT_PASS} -importcert -alias ${SERVER_CERT_ALIAS} -rfc < server/${SERVER_CERT_ALIAS}.crt
  keytool -keystore server/keys -storepass ${SERVER_CERT_PASS} -keypass ${SERVER_CERT_PASS} -delete -alias ${SERVER_CA_ALIAS} -noprompt
}

create_client_keys()
{
  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Generate certificate keypair: client/keys"
  echo "------------------------------------------------------------------------------"
  keytool -genkeypair -keystore client/keys -storepass ${CLIENT_CERT_PASS} -keypass ${CLIENT_CERT_PASS} -alias ${CLIENT_CERT_ALIAS} -dname "C=US, ST=California, O=Aklivty, OU=Development, CN=${SERVER_CERT_ALIAS}" -validity 3650 -keyalg RSA

  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Create certificate signing request: client/${CLIENT_CERT_ALIAS}.csr"
  echo "------------------------------------------------------------------------------"
  keytool -keystore client/keys -storepass ${CLIENT_CERT_PASS} -alias ${CLIENT_CERT_ALIAS} -certreq -rfc > client/${CLIENT_CERT_ALIAS}.csr

  print_req client/${CLIENT_CERT_ALIAS}.csr

  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Create signed certificate: client/${CLIENT_CERT_ALIAS}.crt"
  echo "------------------------------------------------------------------------------"
  keytool -keystore client/signers -storepass ${CLIENT_CA_PASS} -keypass ${CLIENT_CA_PASS} -gencert -alias ${CLIENT_CA_ALIAS} -ext ku:c=dig,keyenc -dname "CN=${CLIENT_CERT_ALIAS}" -rfc -validity 1800 < client/${CLIENT_CERT_ALIAS}.csr > client/${CLIENT_CERT_ALIAS}.crt

  print_cert client/${CLIENT_CERT_ALIAS}.crt

  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Import signed certificate: client/${CLIENT_CERT_ALIAS}.crt"
  echo "------------------------------------------------------------------------------"
  keytool -keystore client/keys -storepass ${CLIENT_CERT_PASS} -keypass ${CLIENT_CERT_PASS} -importcert -alias ${CLIENT_CA_ALIAS} -rfc -noprompt < client/${CLIENT_CA_ALIAS}.crt
  keytool -keystore client/keys -storepass ${CLIENT_CERT_PASS} -keypass ${CLIENT_CERT_PASS} -importcert -alias ${CLIENT_CERT_ALIAS} -rfc < client/${CLIENT_CERT_ALIAS}.crt
  keytool -keystore client/keys -storepass ${CLIENT_CERT_PASS} -keypass ${CLIENT_CERT_PASS} -delete -alias ${CLIENT_CA_ALIAS} -noprompt
}

create_server_trust()
{
  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Import the client ca certificate: server/trust"
  echo "------------------------------------------------------------------------------"
  keytool -keystore server/trust -storepass ${SERVER_TRUST_PASS} -keypass ${SERVER_TRUST_PASS} -importcert -alias ${CLIENT_CA_ALIAS} -rfc -noprompt < client/${CLIENT_CA_ALIAS}.crt

  print_cert client/${CLIENT_CA_ALIAS}.crt
}

create_client_trust()
{
  echo ""
  echo "------------------------------------------------------------------------------"
  echo "Import the server ca certificate: client/trust"
  echo "------------------------------------------------------------------------------"
  keytool -keystore client/trust -storepass ${CLIENT_TRUST_PASS} -keypass ${CLIENT_TRUST_PASS} -importcert -alias ${SERVER_CA_ALIAS} -rfc -noprompt < server/${SERVER_CA_ALIAS}.crt

  print_cert server/${SERVER_CA_ALIAS}.crt
}

clean
create_server_signers
create_client_signers
create_server_keys
create_client_keys
create_server_trust
create_client_trust
