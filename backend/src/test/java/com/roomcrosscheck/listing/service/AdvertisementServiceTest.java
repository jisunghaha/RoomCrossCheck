package com.roomcrosscheck.listing.service;

import com.roomcrosscheck.listing.domain.Advertisement;
import com.roomcrosscheck.listing.repository.AdvertisementRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
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
        when(repo.saveAndFlush(any(Advertisement.class))).thenAnswer(call -> call.getArgument(0));
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
        verify(repo).saveAndFlush(capture.capture());
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

    @Test void concurrentRetryReturnsWinnerAndRemovesOnlyLosingImage() throws Exception {
        var repo = mock(AdvertisementRepository.class);
        var winner = new Advertisement();
        var duplicate = new DataIntegrityViolationException("duplicate key");
        when(repo.findByIdempotencyKey("request-race")).thenReturn(Optional.empty(), Optional.of(winner));
        when(repo.saveAndFlush(any(Advertisement.class))).thenAnswer(call -> {
            Advertisement attempted = call.getArgument(0);
            winner.requestHash = attempted.requestHash;
            winner.imageName = attempted.imageName;
            throw duplicate;
        });
        Files.writeString(directory.resolve("existing.png"), "preserve existing file");
        var pixels = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(pixels, "png", output);
        var image = new MockMultipartFile("image", "room.png", "image/png", output.toByteArray());
        var service = new AdvertisementService(repo, directory.toString());
        assertEquals(winner.id, service.create("request-race", "광고", null, null, null, image).id());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
        assertEquals("preserve existing file", Files.readString(directory.resolve("existing.png")));
    }

    @Test void concurrentDifferentContentConflictsAndUnrelatedDbFailureIsNotReplayed() {
        var repo = mock(AdvertisementRepository.class);
        var winner = new Advertisement();
        winner.requestHash = "different request hash";
        when(repo.findByIdempotencyKey("request-conflict")).thenReturn(Optional.empty(), Optional.of(winner));
        var failure = new DataIntegrityViolationException("constraint failure");
        when(repo.saveAndFlush(any(Advertisement.class))).thenThrow(failure);
        var service = new AdvertisementService(repo, directory.toString());
        var conflict = assertThrows(ResponseStatusException.class,
            () -> service.create("request-conflict", "다른 광고", null, null, null, null));
        assertEquals(409, conflict.getStatusCode().value());
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
            () -> service.create("request-other", "광고", null, null, null, null)));
    }

    @Test void oversizedImageIsRejectedBeforeReadingBytes() throws Exception {
        var image = mock(org.springframework.web.multipart.MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        var service = new AdvertisementService(mock(AdvertisementRepository.class), directory.toString());
        var error = assertThrows(ResponseStatusException.class,
            () -> service.create("request-large", "", null, null, null, image));
        assertEquals(413, error.getStatusCode().value());
        assertTrue(error.getReason().contains("10MB"));
        verify(image, never()).getBytes();
    }
}
