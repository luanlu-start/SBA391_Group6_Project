package com.fptpost.common.file.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.file.exception.FileErrorCode;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;

@Service @Slf4j
public class PrivateFileStorage {
    private final Path directory;
    public PrivateFileStorage(@Value("${app.files.directory:private-data}") String location) {
        directory = Paths.get(location).toAbsolutePath().normalize();
        if (directory.startsWith(Paths.get("uploads").toAbsolutePath().normalize()))
            throw new IllegalStateException("Private files must be stored outside the public uploads directory");
    }
    public UUID put(byte[] bytes) {
        UUID key = UUID.randomUUID();
        try {
            Files.createDirectories(directory);
            Files.write(path(key), bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) { throw new AppException(FileErrorCode.STORAGE_UNAVAILABLE); }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) delete(key); }
            });
        }
        return key;
    }
    public byte[] read(UUID key) {
        try {
            if (Files.size(path(key)) > 6L * 1024 * 1024) throw new AppException(FileErrorCode.STORAGE_UNAVAILABLE);
            return Files.readAllBytes(path(key));
        } catch (IOException exception) { throw new AppException(FileErrorCode.STORAGE_UNAVAILABLE); }
    }
    public void deleteAfterCommit(UUID key) {
        if (key == null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { delete(key); }
        });
    }
    private Path path(UUID key) { return directory.resolve(key.toString() + ".bin"); }
    private void delete(UUID key) {
        try { Files.deleteIfExists(path(key)); }
        catch (IOException exception) { log.warn("Could not remove an unused private file"); }
    }
}
