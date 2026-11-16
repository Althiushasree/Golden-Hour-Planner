package com.example.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.database.PhotoShootPlan
import com.example.databinding.ItemPhotoShootPlanBinding

class PhotoShootPlanAdapter(
    private val onEditClick: (PhotoShootPlan) -> Unit,
    private val onDeleteClick: (PhotoShootPlan) -> Unit
) : ListAdapter<PhotoShootPlan, PhotoShootPlanAdapter.PlanViewHolder>(PlanDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val binding = ItemPhotoShootPlanBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlanViewHolder(
        private val binding: ItemPhotoShootPlanBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(plan: PhotoShootPlan) {
            binding.tvPlanDate.text = plan.date
            binding.tvPlanPlace.text = plan.placeName
            binding.tvPlanNote.text = plan.note
            binding.root.setOnClickListener { onEditClick(plan) }
            binding.btnEditPlan.setOnClickListener { onEditClick(plan) }
            binding.btnDeletePlan.setOnClickListener { onDeleteClick(plan) }
        }
    }

    private object PlanDiffCallback : DiffUtil.ItemCallback<PhotoShootPlan>() {
        override fun areItemsTheSame(oldItem: PhotoShootPlan, newItem: PhotoShootPlan): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PhotoShootPlan, newItem: PhotoShootPlan): Boolean {
            return oldItem == newItem
        }
    }
}
