package com.iset.fastcolloc.ui.messaging
import com.iset.fastcolloc.ui.showBusy
import android.os.Bundle
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
   binding.sendMessage.showBusy(result.sending)
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
  override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder{val text=TextView(parent.context);text.layoutParams=RecyclerView.LayoutParams(-1,-2);val unit=parent.resources.displayMetrics.density;text.setPadding((16*unit).toInt(),(13*unit).toInt(),(16*unit).toInt(),(13*unit).toInt());text.textSize=14f;text.typeface=androidx.core.content.res.ResourcesCompat.getFont(parent.context,R.font.dm_sans);return Holder(text)}
  override fun getItemCount()=rows.size
  override fun onBindViewHolder(holder:Holder,position:Int){
   val row=rows[position];val context=holder.text.context;val unit=context.resources.displayMetrics.density
   val own=thread&&row.mine;val ink=androidx.core.content.ContextCompat.getColor(context,if(own)R.color.fc_white else R.color.fc_text)
   val secondary=androidx.core.content.ContextCompat.getColor(context,if(own)R.color.fc_cream else R.color.fc_muted)
   val text=android.text.SpannableString(row.title+"\n"+row.detail)
   text.setSpan(android.text.style.RelativeSizeSpan(.8f),row.title.length+1,text.length,0);text.setSpan(android.text.style.ForegroundColorSpan(secondary),row.title.length+1,text.length,0)
   holder.text.text=text;holder.text.setTextColor(ink)
   holder.text.background=android.graphics.drawable.GradientDrawable().apply{setColor(androidx.core.content.ContextCompat.getColor(context,if(own)R.color.fc_violet else if(thread)R.color.fc_surface_alt else R.color.fc_surface));cornerRadius=12*unit}
   holder.text.layoutParams=RecyclerView.LayoutParams(-1,-2).apply{setMargins(if(own)(40*unit).toInt()else 0,(4*unit).toInt(),if(thread&&!own)(40*unit).toInt()else 0,(10*unit).toInt())}
   holder.text.gravity=if(own)Gravity.END else Gravity.START;holder.text.setOnClickListener{if(!thread)open(row.id)}
  }
 }
}
