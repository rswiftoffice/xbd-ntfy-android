package io.heckel.ntfy.msg

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.heckel.ntfy.db.CustomHeader
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.db.User
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Credentials
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiServiceVerifyAccountTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService

    private val user = User(
        baseUrl = "placeholder",
        username = "alice",
        password = "secret"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("MainPreferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        Repository::class.java.getDeclaredField("instance").apply {
            isAccessible = true
            set(null, null)
        }
        server = MockWebServer().apply { start() }
        apiService = ApiService(context)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    @Test
    fun `verifyAccount returns true on HTTP 200`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).build())

        val result = apiService.verifyAccount(baseUrl(), user)

        assertTrue("expected verifyAccount to return true on 200", result)
        val recorded = server.takeRequest()
        assertEquals("/v1/account", recorded.target)
        assertEquals(
            Credentials.basic(user.username, user.password),
            recorded.headers["Authorization"]
        )
    }

    @Test
    fun `verifyAccount returns false on HTTP 401`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).build())

        val result = apiService.verifyAccount(baseUrl(), user)

        assertFalse("expected verifyAccount to return false on 401", result)
    }

    @Test
    fun `verifyAccount returns false on HTTP 403`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(403).build())

        val result = apiService.verifyAccount(baseUrl(), user)

        assertFalse("expected verifyAccount to return false on 403", result)
    }

    @Test(expected = Exception::class)
    fun `verifyAccount throws on HTTP 500`() {
        server.enqueue(MockResponse.Builder().code(500).build())

        runBlocking { apiService.verifyAccount(baseUrl(), user) }
    }

    @Test
    fun `verifyAccount forwards configured custom headers`() = runBlocking {
        // Arrange: seed a custom header for the target base URL.
        val repository = Repository.getInstance(context)
        val headerName = "X-Tenant"
        val headerValue = "alpha"
        repository.addCustomHeader(CustomHeader(baseUrl = baseUrl(), name = headerName, value = headerValue))
        server.enqueue(MockResponse.Builder().code(200).build())

        // Act
        apiService.verifyAccount(baseUrl(), user)

        // Assert: the outgoing request carried the custom header.
        val recorded = server.takeRequest()
        assertEquals(headerValue, recorded.headers[headerName])
    }
}
