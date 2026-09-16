package com.example.backend.controller;

import com.example.backend.config.AuthContextInterceptor;
import com.example.backend.service.AiEngineGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AiOrchestrationController {

    @Autowired
    private AiEngineGatewayService aiEngineGatewayService;

    @PostMapping("/agent/chat")
    public ResponseEntity<?> agentChat(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/agent/chat", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/plan/generate")
    public ResponseEntity<?> planGenerate(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.post("/ai/plan/generate", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/plan/parse")
    public ResponseEntity<?> planParse(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.post("/ai/plan/parse", withTeacherContext(payload, request), authToken(request));
    }

    @GetMapping("/plan/{planId}")
    public ResponseEntity<?> getPlan(@PathVariable("planId") String planId, HttpServletRequest request) {
        return aiEngineGatewayService.get("/ai/plan/" + segment(planId), authToken(request), null);
    }

    @PutMapping("/plan/{planId}")
    public ResponseEntity<?> putPlan(
            @PathVariable("planId") String planId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.put("/ai/plan/" + segment(planId), withTeacherContext(payload, request), authToken(request));
    }

    @PatchMapping("/plan/{planId}")
    public ResponseEntity<?> patchPlan(
            @PathVariable("planId") String planId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.patch("/ai/plan/" + segment(planId), withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/plan/{planId}/revise")
    public ResponseEntity<?> revisePlan(
            @PathVariable("planId") String planId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.post(
                "/ai/plan/" + segment(planId) + "/revise",
                withTeacherContext(payload, request),
                authToken(request)
        );
    }

    @PostMapping("/plan/{planId}/regenerate")
    public ResponseEntity<?> regeneratePlan(
            @PathVariable("planId") String planId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.post(
                "/ai/plan/" + segment(planId) + "/regenerate",
                withTeacherContext(payload, request),
                authToken(request)
        );
    }

    @GetMapping("/plan/{planId}/versions")
    public ResponseEntity<?> planVersions(@PathVariable("planId") String planId, HttpServletRequest request) {
        return aiEngineGatewayService.get("/ai/plan/" + segment(planId) + "/versions", authToken(request), null);
    }

    @PostMapping("/chat/respond")
    public ResponseEntity<?> chatRespond(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/chat/respond", withTeacherContext(payload, request), authToken(request));
    }

    @GetMapping("/model/backend/status")
    public ResponseEntity<?> modelBackendStatus(HttpServletRequest request) {
        return aiEngineGatewayService.get("/ai/model/backend/status", authToken(request), null);
    }

    @PostMapping("/model/backend/reload-local")
    public ResponseEntity<?> modelBackendReloadLocal(HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/model/backend/reload-local", authToken(request));
    }

    @PostMapping("/model/generate")
    public ResponseEntity<?> modelGenerate(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/model/generate", payload, authToken(request));
    }

    @GetMapping("/plan/conversation/{conversationKey}/latest")
    public ResponseEntity<?> latestPlanByConversation(
            @PathVariable("conversationKey") String conversationKey,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.get(
                "/ai/plan/conversation/" + segment(conversationKey) + "/latest",
                authToken(request),
                null
        );
    }

    @PostMapping("/plan/extract")
    public ResponseEntity<?> planExtract(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/plan/extract", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/plan/link_sources")
    public ResponseEntity<?> planLinkSources(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/plan/link_sources", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/plan/{planId}/auto_link_sources")
    public ResponseEntity<?> autoLinkSources(
            @PathVariable("planId") String planId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.post(
                "/ai/plan/" + segment(planId) + "/auto_link_sources",
                withTeacherContext(payload, request),
                authToken(request)
        );
    }

    @PostMapping("/kb/retrieve")
    public ResponseEntity<?> kbRetrieve(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/kb/retrieve", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/kb/ingest")
    public ResponseEntity<?> kbIngest(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/kb/ingest", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/kb/search")
    public ResponseEntity<?> kbSearch(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/kb/search", withTeacherContext(payload, request), authToken(request));
    }

    @GetMapping("/kb/docs")
    public ResponseEntity<?> kbDocs(HttpServletRequest request) {
        return aiEngineGatewayService.get("/ai/kb/docs", authToken(request), null);
    }

    @GetMapping("/kb/doc/{docId}/outline")
    public ResponseEntity<?> kbDocOutline(@PathVariable("docId") String docId, HttpServletRequest request) {
        return aiEngineGatewayService.get("/ai/kb/doc/" + segment(docId) + "/outline", authToken(request), null);
    }

    @PostMapping("/kb/upload")
    public ResponseEntity<?> kbUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "teacherId", required = false) String teacherId,
            @RequestParam(value = "teacher_id", required = false) String teacherIdSnake,
            @RequestParam(value = "kb_scope", required = false) String kbScope,
            @RequestParam(value = "conversation_key", required = false) String conversationKey,
            @RequestParam(value = "subject_tag", required = false) String subjectTag,
            @RequestParam(value = "grade_tag", required = false) String gradeTag,
            HttpServletRequest request
    ) {
        Map<String, String> formFields = new LinkedHashMap<>();
        putIfText(formFields, "kb_scope", kbScope);
        putIfText(formFields, "conversation_key", conversationKey);
        putIfText(formFields, "subject_tag", subjectTag);
        putIfText(formFields, "grade_tag", gradeTag);

        String scopedTeacherId = firstNonBlank(teacherId, teacherIdSnake, teacherIdFromRequest(request));
        putIfText(formFields, "teacher_id", scopedTeacherId);
        return aiEngineGatewayService.postMultipart("/ai/kb/upload", file, formFields, authToken(request));
    }

    @GetMapping("/kb/documents/{documentId}")
    public ResponseEntity<?> kbDocumentDetail(@PathVariable("documentId") String documentId, HttpServletRequest request) {
        return aiEngineGatewayService.get("/ai/kb/documents/" + segment(documentId), authToken(request), null);
    }

    @GetMapping("/kb/chunks/{chunkId}")
    public ResponseEntity<?> kbChunkDetail(@PathVariable("chunkId") String chunkId, HttpServletRequest request) {
        return aiEngineGatewayService.get("/ai/kb/chunks/" + segment(chunkId), authToken(request), null);
    }

    @PostMapping("/tasks")
    public ResponseEntity<?> createTask(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/tasks", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/tasks/image")
    public ResponseEntity<?> createImageTask(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/tasks/image", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/tasks/micro-lesson")
    public ResponseEntity<?> createMicroLessonTask(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/tasks/micro-lesson", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/tasks/render-ppt")
    public ResponseEntity<?> createRenderPptTask(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/tasks/render-ppt", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/tasks/render-docx")
    public ResponseEntity<?> createRenderDocxTask(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/tasks/render-docx", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/tasks/multi-agent-workflow")
    public ResponseEntity<?> createMultiAgentWorkflowTask(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/tasks/multi-agent-workflow", withTeacherContext(payload, request), authToken(request));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getTask(@PathVariable("taskId") String taskId, HttpServletRequest request) {
        return aiEngineGatewayService.get("/ai/tasks/" + segment(taskId), authToken(request), null);
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public ResponseEntity<?> cancelTask(@PathVariable("taskId") String taskId, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/tasks/" + segment(taskId) + "/cancel", authToken(request));
    }

    @GetMapping(value = "/tasks/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTask(@PathVariable("taskId") String taskId, HttpServletRequest request) {
        return aiEngineGatewayService.proxyTaskStream(taskId, authToken(request));
    }

    @GetMapping("/artifacts")
    public ResponseEntity<?> listArtifacts(
            @RequestParam(value = "planId", required = false) String planId,
            @RequestParam(value = "teacherId", required = false) String teacherId,
            HttpServletRequest request
    ) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (planId != null && !planId.trim().isEmpty()) {
            query.put("planId", planId.trim());
        }

        String scopedTeacherId = (teacherId != null && !teacherId.trim().isEmpty())
                ? teacherId.trim()
                : teacherIdFromRequest(request);
        if (scopedTeacherId != null && !scopedTeacherId.isEmpty()) {
            query.put("teacherId", scopedTeacherId);
        }
        return aiEngineGatewayService.get("/ai/artifacts", authToken(request), query);
    }

    @GetMapping("/artifacts/{artifactId}")
    public ResponseEntity<?> getArtifact(@PathVariable("artifactId") String artifactId, HttpServletRequest request) {
        return aiEngineGatewayService.get("/ai/artifacts/" + segment(artifactId), authToken(request), null);
    }

    @GetMapping("/artifact/ppt/conversation/{conversationKey}")
    public ResponseEntity<?> listPptArtifactsByConversation(
            @PathVariable("conversationKey") String conversationKey,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.get(
                "/ai/artifact/ppt/conversation/" + segment(conversationKey),
                authToken(request),
                null
        );
    }

    @GetMapping("/artifact/ppt/conversation/{conversationKey}/latest")
    public ResponseEntity<?> latestPptArtifactByConversation(
            @PathVariable("conversationKey") String conversationKey,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.get(
                "/ai/artifact/ppt/conversation/" + segment(conversationKey) + "/latest",
                authToken(request),
                null
        );
    }

    @GetMapping("/artifact/{artifactId}")
    public ResponseEntity<?> getLegacyArtifactDetail(@PathVariable("artifactId") String artifactId, HttpServletRequest request) {
        return aiEngineGatewayService.get("/ai/artifact/" + segment(artifactId), authToken(request), null);
    }

    @GetMapping("/artifact/{artifactId}/preview/{pageNumber}")
    public ResponseEntity<?> getLegacyArtifactPreview(
            @PathVariable("artifactId") String artifactId,
            @PathVariable("pageNumber") String pageNumber,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.getRaw(
                "/ai/artifact/" + segment(artifactId) + "/preview/" + segment(pageNumber),
                authToken(request),
                null
        );
    }

    @PostMapping("/artifacts/{artifactId}/regenerate")
    public ResponseEntity<?> regenerateArtifact(
            @PathVariable("artifactId") String artifactId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.post(
                "/ai/artifacts/" + segment(artifactId) + "/regenerate",
                withTeacherContext(payload, request),
                authToken(request)
        );
    }

    @PostMapping("/generate_word")
    public ResponseEntity<?> generateWord(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.postForBinary("/ai/generate_word", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/generate/lesson-plan")
    public ResponseEntity<?> generateLessonPlan(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/generate/lesson-plan", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/generate/ppt-spec")
    public ResponseEntity<?> generatePptSpec(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/generate/ppt-spec", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/generate/review-outline")
    public ResponseEntity<?> generateReviewOutline(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/generate/review-outline", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/generate/quiz")
    public ResponseEntity<?> generateQuiz(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/generate/quiz", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/generate/flashcards")
    public ResponseEntity<?> generateFlashcards(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/generate/flashcards", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/generate/micro-lesson-script")
    public ResponseEntity<?> generateMicroLessonScript(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/generate/micro-lesson-script", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/generate/image-prompt")
    public ResponseEntity<?> generateImagePrompt(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/generate/image-prompt", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/generate/game-spec")
    public ResponseEntity<?> generateGameSpec(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/generate/game-spec", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/generate/key-points")
    public ResponseEntity<?> generateKeyPoints(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/generate/key-points", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/generate_doc")
    public ResponseEntity<?> generateDoc(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.postForBinary("/ai/generate_doc", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/edit_ppt")
    public ResponseEntity<?> editPpt(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.postForBinary("/ai/edit_ppt", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/generate_game")
    public ResponseEntity<?> generateGame(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/generate_game", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/edit_video")
    public ResponseEntity<?> editVideo(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/edit_video", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/video/export")
    public ResponseEntity<?> exportVideo(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/video/export", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/video/preview_audio")
    public ResponseEntity<?> previewVideoAudio(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/video/preview_audio", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/generate_word_task")
    public ResponseEntity<?> generateWordTask(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/generate_word_task", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/generate_ppt_task")
    public ResponseEntity<?> generatePptTask(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/generate_ppt_task", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/workflow/teaching-package")
    public ResponseEntity<?> workflowTeachingPackage(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/workflow/teaching-package", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/workflow/multi-agent")
    public ResponseEntity<?> workflowMultiAgent(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/workflow/multi-agent", withTeacherContext(payload, request), authToken(request));
    }

    @PostMapping("/course/context/bootstrap")
    public ResponseEntity<?> bootstrapCourse(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/course/context/bootstrap", payload, authToken(request));
    }

    @PostMapping("/courseware/parse")
    public ResponseEntity<?> parseCourseware(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "courseId", required = false) String courseId,
            @RequestParam(value = "course_id", required = false) String courseIdSnake,
            HttpServletRequest request
    ) {
        Map<String, String> formFields = new LinkedHashMap<>();
        putIfText(formFields, "course_id", firstNonBlank(courseId, courseIdSnake));
        return aiEngineGatewayService.postMultipart("/ai/courseware/parse", file, formFields, authToken(request));
    }

    @GetMapping("/courseware/{assetId}/outline")
    public ResponseEntity<?> getCoursewareOutline(@PathVariable("assetId") String assetId, HttpServletRequest request) {
        return aiEngineGatewayService.get("/ai/courseware/" + segment(assetId) + "/outline", authToken(request), null);
    }

    @PostMapping("/lecture/generate")
    public ResponseEntity<?> generateLecture(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/lecture/generate", payload, authToken(request));
    }

    @PatchMapping("/lecture/{lectureId}/script")
    public ResponseEntity<?> patchLectureScript(
            @PathVariable("lectureId") String lectureId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.patch("/ai/lecture/" + segment(lectureId) + "/script", payload, authToken(request));
    }

    @GetMapping("/lecture/{lectureId}")
    public ResponseEntity<?> getLecture(@PathVariable("lectureId") String lectureId, HttpServletRequest request) {
        return aiEngineGatewayService.get("/ai/lecture/" + segment(lectureId), authToken(request), null);
    }

    @PostMapping("/lecture/{lectureId}/start")
    public ResponseEntity<?> startLecture(
            @PathVariable("lectureId") String lectureId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.post("/ai/lecture/" + segment(lectureId) + "/start", payload, authToken(request));
    }

    @GetMapping("/lecture/{lectureId}/progress")
    public ResponseEntity<?> lectureProgress(
            @PathVariable("lectureId") String lectureId,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "session_id", required = false) String sessionIdSnake,
            HttpServletRequest request
    ) {
        Map<String, Object> query = new LinkedHashMap<>();
        String resolvedSessionId = firstNonBlank(sessionId, sessionIdSnake);
        if (resolvedSessionId != null && !resolvedSessionId.isEmpty()) {
            query.put("sessionId", resolvedSessionId);
        }
        return aiEngineGatewayService.get("/ai/lecture/" + segment(lectureId) + "/progress", authToken(request), query);
    }

    @PostMapping("/lecture/{lectureId}/ask")
    public ResponseEntity<?> lectureAsk(
            @PathVariable("lectureId") String lectureId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.post("/ai/lecture/" + segment(lectureId) + "/ask", payload, authToken(request));
    }

    @PostMapping("/lecture/{lectureId}/resume")
    public ResponseEntity<?> lectureResume(
            @PathVariable("lectureId") String lectureId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.post("/ai/lecture/" + segment(lectureId) + "/resume", payload, authToken(request));
    }

    @PostMapping("/lecture/{lectureId}/feedback")
    public ResponseEntity<?> lectureFeedback(
            @PathVariable("lectureId") String lectureId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.post("/ai/lecture/" + segment(lectureId) + "/feedback", payload, authToken(request));
    }

    @PostMapping("/artifact/ppt/export")
    public ResponseEntity<?> exportLecturePpt(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.postForBinary("/ai/artifact/ppt/export", payload, authToken(request));
    }

    @PostMapping("/artifact/doc/export")
    public ResponseEntity<?> exportLectureDoc(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.postForBinary("/ai/artifact/doc/export", payload, authToken(request));
    }

    @GetMapping("/training/reports/objective-trace")
    public ResponseEntity<?> trainingObjectiveTraceReport(
            @RequestParam(value = "outputRoot", required = false) String outputRoot,
            @RequestParam(value = "output_root", required = false) String outputRootSnake,
            HttpServletRequest request
    ) {
        Map<String, Object> query = new LinkedHashMap<>();
        String resolvedOutputRoot = firstNonBlank(outputRoot, outputRootSnake);
        if (resolvedOutputRoot != null && !resolvedOutputRoot.isEmpty()) {
            query.put("outputRoot", resolvedOutputRoot);
        }
        return aiEngineGatewayService.get("/ai/training/reports/objective-trace", authToken(request), query);
    }

    @GetMapping("/training/reports/experiment-design")
    public ResponseEntity<?> trainingExperimentDesignReport(
            @RequestParam(value = "outputRoot", required = false) String outputRoot,
            @RequestParam(value = "output_root", required = false) String outputRootSnake,
            HttpServletRequest request
    ) {
        Map<String, Object> query = new LinkedHashMap<>();
        String resolvedOutputRoot = firstNonBlank(outputRoot, outputRootSnake);
        if (resolvedOutputRoot != null && !resolvedOutputRoot.isEmpty()) {
            query.put("outputRoot", resolvedOutputRoot);
        }
        return aiEngineGatewayService.get("/ai/training/reports/experiment-design", authToken(request), query);
    }

    @GetMapping("/training/reports/experiment-eval-metrics")
    public ResponseEntity<?> trainingExperimentEvalMetricsReport(
            @RequestParam(value = "outputRoot", required = false) String outputRoot,
            @RequestParam(value = "output_root", required = false) String outputRootSnake,
            HttpServletRequest request
    ) {
        Map<String, Object> query = new LinkedHashMap<>();
        String resolvedOutputRoot = firstNonBlank(outputRoot, outputRootSnake);
        if (resolvedOutputRoot != null && !resolvedOutputRoot.isEmpty()) {
            query.put("outputRoot", resolvedOutputRoot);
        }
        return aiEngineGatewayService.get("/ai/training/reports/experiment-eval-metrics", authToken(request), query);
    }

    private Map<String, Object> withTeacherContext(Map<String, Object> payload, HttpServletRequest request) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (payload != null) {
            merged.putAll(payload);
        }

        if (!hasTeacherField(merged)) {
            String teacherId = teacherIdFromRequest(request);
            if (teacherId != null && !teacherId.isEmpty()) {
                merged.put("teacherId", teacherId);
            }
        }
        return merged;
    }

    private boolean hasTeacherField(Map<String, Object> payload) {
        Object camel = payload.get("teacherId");
        if (camel != null && !String.valueOf(camel).trim().isEmpty()) {
            return true;
        }
        Object snake = payload.get("teacher_id");
        return snake != null && !String.valueOf(snake).trim().isEmpty();
    }

    private String teacherIdFromRequest(HttpServletRequest request) {
        Object teacherId = request.getAttribute(AuthContextInterceptor.ATTR_TEACHER_ID);
        if (teacherId instanceof Number number) {
            return String.valueOf(number.intValue());
        }
        if (teacherId == null) {
            return null;
        }
        String text = String.valueOf(teacherId).trim();
        return text.isEmpty() ? null : text;
    }

    private String authToken(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthContextInterceptor.ATTR_AUTH_HEADER);
        if (attr != null) {
            String text = String.valueOf(attr).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.trim().isEmpty()) {
            return null;
        }
        return header.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String text = value.trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private void putIfText(Map<String, String> target, String key, String value) {
        if (target == null || key == null) {
            return;
        }
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return;
        }
        target.put(key, text);
    }

    private String segment(String value) {
        return UriUtils.encodePathSegment(String.valueOf(value), StandardCharsets.UTF_8);
    }
}
