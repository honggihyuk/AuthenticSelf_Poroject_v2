package com.authenticself.service;

import com.authenticself.config.UploadProperties;
import com.authenticself.controller.dto.UploadPhotoResponse;
import com.authenticself.domain.Space;
import com.authenticself.repository.SpaceRepository;
import com.authenticself.repository.UserRepository;
import com.authenticself.storage.ObjectStorageService;
import com.authenticself.storage.PutResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PhotoUploadService}. Uses an in-memory test-double
 * for {@link ObjectStorageService} + thin stubs for the JPA repositories so
 * we can assert the compensating-delete behaviour directly (AC-14).
 */
class PhotoUploadServiceTest {

    private RecordingStorage storage;
    private StubSpaceRepo    spaces;
    private StubUserRepo     users;
    private PhotoUploadService svc;

    @BeforeEach
    void init() {
        storage = new RecordingStorage();
        spaces  = new StubSpaceRepo();
        users   = new StubUserRepo();
        users.knownIds.add("u1");
        svc = new PhotoUploadService(storage, spaces, users, new UploadProperties());
    }

    // -----------------------------------------------------------------
    // AC-6 — happy path
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-6: happy path uploads blob, saves PENDING_ANALYSIS row")
    void happyPath() throws IOException {
        byte[] jpeg = fakeJpeg(1920, 1080);

        UploadPhotoResponse resp = svc.upload("u1", "image/jpeg", "room.jpg", jpeg);

        assertThat(resp.status()).isEqualTo("PENDING_ANALYSIS");
        assertThat(resp.roomId()).isNotBlank();
        assertThat(resp.uploadUrl()).startsWith("file://recorded/");
        assertThat(storage.uploads).hasSize(1);
        assertThat(storage.deletes).isEmpty();
        assertThat(spaces.saved).hasSize(1);
        Space saved = spaces.saved.get(0);
        assertThat(saved.getStatus()).isEqualTo(Space.Status.PENDING_ANALYSIS);
        assertThat(saved.getUserId()).isEqualTo("u1");
        assertThat(saved.getContentType()).isEqualTo("image/jpeg");
        assertThat(saved.getDimensions()).isNull();
        assertThat(saved.getAnalysisDate()).isNull();
    }

    // -----------------------------------------------------------------
    // AC-8 — missing header propagates as MISSING_USER_HEADER
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-8: missing userId -> MISSING_USER_HEADER")
    void missingUser() throws IOException {
        byte[] jpeg = fakeJpeg(1920, 1080);

        assertThatThrownBy(() -> svc.upload("", "image/jpeg", "f.jpg", jpeg))
                .isInstanceOf(UploadException.class)
                .extracting("code").isEqualTo(UploadErrorCode.MISSING_USER_HEADER);
    }

    // -----------------------------------------------------------------
    // AC-9 — unknown user -> UNKNOWN_USER, no storage call, no DB save
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-9: unknown user -> UNKNOWN_USER; no blob, no DB row")
    void unknownUser() throws IOException {
        byte[] jpeg = fakeJpeg(1920, 1080);

        assertThatThrownBy(() -> svc.upload("ghost", "image/jpeg", "f.jpg", jpeg))
                .isInstanceOf(UploadException.class)
                .extracting("code").isEqualTo(UploadErrorCode.UNKNOWN_USER);
        assertThat(storage.uploads).isEmpty();
        assertThat(spaces.saved).isEmpty();
    }

    // -----------------------------------------------------------------
    // AC-10 — image/gif rejected
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-10: image/gif -> UNSUPPORTED_MEDIA_TYPE")
    void unsupportedMime() {
        assertThatThrownBy(() -> svc.upload("u1", "image/gif", "cat.gif", new byte[]{1, 2, 3}))
                .isInstanceOf(UploadException.class)
                .extracting("code").isEqualTo(UploadErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    // -----------------------------------------------------------------
    // AC-11 — oversize rejected
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-11: > max-size bytes -> FILE_TOO_LARGE")
    void tooLarge() {
        UploadProperties p = new UploadProperties();
        p.setMaxSizeBytes(16);
        svc = new PhotoUploadService(storage, spaces, users, p);

        assertThatThrownBy(() -> svc.upload("u1", "image/jpeg", "big.jpg", new byte[32]))
                .isInstanceOf(UploadException.class)
                .extracting("code").isEqualTo(UploadErrorCode.FILE_TOO_LARGE);
    }

    // -----------------------------------------------------------------
    // AC-12 — garbage bytes -> IMAGE_UNREADABLE
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-12: garbage bytes -> IMAGE_UNREADABLE")
    void unreadable() {
        byte[] junk = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

        assertThatThrownBy(() -> svc.upload("u1", "image/jpeg", "junk.jpg", junk))
                .isInstanceOf(UploadException.class)
                .extracting("code").isEqualTo(UploadErrorCode.IMAGE_UNREADABLE);
    }

    // -----------------------------------------------------------------
    // AC-13 — 500x500 PNG -> RESOLUTION_TOO_LOW
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-13: 500x500 PNG -> RESOLUTION_TOO_LOW")
    void tooLowRes() throws IOException {
        byte[] png = fakePng(500, 500);

        assertThatThrownBy(() -> svc.upload("u1", "image/png", "tiny.png", png))
                .isInstanceOf(UploadException.class)
                .extracting("code").isEqualTo(UploadErrorCode.RESOLUTION_TOO_LOW);
    }

    // -----------------------------------------------------------------
    // AC-14 — DB throws after storage success -> compensating delete
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-14: DB failure after storage success -> compensating delete + STORAGE_PERSIST_FAILED")
    void dbFailCompensates() throws IOException {
        byte[] jpeg = fakeJpeg(1920, 1080);
        spaces.throwOnSave = true;

        assertThatThrownBy(() -> svc.upload("u1", "image/jpeg", "ok.jpg", jpeg))
                .isInstanceOf(UploadException.class)
                .extracting("code").isEqualTo(UploadErrorCode.STORAGE_PERSIST_FAILED);
        assertThat(storage.uploads).hasSize(1);
        assertThat(storage.deletes).hasSize(1);
        assertThat(storage.deletes.get(0))
                .isEqualTo(storage.uploads.keySet().iterator().next());
        assertThat(spaces.saved).isEmpty();
    }

    // -----------------------------------------------------------------
    // AC-16 — key layout: dated prefix + extension matches content type
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-16: object key matches spaces/yyyy/MM/dd/<roomId>.<ext>")
    void keyLayout() throws IOException {
        byte[] jpeg = fakeJpeg(1920, 1080);
        svc.upload("u1", "image/jpeg", "a.jpg", jpeg);
        svc.upload("u1", "image/jpeg", "b.jpg", jpeg);

        assertThat(storage.uploads.keySet()).allSatisfy(key ->
                assertThat(key).matches("spaces/\\d{4}/\\d{2}/\\d{2}/[A-Za-z0-9_-]{20,64}\\.(jpg|png|webp)"));
        assertThat(storage.uploads).hasSize(2);
    }

    // =================================================================
    // test doubles
    // =================================================================

    private static class RecordingStorage implements ObjectStorageService {
        final Map<String, byte[]> uploads = new HashMap<>();
        final List<String>        deletes = new ArrayList<>();

        @Override
        public PutResult upload(String objectKey, byte[] bytes, String contentType) {
            uploads.put(objectKey, bytes);
            return new PutResult(objectKey, "file://recorded/" + objectKey, bytes.length);
        }
        @Override
        public void delete(String objectKey) { deletes.add(objectKey); }
        @Override
        public boolean exists(String objectKey) { return uploads.containsKey(objectKey); }
    }

    /** Minimal subset of JpaRepository methods actually used by the service. */
    private static class StubSpaceRepo implements SpaceRepository {
        final List<Space> saved = new ArrayList<>();
        boolean throwOnSave = false;

        @Override public <S extends Space> S save(S entity) {
            if (throwOnSave) throw new RuntimeException("simulated DB failure");
            saved.add(entity);
            return entity;
        }
        // --- all other JpaRepository methods: unused, throw to surface bugs ---
        @Override public <S extends Space> java.util.List<S> saveAll(Iterable<S> entities)         { throw nope(); }
        @Override public java.util.Optional<Space> findById(String s)                               { throw nope(); }
        @Override public boolean existsById(String s)                                               { throw nope(); }
        @Override public java.util.List<Space> findAll()                                            { throw nope(); }
        @Override public java.util.List<Space> findAll(org.springframework.data.domain.Sort sort)   { throw nope(); }
        @Override public java.util.List<Space> findAllById(Iterable<String> strings)                { throw nope(); }
        @Override public long count()                                                               { throw nope(); }
        @Override public void deleteById(String s)                                                  { throw nope(); }
        @Override public void delete(Space entity)                                                  { throw nope(); }
        @Override public void deleteAllById(Iterable<? extends String> strings)                     { throw nope(); }
        @Override public void deleteAll(Iterable<? extends Space> entities)                         { throw nope(); }
        @Override public void deleteAll()                                                           { throw nope(); }
        @Override public org.springframework.data.domain.Page<Space> findAll(org.springframework.data.domain.Pageable pageable) { throw nope(); }
        @Override public void flush()                                                               { throw nope(); }
        @Override public <S extends Space> S saveAndFlush(S entity)                                 { throw nope(); }
        @Override public <S extends Space> java.util.List<S> saveAllAndFlush(Iterable<S> entities)  { throw nope(); }
        @Override public void deleteAllInBatch(Iterable<Space> entities)                            { throw nope(); }
        @Override public void deleteAllByIdInBatch(Iterable<String> strings)                        { throw nope(); }
        @Override public void deleteAllInBatch()                                                    { throw nope(); }
        @Override public Space getOne(String s)                                                     { throw nope(); }
        @Override public Space getById(String s)                                                    { throw nope(); }
        @Override public Space getReferenceById(String s)                                           { throw nope(); }
        @Override public <S extends Space> java.util.Optional<S> findOne(org.springframework.data.domain.Example<S> example)                                              { throw nope(); }
        @Override public <S extends Space> java.util.List<S>      findAll(org.springframework.data.domain.Example<S> example)                                             { throw nope(); }
        @Override public <S extends Space> java.util.List<S>      findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort)  { throw nope(); }
        @Override public <S extends Space> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { throw nope(); }
        @Override public <S extends Space> long count(org.springframework.data.domain.Example<S> example)      { throw nope(); }
        @Override public <S extends Space> boolean exists(org.springframework.data.domain.Example<S> example)  { throw nope(); }
        @Override public <S extends Space, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw nope(); }

        // Added by Task-3 (UC-01-space-analysis) — polling query on PENDING_ANALYSIS.
        @Override public java.util.List<Space> findTop10ByStatusOrderByUploadedAtAsc(Space.Status status) { throw nope(); }

        private static RuntimeException nope() { return new UnsupportedOperationException("not used in this test"); }
    }

    private static class StubUserRepo implements UserRepository {
        final Set<String> knownIds = new HashSet<>();

        @Override public boolean existsById(String s) { return knownIds.contains(s); }
        // --- rest unused ---
        @Override public <S extends com.authenticself.domain.User> S save(S entity)           { throw nope(); }
        @Override public <S extends com.authenticself.domain.User> java.util.List<S> saveAll(Iterable<S> entities) { throw nope(); }
        @Override public java.util.Optional<com.authenticself.domain.User> findById(String s) { throw nope(); }
        @Override public java.util.List<com.authenticself.domain.User> findAll()              { throw nope(); }
        @Override public java.util.List<com.authenticself.domain.User> findAll(org.springframework.data.domain.Sort sort) { throw nope(); }
        @Override public java.util.List<com.authenticself.domain.User> findAllById(Iterable<String> strings) { throw nope(); }
        @Override public long count()                                                         { throw nope(); }
        @Override public void deleteById(String s)                                            { throw nope(); }
        @Override public void delete(com.authenticself.domain.User entity)                    { throw nope(); }
        @Override public void deleteAllById(Iterable<? extends String> strings)               { throw nope(); }
        @Override public void deleteAll(Iterable<? extends com.authenticself.domain.User> entities) { throw nope(); }
        @Override public void deleteAll()                                                     { throw nope(); }
        @Override public org.springframework.data.domain.Page<com.authenticself.domain.User> findAll(org.springframework.data.domain.Pageable pageable) { throw nope(); }
        @Override public void flush()                                                         { throw nope(); }
        @Override public <S extends com.authenticself.domain.User> S saveAndFlush(S entity)   { throw nope(); }
        @Override public <S extends com.authenticself.domain.User> java.util.List<S> saveAllAndFlush(Iterable<S> entities) { throw nope(); }
        @Override public void deleteAllInBatch(Iterable<com.authenticself.domain.User> entities) { throw nope(); }
        @Override public void deleteAllByIdInBatch(Iterable<String> strings)                  { throw nope(); }
        @Override public void deleteAllInBatch()                                              { throw nope(); }
        @Override public com.authenticself.domain.User getOne(String s)                       { throw nope(); }
        @Override public com.authenticself.domain.User getById(String s)                      { throw nope(); }
        @Override public com.authenticself.domain.User getReferenceById(String s)             { throw nope(); }
        @Override public <S extends com.authenticself.domain.User> java.util.Optional<S> findOne(org.springframework.data.domain.Example<S> example) { throw nope(); }
        @Override public <S extends com.authenticself.domain.User> java.util.List<S>      findAll(org.springframework.data.domain.Example<S> example) { throw nope(); }
        @Override public <S extends com.authenticself.domain.User> java.util.List<S>      findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { throw nope(); }
        @Override public <S extends com.authenticself.domain.User> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { throw nope(); }
        @Override public <S extends com.authenticself.domain.User> long count(org.springframework.data.domain.Example<S> example)      { throw nope(); }
        @Override public <S extends com.authenticself.domain.User> boolean exists(org.springframework.data.domain.Example<S> example)  { throw nope(); }
        @Override public <S extends com.authenticself.domain.User, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw nope(); }

        private static RuntimeException nope() { return new UnsupportedOperationException("not used in this test"); }
    }

    // =================================================================
    // helpers — fabricate tiny real images ImageIO can decode
    // =================================================================
    private static byte[] fakeJpeg(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    private static byte[] fakePng(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
