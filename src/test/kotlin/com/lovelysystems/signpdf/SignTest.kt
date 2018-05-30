package com.lovelysystems.signpdf

import org.amshove.kluent.shouldEqual
import org.junit.Test
import java.io.ByteArrayOutputStream

class SignTest {

    @Test
    fun testSign() {

        // The sample keystore was created using:
        // keytool -genkeypair -storepass sample -storetype pkcs12 -alias sample -validity 3650 -v -keyalg RSA -keystore samplekeystore.p12
        // Note that for some reason DSA keys wont work, so ensure that you use RSA

        val keyStore = javaClass.getResourceAsStream("/com/lovelysystems/signpdf/samplekeystore.p12")
        val signer = Sign(keyStore, "sample".toCharArray())
        val content = javaClass.getResourceAsStream("/com/lovelysystems/signpdf/simple.pdf")
        val output = ByteArrayOutputStream()
        signer.sign(
            content, output,
            name = "Max Mustermann",
            location = "Dornbirn",
            reason = "Just testing",
            contactInfo = "Around the corner"
        )
        val sig = validateAndGetFirstSignature(output.toByteArray())

        sig.name shouldEqual "Max Mustermann"
        sig.location shouldEqual "Dornbirn"
        sig.reason shouldEqual "Just testing"
        sig.contactInfo shouldEqual "Around the corner"
    }
}