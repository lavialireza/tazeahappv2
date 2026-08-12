package com.example.bookapp.data

import androidx.room.Dao
import androidx.room.Query

data class SearchResult(
    val sectionId: Long,
    val sectionTitle: String,
    val roleTitle: String,
    val taziehTitle: String,
    val fieldTitle: String
)

@Dao
interface SearchDao {
    @Query(
        """
        SELECT
            sections.id AS sectionId,
            sections.title AS sectionTitle,
            roles.title AS roleTitle,
            taziehs.title AS taziehTitle,
            fields.title AS fieldTitle
        FROM sections
        INNER JOIN roles ON sections.roleId = roles.id
        INNER JOIN taziehs ON roles.taziehId = taziehs.id
        INNER JOIN fields ON taziehs.fieldId = fields.id
        WHERE sections.title LIKE '%' || :query || '%'
           OR sections.content LIKE '%' || :query || '%'
           OR roles.title LIKE '%' || :query || '%'
           OR taziehs.title LIKE '%' || :query || '%'
        ORDER BY fields.title, taziehs.title, roles.title
        LIMIT 100
        """
    )
    suspend fun search(query: String): List<SearchResult>

    @Query(
        """
        SELECT
            sections.id AS sectionId,
            sections.title AS sectionTitle,
            roles.title AS roleTitle,
            taziehs.title AS taziehTitle,
            fields.title AS fieldTitle
        FROM sections
        INNER JOIN roles ON sections.roleId = roles.id
        INNER JOIN taziehs ON roles.taziehId = taziehs.id
        INNER JOIN fields ON taziehs.fieldId = fields.id
        WHERE fields.id = :fieldId
          AND (sections.title LIKE '%' || :query || '%'
           OR sections.content LIKE '%' || :query || '%'
           OR roles.title LIKE '%' || :query || '%'
           OR taziehs.title LIKE '%' || :query || '%')
        ORDER BY taziehs.title, roles.title
        LIMIT 100
        """
    )
    suspend fun searchInField(query: String, fieldId: Long): List<SearchResult>

    @Query(
        """
        SELECT
            sections.id AS sectionId,
            sections.title AS sectionTitle,
            roles.title AS roleTitle,
            taziehs.title AS taziehTitle,
            fields.title AS fieldTitle
        FROM sections
        INNER JOIN roles ON sections.roleId = roles.id
        INNER JOIN taziehs ON roles.taziehId = taziehs.id
        INNER JOIN fields ON taziehs.fieldId = fields.id
        WHERE taziehs.id = :taziehId
          AND (sections.title LIKE '%' || :query || '%'
           OR sections.content LIKE '%' || :query || '%'
           OR roles.title LIKE '%' || :query || '%')
        ORDER BY roles.title
        LIMIT 100
        """
    )
    suspend fun searchInTazieh(query: String, taziehId: Long): List<SearchResult>

    @Query(
        """
        SELECT
            sections.id AS sectionId,
            sections.title AS sectionTitle,
            roles.title AS roleTitle,
            taziehs.title AS taziehTitle,
            fields.title AS fieldTitle
        FROM sections
        INNER JOIN roles ON sections.roleId = roles.id
        INNER JOIN taziehs ON roles.taziehId = taziehs.id
        INNER JOIN fields ON taziehs.fieldId = fields.id
        WHERE sections.id IN (:ids)
        """
    )
    suspend fun getByIds(ids: List<Long>): List<SearchResult>

    @Query(
        """
        SELECT
            sections.id AS sectionId,
            sections.title AS sectionTitle,
            roles.title AS roleTitle,
            taziehs.title AS taziehTitle,
            fields.title AS fieldTitle
        FROM sections
        INNER JOIN roles ON sections.roleId = roles.id
        INNER JOIN taziehs ON roles.taziehId = taziehs.id
        INNER JOIN fields ON taziehs.fieldId = fields.id
        ORDER BY RANDOM()
        LIMIT 1
        """
    )
    suspend fun getRandomSection(): SearchResult?

    @Query("SELECT COUNT(*) FROM fields")
    suspend fun countFields(): Int

    @Query("SELECT COUNT(*) FROM taziehs")
    suspend fun countTaziehs(): Int

    @Query("SELECT COUNT(*) FROM roles")
    suspend fun countRoles(): Int

    @Query("SELECT COUNT(*) FROM sections")
    suspend fun countSections(): Int
}
