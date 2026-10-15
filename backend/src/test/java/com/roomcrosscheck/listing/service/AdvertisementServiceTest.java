package com.roomcrosscheck.listing.service;

import com.roomcrosscheck.listing.domain.Advertisement;
import com.roomcrosscheck.listing.repository.AdvertisementRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdvertisementServiceTest {
    @TempDir Path directory;

    @Test void rejectsMissingSourceAndRenamedNonImage() {
        var repo = mock(AdvertisementRepository.class);
        var service = new AdvertisementService(repo, directory.toString());
        assertThrows(ResponseStatusException.class,
            () -> service.create("request-0001", "  ", null, null, null, null));
        var fake = new MockMultipartFile("image", "room.png", "image/png", "not an image".getBytes());
        assertThrows(ResponseStatusException.class,
            () -> service.create("request-0002", "", null, null, null, fake));
        byte[] brokenPng = {(byte)137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0,
            'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1};
        var broken = new MockMultipartFile("image", "room.png", "image/png", brokenPng);
        assertThrows(ResponseStatusException.class,
            () -> service.create("request-0004", "", null, null, null, broken));
        verifyNoInteractions(repo);
    }

    @Test void preservesOneOriginalAcrossRetryAndConfirmation() throws Exception {
        var repo = mock(AdvertisementRepository.class);
        when(repo.save(any(Advertisement.class))).thenAnswer(call -> call.getArgument(0));
        var service = new AdvertisementService(repo, directory.toString());
        var pixels = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(pixels, "png", output);
        var image = new MockMultipartFile("image", "room.png", "image/png", output.toByteArray());
        var first = service.create("request-0003", "보증금 500", "주소", null, null, image);
        assertEquals(Advertisement.Status.DRAFT, first.status());
        assertEquals("room.png", first.imageName());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
        // Capture the stored entity to ensure retries use the original request fingerprint.
        var capture = org.mockito.ArgumentCaptor.forClass(Advertisement.class);
        verify(repo).save(capture.capture());
        var saved = capture.getValue();
        when(repo.findByIdempotencyKey("request-0003")).thenReturn(Optional.of(saved));
        assertEquals(first.id(), service.create("request-0003", "보증금 500", "주소", null, null, image).id());
        assertThrows(ResponseStatusException.class,
            () -> service.create("request-0003", "월세 500", "주소", null, null, image));
        when(repo.findById(saved.id)).thenReturn(Optional.of(saved));
        assertEquals(Advertisement.Status.EXTRACTION_PENDING, service.confirm(saved.id).status());
        assertEquals(first.id(), service.confirm(saved.id).id());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
