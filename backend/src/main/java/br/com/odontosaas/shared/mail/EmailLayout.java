package br.com.odontosaas.shared.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monta a versão HTML de um e-mail a partir do texto simples, para que todos os módulos continuem escrevendo só o
 * texto. Links viram botões, linhas com "- " viram lista, o protocolo do chamado fica em destaque e o que vem
 * depois da linha "--" vai para o rodapé.
 */
public final class EmailLayout {

    public static final String LOGO_ID = "odonto-logo";

    private static final String PAGE = "#F4EDE6";
    private static final String HEADER = "#7A5C41";
    private static final String TEXT = "#1E1C1A";
    private static final String MUTED = "#8D867F";
    private static final String LINE = "#E8E3DD";
    private static final String ACCENT = "#7A5C41";
    private static final String SOFT = "#F4EDE6";
    private static final String BUTTON = "#141414";
    private static final String FONT = "Helvetica, Arial, sans-serif";

    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern PROTOCOL = Pattern.compile("SUP-\\d{4}-\\d{5}");
    private static final Pattern CODE = Pattern.compile("(?i)^(.*c[óo]digo.*?:)\\s*(\\d{4,8})$");

    private EmailLayout() {}

    public static String html(String subject, String text) {
        String normalized = text.replace("\r\n", "\n").strip();
        String body = normalized;
        String footer = "";
        int separator = normalized.indexOf("\n--\n");
        if (separator >= 0) {
            body = normalized.substring(0, separator).strip();
            footer = normalized.substring(separator + 4).strip();
        }

        StringBuilder content = new StringBuilder();
        for (String block : body.split("\\n\\s*\\n")) {
            content.append(block(block.strip()));
        }

        String footerHtml = (footer.isEmpty() ? "" : inline(footer).replace("\n", "<br>") + "<br><br>")
                + "Este é um e-mail automático do Odonto SaaS. Por favor, não responda a esta mensagem.";

        return """
                <!doctype html>
                <html lang="pt-BR">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="color-scheme" content="light">
                <title>%1$s</title>
                </head>
                <body style="margin:0;padding:0;background:%2$s;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%3$s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%2$s;">
                <tr><td align="center" style="padding:32px 16px;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" \
                style="max-width:560px;background:#FFFFFF;border-radius:16px;overflow:hidden;border:1px solid %4$s;">
                <tr><td align="center" style="background:%5$s;padding:24px 24px 18px;">
                <img src="cid:%6$s" width="104" alt="Odonto SaaS" style="display:block;width:104px;height:auto;border:0;">
                </td></tr>
                <tr><td style="padding:32px 36px 8px;font-family:%7$s;">
                <h1 style="margin:0 0 20px;font-size:20px;line-height:1.35;color:%8$s;font-weight:700;">%1$s</h1>
                %9$s
                </td></tr>
                <tr><td style="padding:8px 36px 28px;font-family:%7$s;">
                <div style="border-top:1px solid %4$s;padding-top:18px;font-size:12px;line-height:1.6;color:%10$s;">%11$s</div>
                </td></tr>
                </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(
                        escape(subject),
                        PAGE,
                        escape(preview(body)),
                        LINE,
                        HEADER,
                        LOGO_ID,
                        FONT,
                        TEXT,
                        content,
                        MUTED,
                        footerHtml);
    }

    private static String block(String block) {
        StringBuilder out = new StringBuilder();
        List<String> paragraph = new ArrayList<>();
        List<String> items = new ArrayList<>();
        for (String line : block.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("- ")) {
                out.append(paragraph(paragraph));
                items.add(trimmed.substring(2));
                continue;
            }
            out.append(list(items));
            Matcher code = CODE.matcher(trimmed);
            Matcher url = URL.matcher(trimmed);
            if (code.matches()) {
                paragraph.add(code.group(1));
                out.append(paragraph(paragraph)).append(code(code.group(2)));
            } else if (url.find()) {
                String before = trimmed.substring(0, url.start()).strip();
                String after = trimmed.substring(url.end()).strip();
                if (!before.isEmpty()) paragraph.add(before);
                out.append(paragraph(paragraph)).append(button(url.group()));
                if (!after.isEmpty()) paragraph.add(after);
            } else {
                paragraph.add(trimmed);
            }
        }
        out.append(paragraph(paragraph)).append(list(items));
        return out.toString();
    }

    private static String paragraph(List<String> lines) {
        if (lines.isEmpty()) return "";
        String html =
                String.join("<br>", lines.stream().map(EmailLayout::inline).toList());
        lines.clear();
        return "<p style=\"margin:0 0 16px;font-size:15px;line-height:1.6;color:%s;\">%s</p>".formatted(TEXT, html);
    }

    private static String list(List<String> items) {
        if (items.isEmpty()) return "";
        StringBuilder html = new StringBuilder(
                "<ul style=\"margin:0 0 16px;padding-left:20px;font-size:15px;line-height:1.6;color:%s;\">"
                        .formatted(TEXT));
        for (String item : items) {
            html.append("<li style=\"margin:0 0 4px;\">").append(inline(item)).append("</li>");
        }
        items.clear();
        return html.append("</ul>").toString();
    }

    private static String button(String url) {
        String href = escape(url);
        return """
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin:8px 0 12px;"><tr>\
                <td style="background:%1$s;border-radius:999px;">\
                <a href="%2$s" style="display:inline-block;padding:13px 28px;font-family:%3$s;font-size:14px;\
                font-weight:700;letter-spacing:0.06em;text-transform:uppercase;color:#FFFFFF;text-decoration:none;">%4$s</a>\
                </td></tr></table>\
                <p style="margin:0 0 20px;font-size:12px;line-height:1.5;color:%5$s;word-break:break-all;">\
                Se o botão não abrir, copie e cole no navegador:<br><a href="%2$s" style="color:%6$s;">%2$s</a></p>
                """.formatted(BUTTON, href, FONT, buttonLabel(url), MUTED, ACCENT);
    }

    private static String code(String value) {
        return ("<p style=\"margin:4px 0 20px;\"><span style=\"display:inline-block;padding:12px 22px;"
                        + "background:%s;border-radius:12px;font-size:28px;font-weight:700;letter-spacing:0.3em;"
                        + "color:%s;\">%s</span></p>")
                .formatted(SOFT, TEXT, escape(value));
    }

    static String buttonLabel(String url) {
        String path = url.replaceFirst("^https?://[^/]+", "");
        if (path.startsWith("/suporte/") || path.startsWith("/plataforma/suporte/")) return "Ver chamado";
        if (path.startsWith("/redefinir-senha") || path.startsWith("/portal/redefinir-senha"))
            return "Criar nova senha";
        if (path.startsWith("/convite")) return "Aceitar convite";
        if (path.startsWith("/portal/ativar")) return "Criar minha senha";
        if (path.startsWith("/portal/entrar")) return "Entrar no portal";
        if (path.startsWith("/consulta")) return "Ver minha consulta";
        if (path.startsWith("/termo")) return "Ver e assinar o termo";
        return "Abrir";
    }

    private static String inline(String text) {
        String html = escape(text);
        return PROTOCOL.matcher(html)
                .replaceAll("<strong style=\"color:" + ACCENT + ";white-space:nowrap;\">$0</strong>");
    }

    private static String preview(String body) {
        String firstLines =
                URL.matcher(body).replaceAll("").replaceAll("\\s+", " ").strip();
        return firstLines.length() > 140 ? firstLines.substring(0, 140) : firstLines;
    }

    static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
