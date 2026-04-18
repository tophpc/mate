package com.example.usermanagement

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 用户管理界面 - 用户列表 + 搜索 + 增删改
 */
class UserManagementActivity : AppCompatActivity() {

    private lateinit var dbHelper: UserDatabaseHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserAdapter
    private lateinit var tvWelcome: TextView
    private lateinit var tvUserCount: TextView
    private lateinit var searchView: SearchView
    private lateinit var btnAddUser: Button
    private lateinit var btnLogout: Button

    private var currentUserId: Long = -1
    private var currentUserRole: String = ""
    private var currentUserName: String = ""

    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_management)

        currentUserId = intent.getLongExtra("current_user_id", -1)
        currentUserRole = intent.getStringExtra("current_user_role") ?: ""
        currentUserName = intent.getStringExtra("current_user_name") ?: ""

        dbHelper = UserDatabaseHelper(this)

        activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                loadUsers()
            }
        }

        initViews()
        loadUsers()
    }

    private fun initViews() {
        tvWelcome = findViewById(R.id.tv_welcome)
        tvUserCount = findViewById(R.id.tv_user_count)
        searchView = findViewById(R.id.search_view)
        recyclerView = findViewById(R.id.rv_users)
        btnAddUser = findViewById(R.id.btn_add_user)
        btnLogout = findViewById(R.id.btn_logout)

        tvWelcome.text = "欢迎，$currentUserName（$currentUserRole）"

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UserAdapter(
            onEditClick = { user -> editUser(user) },
            onDeleteClick = { user -> confirmDeleteUser(user) },
            onToggleActiveClick = { user -> toggleUserActive(user) }
        )
        recyclerView.adapter = adapter

        btnAddUser.setOnClickListener {
            val intent = Intent(this, AddEditUserActivity::class.java)
            activityResultLauncher.launch(intent)
        }

        btnLogout.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchUsers(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    loadUsers()
                } else {
                    searchUsers(newText)
                }
                return true
            }
        })
    }

    private fun loadUsers() {
        val users = dbHelper.getAllUsers()
        adapter.submitList(users)
        tvUserCount.text = "共 ${users.size} 个用户"
    }

    private fun searchUsers(keyword: String) {
        val users = dbHelper.searchUsers(keyword)
        adapter.submitList(users)
        tvUserCount.text = "搜索到 ${users.size} 个用户"
    }

    private fun editUser(user: User) {
        val intent = Intent(this, AddEditUserActivity::class.java).apply {
            putExtra("user_id", user.id)
        }
        activityResultLauncher.launch(intent)
    }

    private fun confirmDeleteUser(user: User) {
        if (user.id == currentUserId) {
            Toast.makeText(this, "不能删除当前登录的用户", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除用户「${user.displayName}」吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                dbHelper.deleteUser(user.id)
                Toast.makeText(this, "用户已删除", Toast.LENGTH_SHORT).show()
                loadUsers()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toggleUserActive(user: User) {
        if (user.id == currentUserId) {
            Toast.makeText(this, "不能禁用当前登录的用户", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedUser = user.copy(isActive = !user.isActive)
        dbHelper.updateUserWithoutPassword(updatedUser)
        val status = if (updatedUser.isActive) "启用" else "禁用"
        Toast.makeText(this, "用户已${status}", Toast.LENGTH_SHORT).show()
        loadUsers()
    }
}
