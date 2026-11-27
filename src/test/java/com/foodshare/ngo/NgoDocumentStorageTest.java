package com.foodshare.ngo;

import com.foodshare.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NgoDocumentStorageTest {
    @TempDir
    Path dir;

    @Test
    void fileTypeIsDetectedFromTheFirstBytes() {
        assertEquals("pdf", NgoDocumentStorage.detect("%PDF-1.7".getBytes()));
        assertEquals("png", NgoDocumentStorage.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D}));
        assertEquals("jpg", NgoDocumentStorage.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertNull(NgoDocumentStorage.detect("MZ-an-exe".getBytes()));
        assertNull(NgoDocumentStorage.detect(new byte[]{1}));
    }

    @Test
    void storesValidDocumentsUnderARandomNameAndRejectsOtherFiles() {
        NgoDocumentStorage storage = new NgoDocumentStorage(dir.toString());
        String name = storage.store(new MockMultipartFile("certificate", "my cert.pdf", "application/pdf", "%PDF-1.4 hello".getBytes()));
        assertTrue(name.matches("[0-9a-f]{32}\\.pdf"));
        assertTrue(storage.load(name).exists());
        // a script renamed to .pdf, or an empty upload, is refused
        assertThrows(ApiException.class, () -> storage.store(new MockMultipartFile("certificate", "evil.pdf", "application/pdf", "<script>alert(1)</script>".getBytes())));
        assertThrows(ApiException.class, () -> storage.store(new MockMultipartFile("certificate", "empty.pdf", "application/pdf", new byte[0])));
        assertThrows(ApiException.class, () -> storage.store(null));
    }

    @Test
    void oversizedFilesAreRefused() {
        NgoDocumentStorage storage = new NgoDocumentStorage(dir.toString());
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        big[0] = '%'; big[1] = 'P'; big[2] = 'D'; big[3] = 'F';
        assertThrows(ApiException.class, () -> storage.store(new MockMultipartFile("certificate", "big.pdf", "application/pdf", big)));
    }

    @Test
    void loadingRefusesPathTricksAndMissingFiles() {
        NgoDocumentStorage storage = new NgoDocumentStorage(dir.toString());
        assertThrows(ApiException.class, () -> storage.load("../../etc/passwd"));
        assertThrows(ApiException.class, () -> storage.load("certificate.pdf"));
        assertThrows(ApiException.class, () -> storage.load("00000000000000000000000000000000.pdf")); // well-formed but missing
        assertThrows(ApiException.class, () -> storage.load(null));
    }
}
