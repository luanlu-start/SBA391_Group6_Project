package com.fptpost.modules.chat.config;

import com.fptpost.modules.chat.model.ChatMessage;
import com.fptpost.modules.chat.model.ChatRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMongoIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        MongoPersistentEntityIndexResolver resolver =
                new MongoPersistentEntityIndexResolver(
                        mongoTemplate.getConverter().getMappingContext()
                );

        createIndexes(ChatRoom.class, resolver);
        createIndexes(ChatMessage.class, resolver);
    }

    private void createIndexes(
            Class<?> documentType,
            MongoPersistentEntityIndexResolver resolver
    ) {
        resolver.resolveIndexFor(documentType)
                .forEach(index ->
                        mongoTemplate.indexOps(documentType).ensureIndex(index)
                );
    }
}