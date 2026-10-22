package br.com.odontosaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import br.com.odontosaas.support.PostgresIntegrationTest;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fotografia do contrato público da API ("golden files" em {@code src/test/resources/golden}). A reestruturação de
 * pacotes não pode mudar nenhuma rota, permissão, schema do OpenAPI ou listener de evento: se mudar, este teste falha.
 *
 * <p>Para regravar de propósito (mudança de contrato intencional): {@code ./mvnw test -Dgolden.update=true}.
 * Nomes de classe e pacote ficam de fora de propósito, porque é justamente isso que a reestruturação muda.
 */
class ApiContractGoldenTest extends PostgresIntegrationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Autowired
    ApplicationContext context;

    @Autowired
    JsonMapper json;

    @Test
    void endpointInventoryIsUnchanged() {
        List<String> lines = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            if (!handler.getBeanType().getName().startsWith("br.com.odontosaas")) return;
            Method method = handler.getMethod();
            PreAuthorize auth = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
            if (auth == null)
                auth = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
            TreeSet<String> verbs = new TreeSet<>();
            for (RequestMethod m : info.getMethodsCondition().getMethods()) verbs.add(m.name());
            TreeSet<String> paths =
                    new TreeSet<>(info.getPathPatternsCondition().getPatternValues());
            TreeSet<String> consumes = new TreeSet<>();
            info.getConsumesCondition().getExpressions().forEach(e -> consumes.add(e.toString()));
            TreeSet<String> produces = new TreeSet<>();
            info.getProducesCondition().getExpressions().forEach(e -> produces.add(e.toString()));
            List<String> params = new ArrayList<>();
            for (Parameter p : method.getParameters())
                params.add(simplify(p.getParameterizedType().getTypeName()));
            lines.add("%s %s | consumes=%s | produces=%s | auth=%s | %s(%s) -> %s"
                    .formatted(
                            verbs,
                            paths,
                            consumes,
                            produces,
                            auth == null ? "-" : auth.value(),
                            method.getName(),
                            String.join(", ", params),
                            simplify(method.getGenericReturnType().getTypeName())));
        });
        lines.sort(String::compareTo);
        assertGolden("endpoints.txt", String.join("\n", lines) + "\n");
    }

    @Test
    void openApiDocumentIsUnchanged() throws Exception {
        String body = mvc.get().uri("/v3/api-docs").exchange().getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = json.readValue(body, Map.class);
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("paths", sorted(doc.get("paths")));
        Object components = doc.get("components");
        if (components instanceof Map<?, ?> c) canonical.put("schemas", sorted(c.get("schemas")));
        canonical.put("tags", sorted(doc.get("tags")));
        assertGolden("openapi.json", json.writerWithDefaultPrettyPrinter().writeValueAsString(canonical) + "\n");
    }

    @Test
    void eventListenersAndScheduledMethodsAreUnchanged() {
        List<String> lines = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null) continue;
            Class<?> user = ClassUtils.getUserClass(type);
            if (!user.getName().startsWith("br.com.odontosaas")) continue;
            for (Method m : user.getDeclaredMethods()) {
                if (m.isAnnotationPresent(EventListener.class)) {
                    lines.add("EVENT %s#%s(%s)".formatted(user.getSimpleName(), m.getName(), paramTypes(m)));
                }
                if (m.isAnnotationPresent(Scheduled.class)) {
                    lines.add("SCHEDULED %s#%s".formatted(user.getSimpleName(), m.getName()));
                }
            }
        }
        lines.sort(String::compareTo);
        assertGolden("listeners.txt", String.join("\n", lines) + "\n");
    }

    private static String paramTypes(Method m) {
        List<String> params = new ArrayList<>();
        for (Class<?> p : m.getParameterTypes()) params.add(p.getSimpleName());
        return String.join(", ", params);
    }

    /** {@code a.b.Outer$Inner<c.D>} vira {@code Inner<D>}: o nome simples não muda quando o record sai da classe. */
    private static String simplify(String typeName) {
        return typeName.replaceAll("[\\w.$]+[.$]([A-Za-z_]\\w*)", "$1");
    }

    /** Ordena chaves recursivamente e descarta {@code operationId} (o sufixo numérico depende da ordem de descoberta). */
    private static Object sorted(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new TreeMap<>();
            map.forEach((k, v) -> {
                if (!"operationId".equals(k)) out.put(String.valueOf(k), sorted(v));
            });
            return out;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object o : list) out.add(sorted(o));
            if (!out.isEmpty() && out.stream().allMatch(o -> o instanceof Map<?, ?> m && m.containsKey("name"))) {
                out.sort((a, b) -> String.valueOf(((Map<?, ?>) a).get("name"))
                        .compareTo(String.valueOf(((Map<?, ?>) b).get("name"))));
            }
            return out;
        }
        return node;
    }

    /** O pretty printer usa o separador do sistema (CRLF no Windows); o repositório guarda LF. */
    private static String normalize(String text) {
        return text.replace("\r\n", "\n");
    }

    private static void assertGolden(String name, String actual) {
        Path file = Path.of("src/test/resources/golden", name);
        try {
            if (Boolean.getBoolean("golden.update")) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, normalize(actual), StandardCharsets.UTF_8);
                return;
            }
            if (!Files.exists(file)) {
                fail("Arquivo golden ausente: %s. Gere com -Dgolden.update=true no código ANTES da mudança.", file);
            }
            assertThat(normalize(actual))
                    .as("contrato mudou em %s", name)
                    .isEqualTo(normalize(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
