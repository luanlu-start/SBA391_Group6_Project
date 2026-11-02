package com.fptpost.modules.chat.mapper;

import com.fptpost.modules.chat.dto.response.ChatMessageResponse;
import com.fptpost.modules.chat.dto.response.ChatRoomResponse;
import com.fptpost.modules.chat.model.ChatMessage;
import com.fptpost.modules.chat.model.ChatRoom;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;


@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface ChatMapper {
    @Mapping(target = "readOnly", ignore = true)
    ChatRoomResponse toRoomResponse(ChatRoom room);

    ChatMessageResponse toMessageResponse(ChatMessage message);
}