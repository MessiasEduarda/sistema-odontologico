package br.com.odontosaas.platform;

import static br.com.odontosaas.support.Browser.json;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Logo da clínica: tipo validado pelo conteúdo, limite de tamanho, isolamento entre clínicas e permissão. */
class ClinicLogoTest extends PostgresIntegrationTest {

    /** PNG 1x1 válido. */
    static final byte[] PNG = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired
    TestData data;

    UUID clinic;
    Browser owner;

    @BeforeEach
    void setUp() {
        clinic = data.clinic();
        owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
    }

    private MvcTestResult upload(Browser who, String name, byte[] content) {
        String csrf = "csrf-logo";
        return mvc.put()
                .uri("/api/v1/clinic/logo")
                .multipart()
                .file(new MockMultipartFile("file", name, "image/png", content))
                .cookie(new Cookie("XSRF-TOKEN", csrf), new Cookie(Browser.SESSION_COOKIE, who.session()))
                .header("X-XSRF-TOKEN", csrf)
                .exchange();
    }

    @Test
    void ownerUploadsLogoAndTeamSeesIt() {
        assertThat(owner.get("/api/v1/clinic/logo")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat((Object) json(owner.get("/api/v1/clinic/settings"), "$.logoUpdatedAt"))
                .isNull();

        assertThat(upload(owner, "logo.png", PNG)).hasStatusOk();
        MvcTestResult logo = owner.get("/api/v1/clinic/logo");
        assertThat(logo).hasStatusOk().hasContentType("image/png");
        assertThat(logo.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat((String) json(owner.get("/api/v1/clinic/settings"), "$.logoUpdatedAt"))
                .isNotBlank();

        Browser dentist = browser().login(data.member(clinic, "DENTIST").email(), TestData.PASSWORD);
        assertThat(dentist.get("/api/v1/clinic/logo")).hasStatusOk();
        assertThat(upload(dentist, "logo.png", PNG)).hasStatus(HttpStatus.FORBIDDEN);

        Browser other = browser().login(data.member(data.clinic(), "OWNER").email(), TestData.PASSWORD);
        assertThat(other.get("/api/v1/clinic/logo")).hasStatus(HttpStatus.NOT_FOUND);

        assertThat(owner.delete("/api/v1/clinic/logo")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(owner.get("/api/v1/clinic/logo")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsNonImagesAndLargeFiles() {
        // Extensão .png com conteúdo SVG (pode conter script): recusado pelo conteúdo.
        assertThat(upload(owner, "logo.png", "<svg onload=alert(1)></svg>".getBytes()))
                .hasStatus(HttpStatus.BAD_REQUEST);
        // Somente PNG: um JPG verdadeiro também é recusado.
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
        assertThat(upload(owner, "logo.jpg", jpeg)).hasStatus(HttpStatus.BAD_REQUEST);
        byte[] big = new byte[1024 * 1024 + 10];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThat(upload(owner, "grande.png", big)).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
