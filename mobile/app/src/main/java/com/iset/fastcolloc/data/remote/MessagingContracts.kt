package com.iset.fastcolloc.data.remote
import com.google.gson.annotations.SerializedName

data class ConversationDto(
 val id:String,
 @SerializedName("listing_id") val listingId:String?,
 @SerializedName("listing_reference") val listingReference:String,
 @SerializedName("listing_title") val listingTitle:String,
 @SerializedName("listing_kind") val listingKind:String,
 val locality:String, val rent:Double,
 @SerializedName("participant_name") val participantName:String,
 @SerializedName("participant_id") val participantId:String,
 @SerializedName("last_message") val lastMessage:String,
 @SerializedName("unread_count") val unreadCount:Int,
 @SerializedName("listing_available") val listingAvailable:Boolean,
)
data class ConversationPageDto(val items:List<ConversationDto>,val total:Int,val page:Int,val size:Int)
data class PrivateMessageDto(val id:Long,@SerializedName("conversation_id") val conversationId:String,@SerializedName("sender_id") val senderId:String,@SerializedName("client_id") val clientId:String,val content:String,@SerializedName("created_at") val createdAt:String)
data class MessagePageDto(val items:List<PrivateMessageDto>,val hasMore:Boolean,val nextBefore:Long?)
data class StartConversationDto(val listingId:String,val content:String,val clientId:String)
data class SendMessageDto(val content:String,val clientId:String)
data class ReadConversationDto(val throughId:Long)
