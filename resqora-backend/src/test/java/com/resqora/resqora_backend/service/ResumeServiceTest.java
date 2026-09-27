package com.resqora.resqora_backend.service;

import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.exception.ResumeNotFoundException;
import com.resqora.resqora_backend.model.analysis.AnalysisResult;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.analysis.ResumeAnalyzer;
import com.resqora.resqora_backend.service.extraction.DocumentTextExtractionService;
import com.resqora.resqora_backend.service.parser.ResumeParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeServiceTest {
    @Test
    void savesExtractedResumeForAuthenticatedUser() {
        DocumentTextExtractionService extractionService = mock(DocumentTextExtractionService.class);
        ResumeRepository resumeRepository = mock(ResumeRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ResumeParser resumeParser = mock(ResumeParser.class);
        ResumeAnalyzer resumeAnalyzer = mock(ResumeAnalyzer.class);
        User user = mock(User.class);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(extractionService.extract(any())).thenReturn("Extracted resume text");
        when(resumeRepository.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResumeService resumeService = new ResumeService(
                extractionService, resumeRepository, userRepository, resumeParser, resumeAnalyzer);
        Authentication authentication = authenticationFor(7L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "document".getBytes());

        resumeService.uploadResume(file, authentication);

        var resumeCaptor = org.mockito.ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).save(resumeCaptor.capture());
        Resume savedResume = resumeCaptor.getValue();
        assertEquals(user, savedResume.getUser());
        assertEquals("resume.docx", savedResume.getOriginalFileName());
        assertEquals("DOCX", savedResume.getDocumentType());
        assertEquals("Extracted resume text", savedResume.getExtractedText());
    }

    @Test
    void parsesAuthenticatedUsersOwnResume() {
        DocumentTextExtractionService extractionService = mock(DocumentTextExtractionService.class);
        ResumeRepository resumeRepository = mock(ResumeRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ResumeParser resumeParser = mock(ResumeParser.class);
        ResumeAnalyzer resumeAnalyzer = mock(ResumeAnalyzer.class);
        User user = mock(User.class);
        Resume resume = mock(Resume.class);
        ParsedResume parsedResume = new ParsedResume(
                "Alex Johnson", "alex@example.com", null, null,
                List.of("Java"), List.of(), List.of(), List.of(), List.of());
        when(resumeRepository.findById(9L)).thenReturn(Optional.of(resume));
        when(resume.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(7L);
        when(resume.getExtractedText()).thenReturn("stored resume text");
        when(resumeParser.parse("stored resume text")).thenReturn(parsedResume);

        ResumeService resumeService = new ResumeService(
                extractionService, resumeRepository, userRepository, resumeParser, resumeAnalyzer);

        assertEquals(parsedResume, resumeService.getParsedResume(9L, authenticationFor(7L)));
        verify(resumeParser).parse("stored resume text");
    }

    @Test
    void analyzesAuthenticatedUsersOwnResume() {
        DocumentTextExtractionService extractionService = mock(DocumentTextExtractionService.class);
        ResumeRepository resumeRepository = mock(ResumeRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ResumeParser resumeParser = mock(ResumeParser.class);
        ResumeAnalyzer resumeAnalyzer = mock(ResumeAnalyzer.class);
        User user = mock(User.class);
        Resume resume = mock(Resume.class);
        ParsedResume parsedResume = new ParsedResume(
                "Alex Johnson", "alex@example.com", null, null,
                List.of(), List.of(), List.of(), List.of(), List.of());
        AnalysisResult analysisResult = new AnalysisResult(10, 4, 0, 0, 10, 0, List.of());
        when(resumeRepository.findById(9L)).thenReturn(Optional.of(resume));
        when(resume.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(7L);
        when(resume.getExtractedText()).thenReturn("stored resume text");
        when(resumeParser.parse("stored resume text")).thenReturn(parsedResume);
        when(resumeAnalyzer.analyze(parsedResume)).thenReturn(analysisResult);

        ResumeService resumeService = new ResumeService(
                extractionService, resumeRepository, userRepository, resumeParser, resumeAnalyzer);

        assertEquals(analysisResult, resumeService.getAnalysis(9L, authenticationFor(7L)));
        verify(resumeParser).parse("stored resume text");
        verify(resumeAnalyzer).analyze(parsedResume);
    }

    @Test
    void deniesAnotherUsersResumeWithoutParsingIt() {
        DocumentTextExtractionService extractionService = mock(DocumentTextExtractionService.class);
        ResumeRepository resumeRepository = mock(ResumeRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ResumeParser resumeParser = mock(ResumeParser.class);
        ResumeAnalyzer resumeAnalyzer = mock(ResumeAnalyzer.class);
        User owner = mock(User.class);
        Resume resume = mock(Resume.class);
        when(resumeRepository.findById(9L)).thenReturn(Optional.of(resume));
        when(resume.getUser()).thenReturn(owner);
        when(owner.getId()).thenReturn(99L);

        ResumeService resumeService = new ResumeService(
                extractionService, resumeRepository, userRepository, resumeParser, resumeAnalyzer);

        assertThrows(ResumeNotFoundException.class,
                () -> resumeService.getParsedResume(9L, authenticationFor(7L)));
        org.mockito.Mockito.verifyNoInteractions(resumeParser);
    }

    @Test
    void returnsNotFoundWhenResumeDoesNotExist() {
        DocumentTextExtractionService extractionService = mock(DocumentTextExtractionService.class);
        ResumeRepository resumeRepository = mock(ResumeRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ResumeParser resumeParser = mock(ResumeParser.class);
        ResumeAnalyzer resumeAnalyzer = mock(ResumeAnalyzer.class);
        when(resumeRepository.findById(9L)).thenReturn(Optional.empty());

        ResumeService resumeService = new ResumeService(
                extractionService, resumeRepository, userRepository, resumeParser, resumeAnalyzer);

        assertThrows(ResumeNotFoundException.class,
                () -> resumeService.getParsedResume(9L, authenticationFor(7L)));
        org.mockito.Mockito.verifyNoInteractions(resumeParser);
    }

    private Authentication authenticationFor(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
