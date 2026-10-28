package com.fptpost.modules.chat.mapper;

import com.fptpost.modules.chat.dto.response.ChatMessageResponse;
import com.fptpost.modules.chat.dto.response.ChatRoomResponse;
import com.fptpost.modules.chat.model.ChatMessage;
import com.fptpost.modules.chat.model.ChatRoom;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface ChatMapper {

    ChatRoomResponse toRoomResponse(ChatRoom room);

    ChatMessageResponse toMessageResponse(ChatMessage message);
}