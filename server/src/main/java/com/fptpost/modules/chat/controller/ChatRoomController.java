package com.fptpost.modules.chat.controller;

import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.chat.dto.request.SendChatMessageRequest;
import com.fptpost.modules.chat.dto.response.ChatMessageResponse;
import com.fptpost.modules.chat.service.ChatMessageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.chat.dto.response.ChatRoomResponse;
import com.fptpost.modules.chat.service.ChatRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
@Tag(name = "Chat Rooms", description = "Order conversation APIs")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    @PostMapping("/orders/{orderId}")
    @Operation(summary = "Open the conversation for an assigned order")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> openRoom(
            @PathVariable Long orderId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ChatRoomResponse room = chatRoomService.openRoom(
                orderId,
                requirePublicId(jwt)
        );

        return ResponseEntity.ok(
                ApiResponse.success("Chat room opened successfully", room)
        );
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "Get a conversation accessible to the current user")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> getRoom(
            @PathVariable String roomId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ChatRoomResponse room = chatRoomService.getRoom(
                roomId,
                requirePublicId(jwt)
        );

        return ResponseEntity.ok(
                ApiResponse.success("Chat room retrieved successfully", room)
        );
    }
    @PostMapping("/{roomId}/messages")
    @Operation(summary = "Send a text message to a conversation")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @PathVariable String roomId,
            @Valid @RequestBody SendChatMessageRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ChatMessageResponse message = chatMessageService.sendMessage(
                roomId,
                requirePublicId(jwt),
                request
        );

        return ResponseEntity.ok(
                ApiResponse.success("Chat message accepted successfully", message)
        );
    }

    @GetMapping("/{roomId}/messages")
    @Operation(summary = "Get paginated conversation history")
    public ResponseEntity<ApiResponse<PageResponse<ChatMessageResponse>>> getHistory(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        PageResponse<ChatMessageResponse> history =
                chatMessageService.getHistory(
                        roomId,
                        requirePublicId(jwt),
                        page,
                        size
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Chat history retrieved successfully",
                        history
                )
        );
    }
    private UUID requirePublicId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
    }
}