package com.example.usermanagement

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

/**
 * 用户数据库帮助类 - 使用 SQLite 进行本地用户管理
 */
class UserDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "user_management.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_USERS = "users"
        private const val COL_ID = "id"
        private const val COL_USERNAME = "username"
        private const val COL_PASSWORD = "password"
        private const val COL_DISPLAY_NAME = "display_name"
        private const val COL_EMAIL = "email"
        private const val COL_PHONE = "phone"
        private const val COL_ROLE = "role"
        private const val COL_IS_ACTIVE = "is_active"
        private const val COL_CREATED_AT = "created_at"

        /**
         * 对密码进行 SHA-256 哈希
         */
        fun hashPassword(password: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_USERS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_USERNAME TEXT NOT NULL UNIQUE,
                $COL_PASSWORD TEXT NOT NULL,
                $COL_DISPLAY_NAME TEXT NOT NULL,
                $COL_EMAIL TEXT DEFAULT '',
                $COL_PHONE TEXT DEFAULT '',
                $COL_ROLE TEXT DEFAULT '普通用户',
                $COL_IS_ACTIVE INTEGER DEFAULT 1,
                $COL_CREATED_AT INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTable)

        // 插入默认管理员账户（密码已哈希存储）
        val adminValues = ContentValues().apply {
            put(COL_USERNAME, "admin")
            put(COL_PASSWORD, hashPassword("admin123"))
            put(COL_DISPLAY_NAME, "系统管理员")
            put(COL_EMAIL, "admin@example.com")
            put(COL_PHONE, "13800000000")
            put(COL_ROLE, "管理员")
            put(COL_IS_ACTIVE, 1)
            put(COL_CREATED_AT, System.currentTimeMillis())
        }
        db.insert(TABLE_USERS, null, adminValues)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    /**
     * 验证登录（对输入密码哈希后与数据库比较）
     */
    fun validateLogin(username: String, password: String): User? {
        val db = readableDatabase
        val hashedPassword = hashPassword(password)
        val cursor = db.query(
            TABLE_USERS,
            null,
            "$COL_USERNAME = ? AND $COL_PASSWORD = ? AND $COL_IS_ACTIVE = 1",
            arrayOf(username, hashedPassword),
            null, null, null
        )
        var user: User? = null
        if (cursor.moveToFirst()) {
            user = cursorToUser(cursor)
        }
        cursor.close()
        return user
    }

    /**
     * 获取所有用户
     */
    fun getAllUsers(): List<User> {
        val users = mutableListOf<User>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USERS, null, null, null, null, null,
            "$COL_CREATED_AT DESC"
        )
        while (cursor.moveToNext()) {
            users.add(cursorToUser(cursor))
        }
        cursor.close()
        return users
    }

    /**
     * 根据ID获取用户
     */
    fun getUserById(id: Long): User? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USERS, null,
            "$COL_ID = ?", arrayOf(id.toString()),
            null, null, null
        )
        var user: User? = null
        if (cursor.moveToFirst()) {
            user = cursorToUser(cursor)
        }
        cursor.close()
        return user
    }

    /**
     * 添加用户（密码会自动哈希）
     */
    fun addUser(user: User): Long {
        val db = writableDatabase
        val values = userToContentValues(user)
        return db.insert(TABLE_USERS, null, values)
    }

    /**
     * 更新用户（密码会自动哈希）
     */
    fun updateUser(user: User): Int {
        val db = writableDatabase
        val values = userToContentValues(user)
        return db.update(TABLE_USERS, values, "$COL_ID = ?", arrayOf(user.id.toString()))
    }

    /**
     * 更新用户（不更改密码）
     */
    fun updateUserWithoutPassword(user: User): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_USERNAME, user.username)
            put(COL_DISPLAY_NAME, user.displayName)
            put(COL_EMAIL, user.email)
            put(COL_PHONE, user.phone)
            put(COL_ROLE, user.role)
            put(COL_IS_ACTIVE, if (user.isActive) 1 else 0)
            put(COL_CREATED_AT, user.createdAt)
        }
        return db.update(TABLE_USERS, values, "$COL_ID = ?", arrayOf(user.id.toString()))
    }

    /**
     * 删除用户
     */
    fun deleteUser(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_USERS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    /**
     * 检查用户名是否已存在
     */
    fun isUsernameExists(username: String, excludeId: Long = -1): Boolean {
        val db = readableDatabase
        val cursor = if (excludeId > 0) {
            db.query(
                TABLE_USERS, arrayOf(COL_ID),
                "$COL_USERNAME = ? AND $COL_ID != ?",
                arrayOf(username, excludeId.toString()),
                null, null, null
            )
        } else {
            db.query(
                TABLE_USERS, arrayOf(COL_ID),
                "$COL_USERNAME = ?", arrayOf(username),
                null, null, null
            )
        }
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    /**
     * 搜索用户
     */
    fun searchUsers(keyword: String): List<User> {
        val users = mutableListOf<User>()
        val db = readableDatabase
        val query = "%$keyword%"
        val cursor = db.query(
            TABLE_USERS, null,
            "$COL_USERNAME LIKE ? OR $COL_DISPLAY_NAME LIKE ? OR $COL_EMAIL LIKE ? OR $COL_PHONE LIKE ?",
            arrayOf(query, query, query, query),
            null, null, "$COL_CREATED_AT DESC"
        )
        while (cursor.moveToNext()) {
            users.add(cursorToUser(cursor))
        }
        cursor.close()
        return users
    }

    private fun cursorToUser(cursor: Cursor): User {
        return User(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            username = cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME)),
            password = cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD)),
            displayName = cursor.getString(cursor.getColumnIndexOrThrow(COL_DISPLAY_NAME)),
            email = cursor.getString(cursor.getColumnIndexOrThrow(COL_EMAIL)),
            phone = cursor.getString(cursor.getColumnIndexOrThrow(COL_PHONE)),
            role = cursor.getString(cursor.getColumnIndexOrThrow(COL_ROLE)),
            isActive = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_ACTIVE)) == 1,
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
        )
    }

    private fun userToContentValues(user: User): ContentValues {
        return ContentValues().apply {
            put(COL_USERNAME, user.username)
            put(COL_PASSWORD, hashPassword(user.password))
            put(COL_DISPLAY_NAME, user.displayName)
            put(COL_EMAIL, user.email)
            put(COL_PHONE, user.phone)
            put(COL_ROLE, user.role)
            put(COL_IS_ACTIVE, if (user.isActive) 1 else 0)
            put(COL_CREATED_AT, user.createdAt)
        }
    }
}
