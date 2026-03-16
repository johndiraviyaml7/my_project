# Certificate Files

Place your SSL/TLS certificate files here before building.

## Required files:

### For EdgeConnector and EdgeConnectViewer (client-side):
- `client.p12`      - Client certificate + private key (PKCS12 format)
- `truststore.jks`  - Java KeyStore containing CA certificate

### For PACSConnector (server-side):
- `server.p12`      - Server certificate + private key (PKCS12 format)  
- `truststore.jks`  - Java KeyStore containing CA certificate (for verifying client certs)

## Generating test certificates:
See the main README.md for OpenSSL commands to generate self-signed certs.

## Default passwords:
The default keystore/truststore password in application.properties is: changeit
Change this in production!
