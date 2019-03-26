# Lovely Sign PDF

[![Build Status](https://travis-ci.org/lovelysystems/lovely-signpdf.svg?branch=master)](https://travis-ci.org/lovelysystems/lovely-signpdf)
[![GitHub License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)

A small HTTP server which allows to sign PDF Files via a HTTP API. This software is
currently in alpha state and might change in incompatible ways.

## Signer

The server can be run with different kind of signers which can be chosen via the environment variable
SIGNER_TYPE. At the moment there is a swisscom and a selfsigned type.

### SwisscomSinger

To use the swisscom signer the SIGNER_TYPE environment variable must be set to `swisscom`.
Additionally the claimIdentity musst be set via the SIGNER_CLAIM_IDENTITY environment variable.
It's also possible to set a different URL for the ais server and specify a timeout. Please take a 
look at [application.conf](./src/main/resources/application.conf) for further information.

The keystore for the swisscom signer has to be in jks format and has to hold the ais-CA certificate using the alias 
ais_server and your public certificate and private key with the alias ais_client. jks format is needed because it's not 
possible to save public certificates without the corresponding private key.

### SelfSignedSigner

This is the default signer it's keystore has to be in pkcs12 format and has to hold your public certifiacte
and private key.

## Usage

Take a look at the example [curl command](./docker/sign.sh) on how to sign a document. Also the
[App test](./src/test/kotlin/com/lovelysystems/signpdf/AppKtTest.kt) might be interesting.

## Installation

The server is intended to be run as a docker container, see the [Docker Readme](./docker/README.md)
for details.
