export interface ChatRoom {
  id: string;
  orderId: number;
  senderId: number;
  courierId: number;
  createdAt: string;
  lastMessageAt: string | null;
  readOnlyAt: string | null;
}

export interface ChatMessage {
  id: string;
  roomId: string;
  authorId: number;
  clientMessageId: string;
  content: string;
  createdAt: string;
}

export interface SendChatMessageRequest {
  clientMessageId: string;
  content: string;
}