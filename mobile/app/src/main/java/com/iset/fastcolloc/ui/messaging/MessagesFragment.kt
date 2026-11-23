package com.iset.fastcolloc.ui.messaging
import android.os.Bundle
import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.*
import com.iset.fastcolloc.R
import com.iset.fastcolloc.databinding.FragmentMessagesBinding
import com.iset.fastcolloc.ui.auth.BoundFragment
import com.iset.fastcolloc.viewmodel.*
import kotlinx.coroutines.*
class MessagesFragment:BoundFragment<FragmentMessagesBinding>(FragmentMessagesBinding::inflate) {
 private val messaging:MessagingViewModel by viewModels{MessagingViewModelFactory(requireActivity().application)}
 override fun onViewCreated(view:View,state:Bundle?){
  val thread=arguments?.getString("conversationId").orEmpty().isNotBlank()||arguments?.getString("listingId").orEmpty().isNotBlank()
  val adapter=RowsAdapter{ id->findNavController().navigate(R.id.conversationFragment,Bundle().apply{putString("conversationId",id)})}
  binding.messageRows.layoutManager=LinearLayoutManager(requireContext());binding.messageRows.adapter=adapter
  binding.composer.isVisible=thread
  binding.messageDraft.doAfterTextChanged{messaging.draft(it.toString());binding.sendMessage.isEnabled=it.toString().isNotBlank()&&messaging.state.value?.sending!=true&&messaging.state.value?.loading!=true}
  binding.sendMessage.setOnClickListener{messaging.send()};binding.refreshMessages.setOnClickListener{messaging.refresh()}
  binding.loadMore.setOnClickListener{if(thread)messaging.older() else messaging.more()}
  binding.viewListing.setOnClickListener{messaging.state.value?.selected?.listingId?.let{id->findNavController().navigate(R.id.listingDetailFragment,Bundle().apply{putString("listingId",id)})}}
  messaging.event.observe(viewLifecycleOwner){it.consume()?.let{findNavController().navigate(R.id.loginFragment)}}
  messaging.state.observe(viewLifecycleOwner){result->
   binding.threadTitle.text=if(thread)result.selected?.let{it.participantName+" · "+it.listingTitle}?:result.listingTitle else getString(R.string.messages_title)
   binding.messageState.text=result.error?:if(result.loading)"Chargement…" else if(thread&&result.selected!=null&&!result.selected.listingAvailable)getString(R.string.messages_unavailable) else if(thread&&result.messages.isEmpty())getString(R.string.messages_first) else if(!thread&&result.conversations.isEmpty())getString(R.string.messages_empty) else ""
   binding.viewListing.isVisible=thread&&result.selected?.listingAvailable==true&&result.selected.listingId!=null
   binding.loadMore.isVisible=if(thread)result.hasMore else result.moreInbox
   binding.loadMore.setText(if(thread)R.string.messages_older else R.string.messages_more)
   binding.loadMore.isEnabled=!result.loading&&!result.sending;binding.refreshMessages.isEnabled=!result.loading&&!result.sending
   binding.messageDraft.isEnabled=!result.sending
   if(binding.messageDraft.text.toString()!=result.draft)binding.messageDraft.setText(result.draft)
   binding.sendMessage.isEnabled=result.draft.isNotBlank()&&!result.sending&&!result.loading
   val manager=binding.messageRows.layoutManager as LinearLayoutManager
   val previousSize=adapter.itemCount
   val previousLastId=adapter.lastId
   val follow=previousSize==0||manager.findLastVisibleItemPosition()>=previousSize-2
   adapter.submit(if(thread)result.messages.map {m->Row(m.id.toString(),m.content,m.createdAt.replace('T',' ').take(16)+" UTC",m.senderId==result.ownerId)} else result.conversations.map{c->Row(c.id,c.participantName+if(c.unreadCount>0)" ("+c.unreadCount+" non lus)" else "",c.listingTitle+"\n"+c.lastMessage,false)},thread)
   if(thread&&follow&&adapter.itemCount>0&&(previousSize==0||previousLastId!=result.messages.lastOrNull()?.id?.toString())){val recycler=binding.messageRows;val owner=viewLifecycleOwner;recycler.post{if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))recycler.scrollToPosition(adapter.itemCount-1)}}
  }
  messaging.open(arguments?.getString("conversationId")?.takeIf{it.isNotBlank()},arguments?.getString("listingId")?.takeIf{it.isNotBlank()},arguments?.getString("listingTitle").orEmpty())
  viewLifecycleOwner.lifecycleScope.launch{viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED){while(isActive){delay(5000);messaging.refresh()}}}
 }
 override fun onDestroyView(){binding.messageRows.adapter=null;super.onDestroyView()}
 private data class Row(val id:String,val title:String,val detail:String,val mine:Boolean)
 private class RowsAdapter(private val open:(String)->Unit):RecyclerView.Adapter<RowsAdapter.Holder>(){
  private var rows:List<Row> = emptyList();private var thread=false
  val lastId:String? get()=rows.lastOrNull()?.id
  fun submit(value:List<Row>,isThread:Boolean){if(rows==value&&thread==isThread)return;val old=rows;val diff=DiffUtil.calculateDiff(object:DiffUtil.Callback(){override fun getOldListSize()=old.size;override fun getNewListSize()=value.size;override fun areItemsTheSame(a:Int,b:Int)=old[a].id==value[b].id;override fun areContentsTheSame(a:Int,b:Int)=old[a]==value[b]});rows=value;thread=isThread;diff.dispatchUpdatesTo(this)}
  class Holder(val text:TextView):RecyclerView.ViewHolder(text)
  override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder{val text=TextView(parent.context);text.layoutParams=RecyclerView.LayoutParams(-1,-2);text.setPadding(24,20,24,20);text.textSize=16f;return Holder(text)}
  override fun getItemCount()=rows.size
  override fun onBindViewHolder(holder:Holder,position:Int){val row=rows[position];holder.text.text=row.title+"\n"+row.detail;holder.text.setTextColor(Color.rgb(37,21,50));holder.text.setBackgroundColor(if(thread&&row.mine)Color.rgb(239,229,252)else Color.rgb(255,246,239));holder.text.gravity=if(thread&&row.mine)Gravity.END else Gravity.START;holder.text.setOnClickListener{if(!thread)open(row.id)}}
 }
}
