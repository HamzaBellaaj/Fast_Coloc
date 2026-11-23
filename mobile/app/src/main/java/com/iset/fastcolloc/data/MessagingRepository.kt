package com.iset.fastcolloc.data
import com.iset.fastcolloc.data.remote.*
interface MessagingDataSource {
 suspend fun owner():String
 suspend fun inbox(page:Int=0):ConversationPageDto
 suspend fun lookup(listingId:String):ConversationDto?
 suspend fun detail(id:String):ConversationDto
 suspend fun history(id:String,before:Long?=null):MessagePageDto
 suspend fun start(listingId:String,content:String,clientId:String):ConversationDto
 suspend fun send(id:String,content:String,clientId:String):PrivateMessageDto
 suspend fun read(id:String,throughId:Long)
}
class MessagingRepository(private val auth:AuthRepository):MessagingDataSource {
 override suspend fun owner()=auth.accountId()
 override suspend fun inbox(page:Int)=auth.authorized { conversations(page) }
 override suspend fun lookup(listingId:String)=auth.authorized { conversations(0,listingId) }.items.firstOrNull()
 override suspend fun detail(id:String)=auth.authorized { conversation(id) }
 override suspend fun history(id:String,before:Long?)=auth.authorized { privateMessages(id,before) }
 override suspend fun start(listingId:String,content:String,clientId:String)=auth.authorized { startConversation(StartConversationDto(listingId,content,clientId)) }
 override suspend fun send(id:String,content:String,clientId:String)=auth.authorized { sendPrivateMessage(id,SendMessageDto(content,clientId)) }
 override suspend fun read(id:String,throughId:Long)=auth.authorized { readConversation(id,ReadConversationDto(throughId)) }
}
