# Keystore and Truststore Setup Guide

This guide provides step-by-step instructions for creating `keystore.jks` and `truststore.jks` files. Follow the commands carefully to ensure proper setup.

## 1. Create a CA certificate and a private key:

```sh
openssl genrsa -des3 -out CA-key.pem 2048
openssl req -new -key CA-key.pem -x509 -days 3600 -out CA-cert.pem
```
## 2. Create a keystore with a private key entry that is signed by the CA:

Note: your name must be "localhost"
```sh
keytool -genkeypair -alias policy_agent -keyalg RSA -keysize 2048 -keystore keystore.jks -validity 3650 -storepass policy_agent
keytool -certreq -alias policy_agent -file request.csr -keystore keystore.jks -ext san=dns:your.domain.com -storepass policy_agent
openssl x509 -req -days 3650 -in request.csr -CA CA-cert.pem -CAkey CA-key.pem -CAcreateserial -out ca_signed-cert.pem
keytool -importcert -alias ca_cert -file CA-cert.pem -keystore keystore.jks -trustcacerts -storepass policy_agent
keytool -importcert -alias policy_agent -file ca_signed-cert.pem -keystore keystore.jks -trustcacerts -storepass policy_agent
```

## 3. Create a trust store containing the CA cert (to trust all certs signed by the CA):
```sh
keytool -genkeypair -alias not_used -keyalg RSA -keysize 2048 -keystore truststore.jks -validity 3650 -storepass policy_agent
keytool -importcert -alias ca_cert -file CA-cert.pem -keystore truststore.jks -trustcacerts -storepass policy_agent
```

## 4. Command for listing of the contents of jks files, examples:
```sh
keytool -list -v -keystore keystore.jks -storepass policy_agent
keytool -list -v -keystore truststore.jks -storepass policy_agent
```

## License

Copyright (C) 2022 Nordix Foundation. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
