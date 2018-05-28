package com.lovelysystems.signpdf

import io.ktor.config.MapApplicationConfig
import io.ktor.content.PartData
import io.ktor.http.*
import io.ktor.server.testing.*
import org.amshove.kluent.shouldEqual
import org.junit.Test

class AppKtTest {

    @Test
    fun testRequireMultipart() = testApp {
        with(handleRequest(HttpMethod.Post, "/sign")) {
            response.status()!!.value shouldEqual 400
            response.status()!!.description shouldEqual "Not a multipart request"
        }
    }

    @Test
    fun testSign() = testApp {
        handleRequest(HttpMethod.Post, "/sign") {
            val boundary = "***bbb***"

            addHeader(
                HttpHeaders.ContentType,
                ContentType.MultiPart.FormData.withParameter("boundary", boundary).toString()
            )
            setBody(boundary, listOf(
                PartData.FileItem({ javaClass.getResourceAsStream("/com/lovelysystems/signpdf/simple.pdf") }, {}, headersOf(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.File
                        .withParameter(ContentDisposition.Parameters.Name, "file")
                        .withParameter(ContentDisposition.Parameters.FileName, "file.txt")
                        .toString()
                )
                )
            )
            )
        }.apply {
            response.status()?.value shouldEqual 200
            response.contentType().toString() shouldEqual "application/pdf"
            checkSignature(response.byteContent!!)
        }
    }

    private fun testApp(callback: TestApplicationEngine.() -> Unit) {
        withTestApplication({
            (environment.config as MapApplicationConfig).apply {
                put("signer.keyStorePath", javaClass.getResource("/com/lovelysystems/signpdf/samplekeystore.p12").path)
                put("signer.keyStorePass", "sample")
            }
            main()
        }, callback)
    }
}

