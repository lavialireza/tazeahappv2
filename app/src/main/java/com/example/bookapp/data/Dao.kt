package com.example.bookapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FieldDao {
    @Query("SELECT * FROM fields ORDER BY id")
    suspend fun getAll(): List<FieldEntity>

    @Insert
    suspend fun insert(field: FieldEntity): Long

    @Query("DELETE FROM fields")
    suspend fun deleteAll()
}

@Dao
interface TaziehDao {
    @Query("SELECT * FROM taziehs WHERE fieldId = :fieldId ORDER BY id")
    suspend fun getByField(fieldId: Long): List<TaziehEntity>

    @Query("SELECT * FROM taziehs ORDER BY fieldId, id")
    suspend fun getAll(): List<TaziehEntity>

    @Insert
    suspend fun insert(tazieh: TaziehEntity): Long

    @Query("DELETE FROM taziehs")
    suspend fun deleteAll()
}

@Dao
interface RoleDao {
    @Query("SELECT * FROM roles WHERE taziehId = :taziehId ORDER BY id")
    suspend fun getByTazieh(taziehId: Long): List<RoleEntity>

    @Query("SELECT * FROM roles WHERE id = :roleId")
    suspend fun getById(roleId: Long): RoleEntity

    @Insert
    suspend fun insert(role: RoleEntity): Long

    @Query("DELETE FROM roles")
    suspend fun deleteAll()
}

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections WHERE roleId = :roleId ORDER BY orderIndex")
    suspend fun getByRole(roleId: Long): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE id = :sectionId")
    suspend fun getById(sectionId: Long): SectionEntity

    @Insert
    suspend fun insert(section: SectionEntity): Long

    @Query("DELETE FROM sections")
    suspend fun deleteAll()
}
