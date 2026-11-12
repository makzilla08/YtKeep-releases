package com.chue.ytkeep

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentParserTest {
    @Test
    fun parsesCommentsAndContinuationFromTheCommentsPanel() {
        val response = JSONObject(
            """
            {
              "engagementPanels": [{
                "engagementPanelSectionListRenderer": {
                  "targetId": "engagement-panel-comments-section",
                  "content": {
                    "sectionListRenderer": {
                      "contents": [{
                        "itemSectionRenderer": {
                          "contents": [
                            {
                              "commentThreadRenderer": {
                                "comment": {
                                  "commentRenderer": {
                                    "commentId": "comment-1",
                                    "authorText": {"simpleText": "Ada"},
                                    "contentText": {"runs": [{"text": "Great "}, {"text": "clip"}]},
                                    "publishedTimeText": {"simpleText": "1 day ago"},
                                    "voteCount": {"simpleText": "1.2K"}
                                  }
                                }
                              }
                            },
                            {
                              "continuationItemRenderer": {
                                "continuationEndpoint": {
                                  "continuationCommand": {"token": "next-page"}
                                }
                              }
                            }
                          ]
                        }
                      }]
                    }
                  }
                }
              }]
            }
            """.trimIndent()
        )

        val result = CommentParser.parse(response) as CommentsResult.Page

        assertEquals(1, result.value.comments.size)
        assertEquals("Ada", result.value.comments.single().author)
        assertEquals("Great clip", result.value.comments.single().text)
        assertEquals(1_200L, result.value.comments.single().likeCount)
        assertEquals("next-page", result.value.next?.token)
    }

    @Test
    fun distinguishesEmptyDisabledAndUnrecognizedResponses() {
        val empty = JSONObject(
            """{"engagementPanels":[{"engagementPanelSectionListRenderer":{"targetId":"engagement-panel-comments-section","content":{"contents":[]}}}]}"""
        )
        val disabled = JSONObject(
            """{"engagementPanels":[{"engagementPanelSectionListRenderer":{"targetId":"engagement-panel-comments-section","content":{"messageRenderer":{"text":{"simpleText":"Comments are turned off"}}}}}]}"""
        )
        val unknown = JSONObject("""{"contents":[]}""")

        assertTrue((CommentParser.parse(empty) as CommentsResult.Page).value.comments.isEmpty())
        assertEquals(CommentsResult.Disabled, CommentParser.parse(disabled))
        assertEquals(CommentsResult.Unavailable, CommentParser.parse(unknown))
    }
}
