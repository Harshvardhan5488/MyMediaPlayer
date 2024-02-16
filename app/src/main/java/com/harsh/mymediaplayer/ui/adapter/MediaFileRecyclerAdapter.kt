package com.harsh.mymediaplayer.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.harsh.mymediaplayer.databinding.LayoutAudioFileBinding

interface OnItemPlayClickListener {
    fun playAudio(link: String)
}

class MediaFileRecyclerAdapter(
    private val feedList: List<String>,
    private val onItemPlayClickListener: OnItemPlayClickListener
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return AudioFileViewHolder(LayoutAudioFileBinding.inflate(LayoutInflater.from(parent.context), parent, false), onItemPlayClickListener)
    }

    override fun getItemCount(): Int = feedList.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        holder.apply {
            when (this) {
                is AudioFileViewHolder -> setUpUI(feedList[position])
            }
        }
    }

    inner class AudioFileViewHolder(
        private val itemBinding: LayoutAudioFileBinding,
        private val onItemPlayClickListener: OnItemPlayClickListener
    ): RecyclerView.ViewHolder(itemBinding.root) {
        fun setUpUI(url: String) {
            // callback with url
            itemBinding.playPauseIv.setOnClickListener {
                onItemPlayClickListener.playAudio(url)
            }
        }
    }
}