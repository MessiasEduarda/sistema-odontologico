package br.com.odontosaas.clinical;

import java.util.List;

/**
 * Modelos de termo de EXEMPLO que a clínica pode adicionar com um clique e depois editar. Trazem só a estrutura usual
 * de um termo (identificação, declaração de esclarecimento, autorização e assinatura); os riscos, cuidados e
 * alternativas de cada procedimento são preenchidos pela clínica. Devem ser revisados por assessoria jurídica e pelo
 * responsável técnico antes do uso.
 */
final class ConsentExamples {

    private ConsentExamples() {}

    record Example(String title, String category, String body) {}

    private static final String WARNING =
            "MODELO DE EXEMPLO — revise com assessoria jurídica e com o responsável" + " técnico antes de usar.\n\n";

    static final List<Example> ALL = List.of(
            new Example("Consentimento para tratamento odontológico (modelo de exemplo)", "DENTAL", WARNING + """
                            Eu, {{paciente}}, CPF {{cpf}}, declaro que fui informado(a) pelo(a) profissional \
                            {{profissional}} ({{cro}}) sobre o procedimento: {{procedimento}}, seus objetivos, \
                            alternativas, riscos e cuidados necessários, e que tive a oportunidade de esclarecer \
                            minhas dúvidas.

                            Declaro que as informações de saúde que prestei são verdadeiras e autorizo a realização \
                            do procedimento na {{clinica}}.

                            Data: {{data}}"""),
            new Example(
                    "Consentimento para procedimento de harmonização orofacial (modelo de exemplo)",
                    "AESTHETIC",
                    WARNING + """
                            Eu, {{paciente}}, CPF {{cpf}}, declaro que fui atendido(a) pelo(a) profissional \
                            {{profissional}} ({{cro}}), que me explicou de forma clara o procedimento de harmonização \
                            orofacial indicado: {{procedimento}}.

                            Declaro que:
                            1. Recebi explicações sobre a finalidade do procedimento, o produto que será utilizado, \
                            as alternativas de tratamento, os riscos, os possíveis efeitos adversos e os cuidados \
                            antes e depois da aplicação, e pude tirar todas as minhas dúvidas.
                            2. Entendo que o resultado varia de pessoa para pessoa, que não há garantia de resultado \
                            específico e que pode ser necessária nova avaliação, retoque ou manutenção.
                            3. Respondi à ficha de anamnese com informações verdadeiras e completas, inclusive sobre \
                            gestação, amamentação, alergias, doenças, medicamentos e procedimentos estéticos \
                            anteriores, e comunicarei qualquer mudança.
                            4. Recebi as orientações de cuidados e me comprometo a segui-las e a procurar a clínica \
                            em caso de qualquer sintoma inesperado.
                            5. Autorizo o registro de fotografias para documentação clínica no meu prontuário.

                            Autorizo a realização do procedimento na {{clinica}}.

                            Data: {{data}}"""),
            new Example("Autorização de uso de imagem (modelo de exemplo)", "IMAGE", WARNING + """
                            Eu, {{paciente}}, CPF {{cpf}}, autorizo a {{clinica}} a registrar fotografias e vídeos \
                            do meu rosto e/ou sorriso, antes, durante e após os procedimentos.

                            Marque abaixo, com o(a) profissional, as finalidades autorizadas:
                            ( ) Somente documentação clínica no prontuário (uso interno).
                            ( ) Apresentações e materiais científicos, sem identificação do meu nome.
                            ( ) Divulgação em redes sociais e materiais da clínica.

                            Sei que posso revogar esta autorização a qualquer momento, por escrito, sem prejuízo do \
                            meu atendimento; a revogação não alcança materiais já publicados antes do pedido.

                            Data: {{data}}"""),
            new Example(
                    "Consentimento para tratamento de dados pessoais — LGPD (modelo de exemplo)",
                    "PRIVACY",
                    WARNING + """
                            Eu, {{paciente}}, CPF {{cpf}}, fui informado(a) de que a {{clinica}} trata meus dados \
                            pessoais e dados de saúde para prestar o atendimento, manter o prontuário pelo prazo \
                            exigido pela legislação, agendar consultas, emitir documentos e cumprir obrigações legais.

                            Concordo também em receber comunicações sobre consultas e cuidados pelos canais que \
                            informei (e-mail, SMS ou WhatsApp), podendo alterar essa preferência a qualquer momento.

                            Sei que posso pedir acesso, correção, informações sobre compartilhamento e revogação deste \
                            consentimento, nos termos da Lei nº 13.709/2018 (LGPD), pelos canais de atendimento da \
                            clínica. Dados do prontuário não podem ser apagados antes do prazo legal de guarda.

                            Data: {{data}}"""));
}
