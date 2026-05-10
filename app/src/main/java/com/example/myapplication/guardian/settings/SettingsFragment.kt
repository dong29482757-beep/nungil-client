package com.example.myapplication.guardian.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.core.network.Session
import com.example.myapplication.guardian.onboarding.LoginActivity
import com.example.myapplication.guardian.schedule.data.ScheduleRepository
import com.example.myapplication.model.Task

class SettingsFragment : Fragment() {

    private val repository = ScheduleRepository()
    private val taskList = mutableListOf<Task>()
    private lateinit var whitelistAdapter: WhitelistAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvName   = view.findViewById<TextView>(R.id.tvGuardianName)
        val tvId     = view.findViewById<TextView>(R.id.tvGuardianId)
        val rv       = view.findViewById<RecyclerView>(R.id.rvWhitelist)
        val pb       = view.findViewById<ProgressBar>(R.id.pbWhitelist)
        val btnLogout = view.findViewById<android.widget.Button>(R.id.btnLogout)

        tvName.text = if (Session.guardianName.isNotEmpty()) Session.guardianName else "보호자"
        tvId.text   = "ID: ${Session.guardianId}"

        whitelistAdapter = WhitelistAdapter(taskList) { task ->
            confirmDeleteTask(task)
        }
        rv.layoutManager = LinearLayoutManager(context)
        rv.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        rv.adapter = whitelistAdapter

        loadWhitelist(pb)

        btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("로그아웃")
                .setMessage("로그아웃 할까요?")
                .setPositiveButton("로그아웃") { _, _ ->
                    Session.logout()
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun loadWhitelist(pb: ProgressBar) {
        pb.visibility = View.VISIBLE
        repository.getWhitelistTasks { result ->
            activity?.runOnUiThread {
                pb.visibility = View.GONE
                when (result) {
                    is ApiResult.Success -> {
                        taskList.clear()
                        taskList.addAll(result.data)
                        whitelistAdapter.notifyDataSetChanged()
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(context, "과업 목록 로드 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun confirmDeleteTask(task: Task) {
        AlertDialog.Builder(requireContext())
            .setTitle("과업 삭제")
            .setMessage("'${task.taskName}' 과업을 목록에서 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                repository.deleteWhitelistTask(task.taskId) { result ->
                    activity?.runOnUiThread {
                        when (result) {
                            is ApiResult.Success -> {
                                taskList.remove(task)
                                whitelistAdapter.notifyDataSetChanged()
                                Toast.makeText(context, "삭제됐어요.", Toast.LENGTH_SHORT).show()
                            }
                            is ApiResult.Error -> {
                                Toast.makeText(context, "삭제 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<ProgressBar>(R.id.pbWhitelist)?.let { loadWhitelist(it) }
    }
}

// 화이트리스트 어댑터 (SettingsFragment 전용)
class WhitelistAdapter(
    private val list: MutableList<Task>,
    private val onDelete: (Task) -> Unit
) : RecyclerView.Adapter<WhitelistAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView      = view.findViewById(R.id.tvTaskName)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_whitelist_task, parent, false))

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val task = list[position]
        holder.tvName.text = task.taskName
        holder.btnDelete.setOnClickListener { onDelete(task) }
    }
}
