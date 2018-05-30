# Lovely Sign PDF

[![Build Status](https://travis-ci.org/lovelysystems/lovely-signpdf.svg?branch=master)](https://travis-ci.org/lovelysystems/lovely-signpdf)
[![GitHub License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)

A small HTTP server which allows to sign PDF Files via a HTTP API. This software is
currently in alpha state and might change in incompatible ways.

## Usage

Take a look at the example [curl command](./docker/sign.sh) on how to sign a document. Also the
[App test](./src/test/kotlin/com/lovelysystems/signpdf/AppKtTest.kt) might be interesting.

## Installation

The server is intended to be run as a docker container, see the [Docker Readme](./docker/README.md)
for details.
