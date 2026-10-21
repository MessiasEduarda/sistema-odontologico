package br.com.odontosaas.clinical;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClinicalDtos {

    private ClinicalDtos() {}

    // ------------------------------------------------------------------ Anamnese

    /**
     * Pergunta do questionário. type: YES_NO, YES_NO_TEXT (sim/não + detalhe) ou TEXT. alertOnYes: resposta "sim"
     * gera alerta visível no prontuário.
     */
    public record Question(
            @NotBlank @Size(max = 40) @Pattern(regexp = "^[a-z0-9_]+$")
            String id,

            @NotBlank @Size(max = 60) String section,
            @NotBlank @Size(max = 300) String text,

            @NotBlank @Pattern(regexp = "YES_NO|YES_NO_TEXT|TEXT")
            String type,

            boolean alertOnYes) {}

    /** Ficha de anamnese. kind: DENTAL (odontológica), AESTHETIC (harmonização orofacial) ou OTHER. */
    public record FormView(
            UUID id, String name, String kind, List<Question> questions, boolean active, Instant updatedAt) {}

    public record FormRequest(
            @NotBlank @Size(max = 120) String name,

            @NotBlank @Pattern(regexp = "DENTAL|AESTHETIC|OTHER")
            String kind,

            @NotEmpty @Size(max = 80) List<@Valid Question> questions) {}

    /** Resposta: answer = "YES" | "NO" | null; detail = texto livre. */
    public record Answer(
            @Pattern(regexp = "YES|NO") String answer,
            @Size(max = 1000) String detail) {}

    /** formId nulo = ficha odontológica padrão da clínica. */
    public record AnamnesisRequest(
            UUID formId,
            @NotNull Map<String, @Valid Answer> answers,
            @Size(max = 4000) String notes,
            @Pattern(regexp = "PROFESSIONAL|PATIENT") String filledBy) {}

    public record Alert(String questionId, String text, String detail) {}

    public record AnamnesisView(
            UUID id,
            UUID formId,
            String formName,
            String formKind,
            List<Question> questions,
            Map<String, Answer> answers,
            List<Alert> alerts,
            String notes,
            String filledBy,
            String createdByName,
            Instant createdAt) {}

    /** Item da lista geral de anamneses da clínica (página Anamneses). */
    public record RecentAnamnesis(
            UUID id,
            UUID patientId,
            String patientName,
            String formName,
            String formKind,
            List<Alert> alerts,
            String filledBy,
            String createdByName,
            Instant createdAt) {}

    // ------------------------------------------------------------------ Odontograma

    public record ChartEntryRequest(
            @Min(11) @Max(85) int tooth,

            @Pattern(regexp = "^[MODVLPI]{1,6}$", message = "faces: M, O, D, V, L, P, I")
            String surfaces,

            @NotBlank String condition,

            @NotBlank @Pattern(regexp = "EXISTING|PLANNED|DONE")
            String status,

            @Size(max = 500) String notes) {}

    public record ChartEntryView(
            UUID id,
            int tooth,
            String surfaces,
            String condition,
            String status,
            String notes,
            String createdByName,
            Instant createdAt,
            Instant voidedAt,
            String voidReason) {}

    public record VoidRequest(@NotBlank @Size(max = 300) String reason) {}

    // ------------------------------------------------------------------ Evoluções, procedimentos, documentos

    public record NoteRequest(
            @NotBlank @Size(max = 20000) String content, UUID professionalId, UUID appointmentId, UUID amendsNoteId) {}

    public record NoteView(
            UUID id,
            String content,
            UUID professionalId,
            String professionalName,
            UUID appointmentId,
            UUID amendsNoteId,
            String createdByName,
            Instant createdAt) {}

    public record PerformedRequest(
            @NotNull UUID professionalId,
            UUID procedureId,
            @NotBlank @Size(max = 200) String description,
            @Min(11) @Max(85) Integer tooth,
            @Pattern(regexp = "^[MODVLPI]{1,6}$") String surfaces,
            UUID treatmentItemId,
            Instant performedAt,
            @Size(max = 1000) String notes) {}

    public record PerformedView(
            UUID id,
            UUID professionalId,
            String professionalName,
            UUID procedureId,
            String description,
            Integer tooth,
            String surfaces,
            UUID treatmentItemId,
            Instant performedAt,
            String notes,
            Instant voidedAt,
            String voidReason) {}

    public record DocumentRequest(
            @NotNull UUID professionalId,

            @NotBlank @Pattern(regexp = "PRESCRIPTION|CERTIFICATE|REFERRAL|INSTRUCTIONS|OTHER")
            String type,

            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 20000) String content) {}

    public record DocumentView(
            UUID id,
            UUID professionalId,
            String professionalName,
            String professionalCro,
            String type,
            String title,
            String content,
            Instant issuedAt) {}

    // ------------------------------------------------------------------ Resumo do prontuário

    public record RecordSummary(
            UUID patientId,
            String patientName,
            LocalDate birthDate,
            List<Alert> alerts,
            Instant lastAnamnesisAt,
            int notesCount,
            int pendingConsents,
            int attachmentsCount) {}

    // ------------------------------------------------------------------ Termos de consentimento

    /** Categoria: DENTAL, AESTHETIC (harmonização), IMAGE (uso de imagem), PRIVACY (LGPD) ou GENERAL. */
    public record ConsentTemplateView(
            UUID id,
            UUID familyId,
            String title,
            String category,
            String body,
            int version,
            boolean active,
            Instant createdAt) {}

    public record ConsentTemplateRequest(
            @NotBlank @Size(max = 160) String title,

            @Pattern(regexp = "DENTAL|AESTHETIC|IMAGE|PRIVACY|GENERAL")
            String category,

            @NotBlank @Size(max = 30000) String body) {}

    /** Item da lista geral de termos da clínica (página Termos). */
    public record ConsentListItem(
            UUID id,
            UUID patientId,
            String patientName,
            String title,
            String category,
            String status,
            String professionalName,
            String procedureDescription,
            Instant createdAt,
            Instant signedAt) {}

    public record ConsentRequest(
            @NotNull UUID templateId,
            UUID professionalId,
            @Size(max = 200) String procedureDescription) {}

    public record ConsentView(
            UUID id,
            String title,
            String category,
            String body,
            String bodySha256,
            Integer templateVersion,
            String procedureDescription,
            String professionalName,
            String status,
            Instant signedAt,
            String signerName,
            String signerDocument,
            String signatureImage,
            String signedIp,
            Instant revokedAt,
            String revokeReason,
            Instant createdAt) {}

    public record SignRequest(
            @NotBlank @Size(max = 160) String signerName,
            @Size(max = 20) String signerDocument,

            @NotBlank
            @Size(max = 400000)
            @Pattern(regexp = "^data:image/png;base64,[A-Za-z0-9+/=]+$", message = "assinatura inválida")
            String signatureImage,

            @AssertTrue(message = "é necessário declarar que leu e concorda com o termo")
            boolean accepted) {}

    public record ConsentLinkView(String url, Instant expiresAt) {}

    public record PublicConsentView(
            String clinicName, String patientName, String title, String body, String status, Instant signedAt) {}

    public record PublicSignRequest(
            @NotBlank @Size(max = 64) String token,
            @NotBlank @Size(max = 160) String signerName,
            @Size(max = 20) String signerDocument,

            @NotBlank
            @Size(max = 400000)
            @Pattern(regexp = "^data:image/png;base64,[A-Za-z0-9+/=]+$", message = "assinatura inválida")
            String signatureImage,

            @AssertTrue(message = "é necessário declarar que leu e concorda com o termo")
            boolean accepted) {}

    // ------------------------------------------------------------------ Anexos

    public record AttachmentView(
            UUID id,
            String category,
            String description,
            String filename,
            String contentType,
            long sizeBytes,
            String uploadedByName,
            Instant createdAt) {}

    // ------------------------------------------------------------------ Orientações e cuidados

    /** Modelo de orientação. moment: BEFORE (pré), AFTER (pós) ou GENERAL. */
    public record CareTemplateView(
            UUID id, String title, String category, String moment, String body, boolean active, Instant updatedAt) {}

    public record CareTemplateRequest(
            @NotBlank @Size(max = 160) String title,

            @NotBlank @Pattern(regexp = "DENTAL|AESTHETIC|GENERAL")
            String category,

            @NotBlank @Pattern(regexp = "BEFORE|AFTER|GENERAL")
            String moment,

            @NotBlank @Size(max = 20000) String body,
            Boolean active) {}

    /** Entrega de orientação ao paciente: vira documento do prontuário e, se pedido, é enviada por e-mail. */
    public record CareIssueRequest(
            @NotNull UUID professionalId,
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 20000) String content,
            boolean sendEmail) {}

    /** Documento emitido, com o paciente (lista geral da clínica). */
    public record IssuedDocument(
            UUID id,
            UUID patientId,
            String patientName,
            String professionalName,
            String type,
            String title,
            Instant issuedAt) {}
}
