package com.hzmwmt.domain.digital.service.file;

import com.hzmwmt.domain.digital.adapter.port.IDigitalPort;
import com.hzmwmt.domain.digital.adapter.repository.IDigitalRepository;
import com.hzmwmt.domain.digital.model.entity.DigitalAudioEntity;
import com.hzmwmt.domain.digital.model.entity.MultipleUploadEntity;
import com.hzmwmt.domain.digital.model.entity.SingleUploadEntity;
import com.hzmwmt.domain.digital.service.File.DigitalFileService;
import com.hzmwmt.domain.digital.service.File.factory.UploadStrategyFactory;
import com.hzmwmt.domain.digital.service.File.strategy.AudioUploadStrategy;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.hzmwmt.domain.digital.service.File.factory.UploadStrategyFactory.UploadTypeEnumVO.RECORDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AudioUploadPreflightTest {
    private final IDigitalPort port = mock(IDigitalPort.class);
    private final IDigitalRepository repository = mock(IDigitalRepository.class);
    private final AudioUploadStrategy strategy = new AudioUploadStrategy();
    private final DigitalFileService service = new DigitalFileService();

    @BeforeEach
    void wireRealAudioStrategy() {
        ReflectionTestUtils.setField(strategy, "digitalRepository", repository);
        UploadStrategyFactory factory = new UploadStrategyFactory();
        ReflectionTestUtils.setField(factory, "audioUploadStrategy", strategy);
        factory.init();
        ReflectionTestUtils.setField(service, "strategyFactory", factory);
        ReflectionTestUtils.setField(service, "digitalPort", port);
    }

    @Test
    void invalidSingleAudioFailsBeforeObjectUploadAndRecordSave() {
        SingleUploadEntity request = single(invalidAudio());
        assertThatThrownBy(() -> service.uploadSingleFile(request)).isInstanceOf(AppException.class);
        verifyNoInteractions(port, repository);
    }

    @Test
    void invalidMemberRejectsEntireBatchBeforeUploadingAnyObject() {
        MultipleUploadEntity request = batch(List.of(wav("valid.wav", 1), invalidAudio()));
        assertThatThrownBy(() -> service.uploadMultipleFiles(request)).isInstanceOf(AppException.class);
        verifyNoInteractions(port, repository);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void serverProbeOverridesAnyStaleDurationAndPersistsTrustedMetadata() {
        SingleUploadEntity request = single(wav("one-second.wav", 1));
        request.setAudioDuration(999);
        when(port.uploadSingle(any())).thenReturn("https://owned.example/one.wav");

        assertThat(service.uploadSingleFile(request)).isEqualTo("https://owned.example/one.wav");
        ArgumentCaptor<List<DigitalAudioEntity>> saved = ArgumentCaptor.forClass((Class) List.class);
        verify(repository).saveDigitalAudio(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(audio -> {
            assertThat(audio.getDuration()).isEqualTo(1);
            assertThat(audio.getUrl()).isEqualTo("https://owned.example/one.wav");
            assertThat(audio.getUserId()).isEqualTo(100L);
            assertThat(audio.getName()).isEqualTo("one-second.wav");
            assertThat(audio.getType()).isEqualTo(RECORDING.getCode());
        });
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void batchDurationsStayAlignedWithFilesAndUploadedUrls() {
        MultipleUploadEntity request = batch(List.of(wav("first.wav", 1), wav("second.wav", 2)));
        when(port.uploadMultiple(any())).thenReturn(List.of("https://owned.example/1.wav", "https://owned.example/2.wav"));
        assertThat(service.uploadMultipleFiles(request)).hasSize(2);
        ArgumentCaptor<List<DigitalAudioEntity>> saved = ArgumentCaptor.forClass((Class) List.class);
        verify(repository).saveDigitalAudio(saved.capture());
        assertThat(saved.getValue()).extracting(DigitalAudioEntity::getDuration).containsExactly(1, 2);
        assertThat(saved.getValue()).extracting(DigitalAudioEntity::getName).containsExactly("first.wav", "second.wav");
        assertThat(saved.getValue()).extracting(DigitalAudioEntity::getUrl).containsExactly("https://owned.example/1.wav", "https://owned.example/2.wav");
    }

    @Test
    void saveRejectsUnprobedOrInvalidDurations() {
        SingleUploadEntity request = single(wav("valid.wav", 1));
        assertThatThrownBy(() -> strategy.saveSingleFile(request)).isInstanceOf(AppException.class);
        request.setAudioDuration(-1);
        assertThatThrownBy(() -> strategy.saveSingleFile(request)).isInstanceOf(AppException.class);
        verifyNoInteractions(repository);
    }

    private static SingleUploadEntity single(MockMultipartFile file) {
        return SingleUploadEntity.builder().userId(100L).file(file).uploadType(RECORDING).build();
    }

    private static MultipleUploadEntity batch(List<org.springframework.web.multipart.MultipartFile> files) {
        MultipleUploadEntity entity = new MultipleUploadEntity();
        entity.setUserId(100L);
        entity.setUploadType(RECORDING);
        entity.setFiles(files);
        return entity;
    }

    private static MockMultipartFile invalidAudio() {
        return new MockMultipartFile("file", "fake.mp3", "audio/mpeg", "not an audio file".getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile wav(String name, int seconds) {
        int sampleRate = 8000;
        int dataSize = sampleRate * 2 * seconds;
        ByteBuffer bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataSize);
        bytes.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        bytes.putShort((short) 1).putShort((short) 1).putInt(sampleRate).putInt(sampleRate * 2);
        bytes.putShort((short) 2).putShort((short) 16);
        bytes.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize);
        return new MockMultipartFile("file", name, "audio/wav", bytes.array());
    }
}
