package com.swpp.team5.frameless.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NewsApiClientTest {
    private lateinit var server: HttpServer
    private lateinit var client: NewsApiClient
    private val responses = ConcurrentLinkedQueue<Response>()
    private val requests = mutableListOf<CapturedRequest>()

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            synchronized(requests) {
                requests += CapturedRequest(
                    exchange.requestMethod,
                    exchange.requestURI.toString(),
                    exchange.requestHeaders.getFirst("Authorization"),
                    exchange.requestBody.bufferedReader().use { it.readText() }
                )
            }
            val response = responses.remove()
            respond(exchange, response)
        }
        server.start()
        client = NewsApiClient("http://127.0.0.1:${server.address.port}/")
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun authenticationRequestsParseSessionsAndUserMetadata() = runBlocking {
        responses += Response(201, sessionJson())
        val registered = client.register("reader@example.com", "reader", "password", "password")
        assertEquals("reader", registered.username)
        assertEquals("reader@example.com", registered.user.email)
        assertFalse(registered.user.isDeveloper)
        assertEquals("POST", requests[0].method)
        assertEquals("/api/v1/auth/register", requests[0].path)
        assertTrue(requests[0].body.contains("password_confirmation"))

        responses += Response(200, sessionJson(developer = true))
        val loggedIn = client.login("reader", "password")
        assertTrue(loggedIn.user.isDeveloper)
        assertTrue(requests[1].body.contains("identifier"))

        responses += Response(200, """{"user":${userJson(email = null)}}""")
        val user = client.me("secret-token")
        assertNull(user.email)
        assertEquals("Bearer secret-token", requests[2].authorization)

        responses += Response(204, "")
        client.logout("secret-token")
        assertEquals("POST", requests[3].method)
    }

    @Test
    fun newsEndpointsParseFeedDetailAndGroundedComparison() = runBlocking {
        responses += Response(200, """{"items":[
          {"code":"yna","name":"연합뉴스","logo_url":"https://img.example/yna.png"},
          {"code":"mbc","name":"MBC","logo_url":null}
        ]}""")
        val publishers = client.publishers("token")
        assertEquals(listOf("yna", "mbc"), publishers.map { it.code })
        assertNull(publishers[1].logoUrl)

        responses += Response(200, """{
          "items":[{"id":"a1","publisher":{"code":"yna","name":"연합뉴스","logo_url":null},
            "title":"정책 추진","category":"POLITICS","published_at":"2026-10-31T12:00:00+09:00",
            "analysis_state":"READY"}],"next_cursor":"cursor-2"}
        """)
        val page = client.articles("token", NewsCategory.POLITICS, setOf("mbc", "yna"), "cursor 1", 10)
        assertEquals("a1", page.items.single().id)
        assertEquals("cursor-2", page.nextCursor)
        assertTrue(requests[1].path.contains("publisher=mbc&publisher=yna"))
        assertTrue(requests[1].path.contains("cursor=cursor+1"))

        responses += Response(200, articleJson())
        val article = client.article("token", "article/id")
        assertEquals("정책 본문", article.body)
        assertTrue(requests[2].path.contains("article%2Fid"))

        responses += Response(200, analysisJson())
        val analysis = client.analysis("token", "a1")
        val finding = analysis.findings.single()
        assertEquals(listOf("C", "D"), finding.categories)
        assertEquals("전격", finding.marker.text)
        assertEquals("빠르게", finding.comparisons.single().evidence.single().marker.text)
        assertEquals("기자", finding.comparisons.single().evidence.single().targetSpeakerLabel)

        responses += Response(200, """{"items":[{
          "id":"a3","publisher":{"code":"yna","name":"연합뉴스"},"title":"제목",
          "category":"UNKNOWN","published_at":"2026-10-31T12:00:00+09:00",
          "analysis_state":"UNKNOWN"}],"next_cursor":null}""")
        val conservative = client.articles("token", NewsCategory.SOCIETY, emptySet())
        assertNull(conservative.nextCursor)
        assertEquals(NewsCategory.POLITICS, conservative.items.single().category)
        assertEquals(StoredAnalysisState.NOT_STARTED, conservative.items.single().analysisState)

        responses += Response(200, """{"state":"READY","version":"v1"}""")
        assertTrue(client.analysis("token", "a3").findings.isEmpty())
    }

    @Test
    fun structuredErrorsInvalidResponsesAndNetworkFailuresAreSafe() {
        responses += Response(409, """{"error":{"code":"VALIDATION_ERROR",
          "message":"입력 내용을 확인해 주세요.","fields":{"email":"이미 사용 중입니다."}}}""")
        val structured = assertThrows(NewsApiException::class.java) {
            runBlocking { client.register("reader@example.com", "reader", "password", "password") }
        }
        assertEquals("VALIDATION_ERROR", structured.code)
        assertEquals("이미 사용 중입니다.", structured.fields["email"])

        responses += Response(502, "not-json")
        val invalid = assertThrows(NewsApiException::class.java) {
            runBlocking { client.publishers("token") }
        }
        assertEquals("INVALID_RESPONSE", invalid.code)

        server.stop(0)
        val network = assertThrows(NewsApiException::class.java) {
            runBlocking { client.publishers("token") }
        }
        assertEquals("NETWORK_ERROR", network.code)
    }

    private fun respond(exchange: HttpExchange, response: Response) {
        val bytes = response.body.toByteArray(Charsets.UTF_8)
        if (response.status == 204) {
            exchange.sendResponseHeaders(204, -1)
        } else {
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(response.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        exchange.close()
    }

    private fun userJson(email: String? = "reader@example.com", developer: Boolean = false) =
        """{"id":"user-1","email":${email?.let { "\"$it\"" } ?: "null"},
          "username":"reader","is_developer":$developer}"""

    private fun sessionJson(developer: Boolean = false) =
        """{"user":${userJson(developer = developer)},"token":"secret-token",
          "expires_at":"2026-11-30T00:00:00+09:00"}"""

    private fun articleJson() = """{
      "id":"a1","publisher":{"code":"yna","name":"연합뉴스","logo_url":null},
      "title":"정책 추진","body":"정책 본문","category":"POLITICS",
      "published_at":"2026-10-31T12:00:00+09:00","original_url":"https://example.com/a1",
      "analysis_state":"READY"}"""

    private fun analysisJson() = """{
      "state":"READY","version":"reviewed-v1","findings":[{
        "id":"f1","categories":["C","D"],"explanation":"속도를 강조한다.",
        "source":{"scope":"BODY","sentence":{"start":0,"end":16,"text":"정책을 전격 추진한다고 밝혔다."},
          "marker":{"start_in_sentence":4,"end_in_sentence":6,"text":"전격"}},
        "comparison_articles":[{"article_id":"a2",
          "publisher":{"code":"mbc","name":"MBC","logo_url":null},
          "title":"정책 추진 계획","published_at":"2026-10-31T11:50:00+09:00",
          "original_url":"https://example.com/a2","body":"정책을 빠르게 추진할 계획이다.",
          "evidence":[{"scope":"BODY","sentence":{"start":0,"end":18,"text":"정책을 빠르게 추진할 계획이다."},
            "marker":{"start_in_sentence":4,"end_in_sentence":7,"text":"빠르게"},
            "target_owner":"REPORTER","target_speaker_label":"기자",
            "comparison_summary":"속도 표현이 다르다."}]}]}]}"""

    private data class Response(val status: Int, val body: String)
    private data class CapturedRequest(
        val method: String, val path: String, val authorization: String?, val body: String
    )
}
