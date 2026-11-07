package com.roomcrosscheck.listing.service;

import com.roomcrosscheck.listing.domain.Advertisement;
import com.roomcrosscheck.listing.dto.AdvertisementResponse;
import com.roomcrosscheck.listing.repository.AdvertisementRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

@Service
public class AdvertisementService {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private final AdvertisementRepository repository;
    private final Path storageDirectory;

    public AdvertisementService(AdvertisementRepository repository,
            @Value("${ads.storage-dir:/tmp/roomcrosscheck-ads}") String storageDirectory) {
        this.repository = repository;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public List<AdvertisementResponse> all() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
            .stream().map(AdvertisementResponse::from).toList();
    }

    public AdvertisementResponse one(String id) { return AdvertisementResponse.from(find(id)); }

    public AdvertisementResponse create(String key, String text, String addressHint,
            String unitLabel, String sourceUrl, MultipartFile image) {
        if (key == null || !key.matches("[A-Za-z0-9-]{8,128}")) bad("유효한 Idempotency-Key가 필요합니다.");
        String original = text == null ? "" : text;
        if (original.length() > 10000) bad("광고 글은 10000자를 넘을 수 없습니다.");
        checkLength(addressHint, 160, "주소");
        checkLength(unitLabel, 40, "동·호수");
        checkLength(sourceUrl, 1000, "출처 URL");
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            try {
                URI uri = URI.create(sourceUrl);
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) bad("출처 URL은 HTTP 또는 HTTPS 주소여야 합니다.");
            } catch (IllegalArgumentException e) { bad("출처 URL 형식이 올바르지 않습니다."); }
        }
        boolean hasImage = image != null && !image.isEmpty();
        if (original.isBlank() && !hasImage) bad("광고 글이나 이미지를 입력해 주세요.");
        byte[] bytes = hasImage ? readImage(image) : new byte[0];
        String extension = hasImage ? extensionFor(image, bytes) : null;
        String hash = hash(original, addressHint, unitLabel, sourceUrl, bytes);
        var existing = repository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().requestHash, hash))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 요청 키에 다른 내용이 사용됐습니다.");
            return AdvertisementResponse.from(existing.get());
        }
        Advertisement ad = new Advertisement();
        ad.idempotencyKey = key;
        ad.requestHash = hash;
        ad.originalText = original;
        ad.addressHint = blankToNull(addressHint);
        ad.unitLabel = blankToNull(unitLabel);
        ad.sourceUrl = blankToNull(sourceUrl);
        if (hasImage) {
            ad.imageName = safeName(image.getOriginalFilename());
            ad.imageContentType = "jpg".equals(extension) ? "image/jpeg" : "image/png";
            ad.imageSize = (long) bytes.length;
            ad.imageStorageKey = UUID.randomUUID() + "." + extension;
        }
        Path stored = hasImage ? storageDirectory.resolve(ad.imageStorageKey) : null;
        try {
            if (stored != null) {
                Files.createDirectories(storageDirectory);
                Files.write(stored, bytes, StandardOpenOption.CREATE_NEW);
            }
            return AdvertisementResponse.from(repository.save(ad));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "원본 이미지를 저장하지 못했습니다.", e);
        } catch (RuntimeException e) {
            if (stored != null) try { Files.deleteIfExists(stored); } catch (IOException ignored) { }
            throw e;
        }
    }

    public AdvertisementResponse confirm(String id) {
        Advertisement ad = find(id);
        if (ad.status == Advertisement.Status.DRAFT) {
            ad.status = Advertisement.Status.EXTRACTION_PENDING;
            ad.confirmedAt = Instant.now();
            ad = repository.save(ad);
        }
        return AdvertisementResponse.from(ad);
    }

    private Advertisement find(String id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void checkLength(String value, int max, String field) {
        if (value != null && value.length() > max) bad(field + " 길이 제한을 초과했습니다.");
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String safeName(String value) {
        String name = value == null ? "" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.length() > 255) name = name.substring(name.length() - 255);
        return name;
    }
    private static byte[] readImage(MultipartFile image) {
        if (image.getSize() > MAX_IMAGE_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        try { return image.getBytes(); }
        catch (IOException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지를 읽지 못했습니다.", e); }
    }
    private static String extensionFor(MultipartFile image, byte[] bytes) {
        String name = safeName(image.getOriginalFilename()).toLowerCase();
        String type = image.getContentType();
        boolean jpeg = bytes.length >= 4 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8
            && (bytes[2] & 255) == 0xff && (bytes[bytes.length - 2] & 255) == 0xff && (bytes[bytes.length - 1] & 255) == 0xd9;
        byte[] pngHeader = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        boolean png = bytes.length >= 24 && java.util.Arrays.equals(java.util.Arrays.copyOf(bytes, 8), pngHeader)
            && bytes[12] == 'I' && bytes[13] == 'H' && bytes[14] == 'D' && bytes[15] == 'R';
        if (jpeg && (name.endsWith(".jpg") || name.endsWith(".jpeg")) && "image/jpeg".equals(type)) {
            validateDecodableImage(bytes);
            return "jpg";
        }
        if (png && name.endsWith(".png") && "image/png".equals(type)) {
            validateDecodableImage(bytes);
            return "png";
        }
        bad("JPG·PNG 파일의 확장자, 형식, 내용이 일치해야 합니다.");
        return null;
    }
    private static void validateDecodableImage(byte[] bytes) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) bad("이미지를 해석할 수 없습니다.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels < 1 || pixels > 40_000_000) bad("이미지 크기가 허용 범위를 초과했습니다.");
                if (reader.read(0) == null) bad("이미지를 해석할 수 없습니다.");
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ResponseStatusException responseError) throw responseError;
            bad("이미지를 해석할 수 없습니다.");
        }
    }
    private static String hash(String text, String address, String unit, String url, byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : List.of(text, Objects.toString(address, ""), Objects.toString(unit, ""), Objects.toString(url, ""))) {
                byte[] encoded = part.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(encoded.length).array());
                digest.update(encoded);
            }
            digest.update(bytes);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
