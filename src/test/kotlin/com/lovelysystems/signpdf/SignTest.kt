package com.lovelysystems.signpdf

import org.junit.Test
import java.io.File

class SignTest {

    @Test
    fun testSign() {

        // The sample keystore was created using:
        // keytool -genkeypair -storepass sample -storetype pkcs12 -alias sample -validity 3650 -v -keyalg RSA -keystore samplekeystore.p12
        // Note that for some reason DSA keys wont work, so ensure that you use RSA

        val keyStore = javaClass.getResourceAsStream("/com/lovelysystems/signpdf/samplekeystore.p12")
        val signer = Sign(keyStore, "sample".toCharArray())
        val content = javaClass.getResourceAsStream("/com/lovelysystems/signpdf/simple.pdf")
        val f = File("/Users/bd/tmp/signed.pdf")
        signer.sign(content, f.outputStream())
        checkSignature(f.readBytes())
    }
}