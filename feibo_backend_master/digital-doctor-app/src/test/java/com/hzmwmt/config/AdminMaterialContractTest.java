package com.hzmwmt.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.controller.function.*;
import com.hzmwmt.domain.entity.function.*;
import com.hzmwmt.domain.handler.DigitalModelDataHandler;
import com.hzmwmt.domain.service.function.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminMaterialContractTest {
    private static MockMvc mvc(Object... controllers) {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return MockMvcBuilders.standaloneSetup(controllers)
                .setMessageConverters(new org.springframework.http.converter.StringHttpMessageConverter(java.nio.charset.StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter(json)).build();
    }

    @Test void musicUploadsInOneRequestAndNameUpdateCannotChangeOwner() throws Exception {
        AdminDigitalMusicService service = mock(AdminDigitalMusicService.class);
        AdminDigitalMusicController controller = new AdminDigitalMusicController();
        ReflectionTestUtils.setField(controller, "adminDigitalMusicService", service);
        when(service.saveWithFileUpload(any(), any())).thenReturn(true);
        when(service.updateById(any())).thenReturn(true);
        MockMvc http = mvc(controller);
        http.perform(multipart("/api/admin/function/music")
                        .file(new MockMultipartFile("file", "sample.mp3", "audio/mpeg", new byte[]{1,2}))
                        .param("title", "测试音乐").param("sort", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        http.perform(put("/api/admin/function/music").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":12,\"name\":\"新名称\",\"userId\":999,\"url\":\"https://bad.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        ArgumentCaptor<TDigitalMusic> write = ArgumentCaptor.forClass(TDigitalMusic.class);
        verify(service).updateById(write.capture());
        assertEquals("新名称", write.getValue().getName());
        assertNull(write.getValue().getUserId());
        assertNull(write.getValue().getUrl());
    }

    @Test void modelsUseNativeFieldsAndImportRawJsonOnlyOnce() throws Exception {
        AdminDigitalModelService service = mock(AdminDigitalModelService.class);
        DigitalModelDataHandler handler = mock(DigitalModelDataHandler.class);
        AdminDigitalModelController controller = new AdminDigitalModelController();
        ReflectionTestUtils.setField(controller,"adminDigitalModelService",service);
        ReflectionTestUtils.setField(controller,"dataHandler",handler);
        when(service.updateById(any())).thenReturn(true);
        MockMvc http=mvc(controller);
        http.perform(put("/api/admin/function/digital-model").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"17\",\"modelName\":\"模型\",\"sortNum\":4,\"status\":\"published\",\"isDeleted\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        ArgumentCaptor<TAdminDigitalModel> update=ArgumentCaptor.forClass(TAdminDigitalModel.class);
        verify(service).updateById(update.capture());
        assertEquals("模型",update.getValue().getModelName());
        assertEquals(4,update.getValue().getSortNum());
        assertEquals("published",update.getValue().getStatus());
        assertNull(update.getValue().getIsDeleted());
        String json="{\"data\":{\"records\":[{\"id\":\"17\",\"speakerName\":\"模型\",\"speakerId\":\"provider-17\"}]}}";
        http.perform(post("/api/admin/function/digital-model/upload-json").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        verify(handler,times(1)).parseAndSave(json);
        verify(service,never()).save(any());
    }

    @Test void voiceKeepsProviderIdPrecisionAndRejectsUrlOnlyPayload() throws Exception {
        AdminVoiceService service=mock(AdminVoiceService.class);
        AdminVoiceController controller=new AdminVoiceController();
        ReflectionTestUtils.setField(controller,"adminVoiceService",service);
        when(service.saveVoice(any())).thenReturn(true);
        MockMvc http=mvc(controller);
        http.perform(post("/api/admin/function/voice").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"男声\",\"digitalVoiceId\":\"9223372036854775800\",\"isDisplay\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        ArgumentCaptor<TDigitalVoice> capture=ArgumentCaptor.forClass(TDigitalVoice.class);
        verify(service).saveVoice(capture.capture());
        assertEquals(9223372036854775800L,capture.getValue().getDigitalVoiceId());
        assertFalse(capture.getValue().getIsDisplay());
        assertFalse(capture.getValue().getIsDelete());
        http.perform(post("/api/admin/function/voice").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"旧表单\",\"voiceUrl\":\"https://example.test/audio.mp3\"}"))
                .andExpect(status().isBadRequest());
        verify(service,times(1)).saveVoice(any());
    }

    @Test void voiceEditWritesOnlyAllowedFieldsAndReadsBooleanVisibility() throws Exception {
        AdminVoiceService service=mock(AdminVoiceService.class);
        AdminVoiceController controller=new AdminVoiceController();
        ReflectionTestUtils.setField(controller,"adminVoiceService",service);
        TDigitalVoice stored=new TDigitalVoice();
        stored.setId(18); stored.setName("旧名称"); stored.setDigitalVoiceId(123L);
        stored.setStyleName("旧风格"); stored.setGender(1); stored.setIsDisplay(true); stored.setIsDelete(false);
        stored.setCreateTime(java.time.LocalDateTime.of(2026,1,1,0,0));
        when(service.getVoiceById(18L)).thenReturn(stored);
        when(service.updateVoice(any())).thenAnswer(invocation -> {
            TDigitalVoice write=invocation.getArgument(0);
            stored.setName(write.getName()); stored.setDigitalVoiceId(write.getDigitalVoiceId());
            stored.setStyleName(write.getStyleName()); stored.setGender(write.getGender());
            stored.setIsDisplay(write.getIsDisplay()); stored.setUpdateTime(write.getUpdateTime());
            return true;
        });
        MockMvc http=mvc(controller);
        http.perform(put("/api/admin/function/voice").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"18\",\"name\":\" 新女声 \",\"digitalVoiceId\":\"9223372036854775800\",\"styleName\":\" 温和 \",\"gender\":2,\"isDisplay\":false,\"isDelete\":true,\"createTime\":\"2099-01-01T00:00:00\",\"updateTime\":\"2099-01-01T00:00:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").value(true));
        ArgumentCaptor<TDigitalVoice> write=ArgumentCaptor.forClass(TDigitalVoice.class);
        verify(service).updateVoice(write.capture());
        assertEquals(18,write.getValue().getId());
        assertEquals(9223372036854775800L,write.getValue().getDigitalVoiceId());
        assertNull(write.getValue().getIsDelete());
        assertNull(write.getValue().getCreateTime());
        assertTrue(write.getValue().getUpdateTime().getYear()<2099);
        http.perform(get("/api/admin/function/voice/18"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("新女声"))
                .andExpect(jsonPath("$.data.digitalVoiceId").value(9223372036854775800L))
                .andExpect(jsonPath("$.data.styleName").value("温和"))
                .andExpect(jsonPath("$.data.gender").value(2))
                .andExpect(jsonPath("$.data.isDisplay").isBoolean()).andExpect(jsonPath("$.data.isDisplay").value(false))
                .andExpect(jsonPath("$.data.isDelete").value(false));
        http.perform(put("/api/admin/function/voice").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"18\",\"name\":\"女声\",\"digitalVoiceId\":\"123\",\"styleName\":\"\",\"gender\":0,\"isDisplay\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        assertEquals("",stored.getStyleName());
        assertEquals(0,stored.getGender());
        assertTrue(stored.getIsDisplay());
        assertFalse(stored.getIsDelete());
    }

    @Test void voiceEditRejectsFailedMissingDeletedAndLegacyPayloads() throws Exception {
        AdminVoiceService service=mock(AdminVoiceService.class);
        AdminVoiceController controller=new AdminVoiceController();
        ReflectionTestUtils.setField(controller,"adminVoiceService",service);
        TDigitalVoice stored=new TDigitalVoice(); stored.setId(18); stored.setIsDelete(false);
        TDigitalVoice deleted=new TDigitalVoice(); deleted.setId(20); deleted.setIsDelete(true);
        when(service.getVoiceById(18L)).thenReturn(stored);
        when(service.getVoiceById(20L)).thenReturn(deleted);
        when(service.updateVoice(any())).thenReturn(false);
        MockMvc http=mvc(controller);
        String body="{\"id\":\"18\",\"name\":\"女声\",\"digitalVoiceId\":\"123\",\"styleName\":\"\",\"gender\":0,\"isDisplay\":false}";
        http.perform(put("/api/admin/function/voice").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500)).andExpect(jsonPath("$.data").doesNotExist());
        for(String id:java.util.List.of("19","20")) {
            http.perform(put("/api/admin/function/voice").contentType(MediaType.APPLICATION_JSON).content(body.replace("\"18\"","\""+id+"\"")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404));
        }
        http.perform(put("/api/admin/function/voice").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"18\",\"title\":\"旧表单\",\"voiceUrl\":\"https://example.test/audio.mp3\",\"isShow\":1}"))
                .andExpect(status().isBadRequest());
        verify(service,times(1)).updateVoice(any());
    }

    @Test void allSevenDeleteEndpointsReturnExplicitSavedState() throws Exception {
        AdminDigitalMusicService music=mock(AdminDigitalMusicService.class);
        AdminVideoService video=mock(AdminVideoService.class);
        TDigitalImageService images=mock(TDigitalImageService.class);
        AdminDigitalModelService model=mock(AdminDigitalModelService.class);
        AdminVoiceService voice=mock(AdminVoiceService.class);
        AdminCaseVideoService cases=mock(AdminCaseVideoService.class);
        AdminDigitalMusicController musicController=new AdminDigitalMusicController();
        AdminVideoController videoController=new AdminVideoController();
        AdminIllustrationController illustrationController=new AdminIllustrationController();
        AdminBackgroundImageController backgroundController=new AdminBackgroundImageController();
        AdminDigitalModelController modelController=new AdminDigitalModelController();
        AdminVoiceController voiceController=new AdminVoiceController();
        AdminCaseVideoController caseController=new AdminCaseVideoController();
        ReflectionTestUtils.setField(musicController,"adminDigitalMusicService",music);
        ReflectionTestUtils.setField(videoController,"adminVideoService",video);
        ReflectionTestUtils.setField(illustrationController,"digitalImageService",images);
        ReflectionTestUtils.setField(backgroundController,"digitalImageService",images);
        ReflectionTestUtils.setField(modelController,"adminDigitalModelService",model);
        ReflectionTestUtils.setField(voiceController,"adminVoiceService",voice);
        ReflectionTestUtils.setField(caseController,"adminCaseVideoService",cases);
        when(music.removeById(9L)).thenReturn(true,false);
        when(video.removeById(9L)).thenReturn(true,false);
        when(model.removeById(9L)).thenReturn(true,false);
        when(voice.removeVoice(9L)).thenReturn(true,false);
        when(cases.removeById(9L)).thenReturn(true,false);
        TDigitalImage illustration=new TDigitalImage(); illustration.setId(201L); illustration.setType(1);
        TDigitalImage background=new TDigitalImage(); background.setId(202L); background.setType(0);
        when(images.getById(201L)).thenReturn(illustration);
        when(images.getById(202L)).thenReturn(background);
        when(images.removeImagesByIds(java.util.List.of(201L))).thenReturn(true,false);
        when(images.removeImagesByIds(java.util.List.of(202L))).thenReturn(true,false);
        MockMvc http=mvc(musicController,videoController,illustrationController,backgroundController,modelController,voiceController,caseController);
        ObjectMapper json=new ObjectMapper();
        for(String path:java.util.List.of("music/9","video/9","illustration/201","background-image/202","digital-model/9","voice/9","case-video/9")) {
            var saved=json.readTree(http.perform(delete("/api/admin/function/"+path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var savedData=saved.has("code") ? saved.path("data") : saved;
            assertTrue(savedData.isBoolean() && savedData.booleanValue(),path+" must confirm a saved deletion");
            if(saved.has("code")) assertEquals(200,saved.path("code").asInt());
            var failed=json.readTree(http.perform(delete("/api/admin/function/"+path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var failedData=failed.has("code") ? failed.path("data") : failed;
            assertFalse(failedData.isBoolean() && failedData.booleanValue(),path+" must not claim a failed deletion succeeded");
        }
        verify(voice,times(2)).removeVoice(9L);
        verify(images,times(2)).removeImagesByIds(java.util.List.of(201L));
        verify(images,times(2)).removeImagesByIds(java.util.List.of(202L));
    }

    @Test void videoAndImageWritesExposeActualBooleanOrSavedRecordResponses() throws Exception {
        AdminVideoService videos=mock(AdminVideoService.class);
        TDigitalImageService images=mock(TDigitalImageService.class);
        AdminVideoController videoController=new AdminVideoController();
        AdminIllustrationController illustrationController=new AdminIllustrationController();
        AdminBackgroundImageController backgroundController=new AdminBackgroundImageController();
        ReflectionTestUtils.setField(videoController,"adminVideoService",videos);
        ReflectionTestUtils.setField(illustrationController,"digitalImageService",images);
        ReflectionTestUtils.setField(backgroundController,"digitalImageService",images);
        when(videos.saveWithFileUpload(any(),any(),any())).thenReturn(true);
        when(videos.updateById(any())).thenReturn(true);
        when(images.addImageWithFile(any(),any())).thenAnswer(invocation -> {
            TDigitalImage saved=invocation.getArgument(0); saved.setId(saved.getType()==1 ? 201L : 202L);
            saved.setUrl("https://example.test/image.png"); return true;
        });
        when(images.getById(anyLong())).thenAnswer(invocation -> {
            Long id=invocation.getArgument(0); TDigitalImage found=new TDigitalImage(); found.setId(id); found.setType(id==201L ? 1 : 0); return found;
        });
        when(images.updateById(any())).thenReturn(true);
        MockMvc http=mvc(videoController,illustrationController,backgroundController);
        http.perform(multipart("/api/admin/function/video")
                        .file(new MockMultipartFile("file","v.mp4","video/mp4",new byte[]{1}))
                        .file(new MockMultipartFile("coverFile","c.png","image/png",new byte[]{1})).param("title","视频"))
                .andExpect(status().isOk()).andExpect(content().string("true"));
        http.perform(put("/api/admin/function/video").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"9\",\"title\":\"改名\"}"))
                .andExpect(status().isOk()).andExpect(content().string("true"));
        for(String kind:java.util.List.of("illustration","background-image")) {
            String upload=kind.equals("illustration") ? "/add-with-image" : "/upload-and-save";
            int id=kind.equals("illustration") ? 201 : 202;
            http.perform(multipart("/api/admin/function/"+kind+upload)
                            .file(new MockMultipartFile("file","c.png","image/png",new byte[]{1})).param("title","图片"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.name").value("图片"));
            http.perform(put("/api/admin/function/"+kind).contentType(MediaType.APPLICATION_JSON).content("{\"id\":\""+id+"\",\"name\":\"改名\"}"))
                    .andExpect(status().isOk()).andExpect(content().string("true"));
        }
    }

    @Test void caseCreatePersistsSortVisibilityAndReplacementPreservesUnchangedFile() throws Exception {
        AdminCaseVideoService service=mock(AdminCaseVideoService.class);
        AdminCaseVideoController controller=new AdminCaseVideoController();
        ReflectionTestUtils.setField(controller,"adminCaseVideoService",service);
        when(service.addCaseVideoWithFiles(anyString(),any(),any(),any(),any(),anyInt(),anyInt())).thenReturn(true);
        when(service.getById(9L)).thenReturn(new TCaseVideo());
        when(service.uploadCoverImage(any())).thenReturn("https://example.test/new-cover.png");
        when(service.updateById(any())).thenReturn(true);
        MockMvc http=mvc(controller);
        http.perform(multipart("/api/admin/function/case-video/add-with-files")
                        .file(new MockMultipartFile("videoFile","v.mp4","video/mp4",new byte[]{1}))
                        .file(new MockMultipartFile("coverImage","c.png","image/png",new byte[]{1}))
                        .param("title","案例").param("sort","3").param("isShow","0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        verify(service).addCaseVideoWithFiles(eq("案例"),isNull(),isNull(),any(),any(),eq(3),eq(0));
        http.perform(multipart("/api/admin/function/case-video/update-with-files")
                        .file(new MockMultipartFile("coverImage","c.png","image/png",new byte[]{1}))
                        .param("id","9").param("title","新案例").param("scene","").param("sort","4").param("isShow","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        ArgumentCaptor<TCaseVideo> update=ArgumentCaptor.forClass(TCaseVideo.class);
        verify(service).updateById(update.capture());
        assertEquals("https://example.test/new-cover.png",update.getValue().getCoverUrl());
        assertNull(update.getValue().getVideoUrl());
        assertEquals("",update.getValue().getScene());
        assertEquals(4,update.getValue().getSort());
        verify(service,never()).uploadVideo(any());
    }

    @Test void backgroundPaginationForwardsActualDatesAndRestrictsRecordType() throws Exception {
        TDigitalImageService service=mock(TDigitalImageService.class);
        AdminBackgroundImageController controller=new AdminBackgroundImageController();
        ReflectionTestUtils.setField(controller,"digitalImageService",service);
        MockMvc http=mvc(controller);
        http.perform(get("/api/admin/function/background-image/page").param("pageNum","2").param("pageSize","5")
                        .param("title","背景").param("startDate","2026-09-01").param("endDate","2026-09-09"))
                .andExpect(status().isOk());
        verify(service).pageWithLike(2,5,"背景",0,java.time.LocalDate.of(2026,9,1),java.time.LocalDate.of(2026,9,9));
        TDigitalImage wrongType=new TDigitalImage(); wrongType.setId(8L);wrongType.setType(1);
        when(service.getById(8L)).thenReturn(wrongType);
        http.perform(put("/api/admin/function/background-image").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":8,\"name\":\"误改插图\"}"))
                .andExpect(status().isOk()).andExpect(content().string("false"));
        verify(service,never()).updateById(any());
    }
}
