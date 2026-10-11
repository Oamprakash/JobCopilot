package com.applycopilot.resume

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaudeResumeParserTest {
    private val resume = """
        Oamprakash R
        Experience
        Lead Android Developer at Movate   Jul 2020 - Present
        • Lead a team of 20+ Android engineers
        Skills: Kotlin, Java
    """.trimIndent()

    private val toolResponse = """
        {
          "id": "msg_1", "type": "message", "role": "assistant", "model": "test",
          "stop_reason": "tool_use",
          "content": [{
            "type": "tool_use", "id": "toolu_1", "name": "save_profile",
            "input": {
              "fullName": "Oamprakash R",
              "headline": "Lead Android Developer",
              "experience": [{
                "title": "Lead Android Developer", "company": "Movate", "startDate": "2020-07", "current": true,
                "bullets": ["Lead a team of 20+ Android engineers"]
              }],
              "skills": ["Kotlin", "Java", "Kubernetes"],
              "unexpectedField": 1
            }
          }]
        }
    """.trimIndent()

    @Test
    fun sendsForcedToolCallAndParsesResult() = runBlocking {
        var sentBody = ""
        var headers = io.ktor.http.Headers.Empty
        val engine = MockEngine { request ->
            headers = request.headers
            sentBody = (request.body as TextContent).text
            respond(toolResponse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val parser = ClaudeResumeParser(HttpClient(engine), apiKey = "test-key", model = "claude-test")
        val profile = parser.parse(resume)

        assertEquals("test-key", headers["x-api-key"])
        assertEquals("2023-06-01", headers["anthropic-version"])
        val body = Json.parseToJsonElement(sentBody).jsonObject
        assertEquals("claude-test", body["model"]!!.jsonPrimitive.content)
        assertEquals("save_profile", body["tool_choice"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertTrue(sentBody.contains("<resume>"))

        assertEquals("Oamprakash R", profile.fullName)
        assertEquals("Movate", profile.experience.single().company)
        assertTrue(profile.experience.single().current)
    }

    @Test
    fun serviceGroundsClaudeOutput() = runBlocking {
        val engine = MockEngine { respond(toolResponse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val service = ResumeParsingService(ClaudeResumeParser(HttpClient(engine), "k", "m"))
        val outcome = service.parse(resume)
        assertEquals("claude", outcome.parser)
        assertFalse("Kubernetes" in outcome.profile.skills, "invented skill must be removed")
        assertTrue(outcome.removed.any { it.contains("Kubernetes") })
    }

    @Test
    fun serviceFallsBackToHeuristicWhenApiFails() = runBlocking {
        val engine = MockEngine { respond("""{"type":"error"}""", HttpStatusCode.InternalServerError) }
        val service = ResumeParsingService(ClaudeResumeParser(HttpClient(engine), "k", "m"))
        val outcome = service.parse(resume)
        assertEquals("heuristic", outcome.parser)
        assertEquals("Oamprakash R", outcome.profile.fullName)
        assertEquals("Movate", outcome.profile.experience.single().company)
    }
}
