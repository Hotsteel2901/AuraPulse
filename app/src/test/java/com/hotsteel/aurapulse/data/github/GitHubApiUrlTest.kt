package com.hotsteel.aurapulse.data.github

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the query-string path that broke the first Gist backup.
 *
 * Passing `gists?per_page=100` to `addPathSegments` encodes the question mark
 * into `%3F`, so GitHub answered 404 before the request could even list gists.
 */
class GitHubApiUrlTest {

  @Test
  fun `query arguments are encoded as a query string`() {
    assertEquals(
      "https://api.github.com/gists?per_page=100",
      githubApiUrl("gists", mapOf("per_page" to "100")).toString(),
    )
  }

  @Test
  fun `path segments are still appended normally`() {
    assertEquals(
      "https://api.github.com/gists/abc123",
      githubApiUrl("gists/abc123").toString(),
    )
    assertEquals(
      "https://api.github.com/user",
      githubApiUrl("/user").toString(),
    )
  }
}
