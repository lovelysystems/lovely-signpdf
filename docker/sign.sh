#!/usr/bin/env bash
declare -r PRJ=$(cd "$(dirname "$0")/.." && pwd)

# sends a file to the server for signing and safes it as singed.pdf
curl -i -X POST -F file=@$PRJ/src/test/resources/com/lovelysystems/signpdf/simple.pdf http://localhost:18080/sign -o signed.pdf
