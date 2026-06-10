package com.androiddevs.runningapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView // 🌟 NUEVO
import android.widget.TextView  // 🌟 NUEVO
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.androiddevs.runningapp.R
import com.androiddevs.runningapp.db.Run
import com.androiddevs.runningapp.other.TrackingUtility
import com.bumptech.glide.Glide
// 🌟 CORREGIDO: Se eliminó el import obsoleto de kotlinx.android.synthetic
import java.text.SimpleDateFormat
import java.util.*

class RunAdapter : RecyclerView.Adapter<RunAdapter.RunViewHolder>() {

    private val diffCallback = object : DiffUtil.ItemCallback<Run>() {
        override fun areItemsTheSame(oldItem: Run, newItem: Run): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Run, newItem: Run): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }
    }

    val differ = AsyncListDiffer(this, diffCallback)

    private var onItemClickListener: ((Run) -> Unit)? = null

    inner class RunViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    fun submitList(list: List<Run>) = differ.submitList(list)

    fun setOnItemClickListener(listener: (Run) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunViewHolder {
        return RunViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_run,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    override fun onBindViewHolder(holder: RunViewHolder, position: Int) {
        val run = differ.currentList[position]

        // 🌟 CORREGIDO: Búsqueda explícita de vistas para total compatibilidad con Kotlin moderno
        val ivRunImage = holder.itemView.findViewById<ImageView>(R.id.ivRunImage)
        val tvDate = holder.itemView.findViewById<TextView>(R.id.tvDate)
        val tvAvgSpeed = holder.itemView.findViewById<TextView>(R.id.tvAvgSpeed)
        val tvDistance = holder.itemView.findViewById<TextView>(R.id.tvDistance)
        val tvTime = holder.itemView.findViewById<TextView>(R.id.tvTime)
        val tvCalories = holder.itemView.findViewById<TextView>(R.id.tvCalories)
        val tvGoalStatus = holder.itemView.findViewById<TextView>(R.id.tvGoalStatus)

        // Asignación de datos a las vistas encontradas
        if (run.img != null) {
            Glide.with(holder.itemView.context).load(run.img).into(ivRunImage)
        } else {
            Glide.with(holder.itemView.context).clear(ivRunImage)
            ivRunImage.setImageResource(R.drawable.ic_directions_run_black_24dp)
        }

        val calendar = Calendar.getInstance().apply {
            timeInMillis = run.timestamp
        }
        val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
        tvDate.text = dateFormat.format(calendar.time)

        "${run.avgSpeedInKMH}km/h".also {
            tvAvgSpeed.text = it
        }
        "${run.distanceInMeters / 1000f}km".also {
            tvDistance.text = it
        }
        tvTime.text = TrackingUtility.getFormattedStopWatchTime(run.timeInMillis)
        "${run.caloriesBurned}kcal".also {
        tvCalories.text = it
        }
        tvGoalStatus.text = getGoalStatusText(holder, run)

        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(run)
        }
    }

    private fun getGoalStatusText(holder: RunViewHolder, run: Run): String {
        val context = holder.itemView.context
        if (run.goalType.isBlank() || run.goalValue <= 0f) {
            return context.getString(R.string.history_goal_none)
        }

        val goalName = when (run.goalType) {
            "DISTANCE" -> context.getString(R.string.goal_type_distance)
            "TIME" -> context.getString(R.string.goal_type_time)
            "CALORIES" -> context.getString(R.string.goal_type_calories)
            else -> run.goalType
        }
        val goalValue = when (run.goalType) {
            "DISTANCE" -> String.format(Locale.getDefault(), "%.2f km", run.goalValue)
            "TIME" -> String.format(Locale.getDefault(), "%.1f min", run.goalValue)
            "CALORIES" -> "${run.goalValue.toInt()} kcal"
            else -> run.goalValue.toString()
        }
        val goalResult = if (run.goalAchieved) {
            context.getString(R.string.history_goal_achieved)
        } else {
            context.getString(R.string.history_goal_pending)
        }

        return context.getString(R.string.history_goal_status, goalName, goalValue, goalResult)
    }
}
