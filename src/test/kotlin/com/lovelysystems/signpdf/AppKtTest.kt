package com.lovelysystems.signpdf

import io.ktor.config.MapApplicationConfig
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.PartData
import io.ktor.http.headersOf
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.ktor.utils.io.streams.asInput
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
    fun testInvalidCall() = testApp {
        sign(
            listOf(
                formItem("invalid", "John Doe"),
                formItem("location", "Dornbirn"),
                fileItem("myfile", "/com/lovelysystems/signpdf/simple.pdf", "simple.pdf")
            )
        ).apply {
            response.status()!!.value shouldEqual 400
            response.content shouldEqual """
                    Unknown form field invalid
                    Unknown file field myfile
                    file field is not defined""".trimIndent()
        }
    }

    @Test
    fun testSign() = testApp {
        sign(
            listOf(
                fileItem("file", "/com/lovelysystems/signpdf/bug.pdf", "simple.pdf"),
                formItem("name", "John Doe"),
                formItem("location", "Dornbirn"),
                formItem("reason", "My Stuff"),
                formItem("contactInfo", "Hintere Achmühlerstraße 1a")
            )
        ).apply {
            response.status()?.value shouldEqual 200
            response.contentType().toString() shouldEqual "application/pdf"
            val sig = validateAndGetFirstSignature(response.byteContent!!)
            sig.name shouldEqual "John Doe"
            sig.location shouldEqual "Dornbirn"
            sig.reason shouldEqual "My Stuff"
            sig.contactInfo shouldEqual "Hintere Achmühlerstraße 1a"
        }
    }

    private fun testApp(callback: TestApplicationEngine.() -> Unit) {
        withTestApplication({
            (environment.config as MapApplicationConfig).apply {
                put("signer.type", "seflsigned")
                put("signer.keyStorePath", samplePath("samplekeystore.p12"))
                put("signer.keyStorePassPath", samplePath("samplekeystorepass.txt"))
            }
            main()
        }, callback)
    }

    private fun samplePath(name: String): String {
        return javaClass.getResource("/com/lovelysystems/signpdf/$name").path!!
    }
}

fun TestApplicationEngine.sign(formItems: List<PartData>, setup: TestApplicationRequest.() -> Unit = {})
        : TestApplicationCall = handleRequest(HttpMethod.Post, "/sign") {
    val boundary = "***bbb***"
    addHeader(
        HttpHeaders.ContentType,
        ContentType.MultiPart.FormData.withParameter("boundary", boundary).toString()
    )
    setBody(boundary, formItems)
    setup(this)
}

fun formItem(name: String, value: String): PartData.FormItem {
    return PartData.FormItem(
        value, { }, headersOf(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Inline
                .withParameter(ContentDisposition.Parameters.Name, name)
                .toString()
        )
    )
}

fun TestApplicationEngine.fileItem(name: String, filePath: String, fileName: String) =
    PartData.FileItem(
        { javaClass.getResourceAsStream(filePath).asInput() },
        {},
        headersOf(
            HttpHeaders.ContentDisposition,
            ContentDisposition.File
                .withParameter(ContentDisposition.Parameters.Name, name)
                .withParameter(ContentDisposition.Parameters.FileName, fileName)
                .toString()
        )
    )
