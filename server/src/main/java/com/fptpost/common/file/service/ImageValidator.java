package com.fptpost.common.file.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.file.exception.FileErrorCode;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImageValidator {
    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new AppException(FileErrorCode.INVALID_IMAGE);
        if (file.getSize() > 5L * 1024 * 1024) throw new AppException(FileErrorCode.TOO_LARGE);
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!name.matches("(?s).*\\.(jpg|jpeg|png|webp)$")) throw new AppException(FileErrorCode.INVALID_IMAGE);
        try {
            byte[] bytes = file.getBytes();
            // Keep sensitive image decoding in memory, including ImageIO's input cache.
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new AppException(FileErrorCode.INVALID_IMAGE);
                var reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    String mediaType = switch (format) {
                        case "jpeg", "jpg" -> "image/jpeg";
                        case "png" -> "image/png";
                        case "webp" -> "image/webp";
                        default -> throw new AppException(FileErrorCode.INVALID_IMAGE);
                    };
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 10000 || height > 10000 || (long) width * height > 20000000)
                        throw new AppException(FileErrorCode.INVALID_IMAGE);
                    var image = reader.read(0);
                    if (image == null) throw new AppException(FileErrorCode.INVALID_IMAGE);
                    image.flush();
                    return new ValidatedImage(bytes, mediaType);
                } finally { reader.dispose(); }
            }
        } catch (AppException exception) { throw exception; }
        catch (Exception exception) { throw new AppException(FileErrorCode.INVALID_IMAGE); }
    }
}
