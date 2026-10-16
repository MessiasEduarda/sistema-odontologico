package br.com.odontosaas.clinical;

import br.com.odontosaas.clinical.ClinicalDtos.Question;
import java.util.List;

/**
 * Fichas de anamnese iniciais (perguntas usuais de triagem): odontológica e de harmonização orofacial. São apenas um
 * ponto de partida: cada clínica edita as suas, e o conteúdo deve ser revisado pelo profissional responsável. O
 * sistema não faz diagnóstico — apenas destaca respostas que pedem atenção.
 */
final class DefaultAnamnesis {

    private DefaultAnamnesis() {}

    private static final String HEALTH = "Saúde geral";
    private static final String HABITS = "Hábitos";
    private static final String ORAL = "Saúde bucal";
    private static final String FACE = "Harmonização orofacial";

    static final String DENTAL_NAME = "Anamnese odontológica";
    static final String AESTHETIC_NAME = "Anamnese de harmonização orofacial";

    static final List<Question> QUESTIONS = List.of(
            new Question("chief_complaint", ORAL, "Qual o motivo da consulta / queixa principal?", "TEXT", false),
            new Question("medical_treatment", HEALTH, "Está em tratamento médico atualmente?", "YES_NO_TEXT", true),
            new Question("medications", HEALTH, "Faz uso de algum medicamento? Quais?", "YES_NO_TEXT", true),
            new Question(
                    "allergies",
                    HEALTH,
                    "Tem alergia a algum medicamento, anestésico, látex ou outro material?",
                    "YES_NO_TEXT",
                    true),
            new Question("anesthesia_reaction", HEALTH, "Já teve reação a anestesia local?", "YES_NO_TEXT", true),
            new Question("hypertension", HEALTH, "Tem pressão alta (hipertensão)?", "YES_NO", true),
            new Question("diabetes", HEALTH, "Tem diabetes?", "YES_NO", true),
            new Question("heart_disease", HEALTH, "Tem ou teve problema cardíaco?", "YES_NO_TEXT", true),
            new Question(
                    "anticoagulants",
                    HEALTH,
                    "Usa anticoagulante ou antiagregante (ex.: AAS, varfarina, clopidogrel)?",
                    "YES_NO_TEXT",
                    true),
            new Question(
                    "bleeding_disorder",
                    HEALTH,
                    "Tem sangramento excessivo ou demora na cicatrização?",
                    "YES_NO",
                    true),
            new Question(
                    "infectious_disease",
                    HEALTH,
                    "Tem ou teve hepatite, HIV, tuberculose ou outra doença infecciosa?",
                    "YES_NO_TEXT",
                    true),
            new Question("respiratory", HEALTH, "Tem asma, bronquite ou outro problema respiratório?", "YES_NO", true),
            new Question("seizures", HEALTH, "Tem epilepsia ou já teve convulsões?", "YES_NO", true),
            new Question("bisphosphonates", HEALTH, "Usa ou usou bisfosfonatos (tratamento ósseo)?", "YES_NO", true),
            new Question("pregnancy", HEALTH, "Está grávida ou amamentando?", "YES_NO_TEXT", true),
            new Question("surgeries", HEALTH, "Já fez alguma cirurgia? Qual?", "YES_NO_TEXT", false),
            new Question(
                    "other_conditions", HEALTH, "Tem alguma outra condição de saúde importante?", "YES_NO_TEXT", true),
            new Question("smoking", HABITS, "Fuma?", "YES_NO_TEXT", false),
            new Question("alcohol", HABITS, "Consome bebida alcoólica com frequência?", "YES_NO", false),
            new Question("bruxism", HABITS, "Range ou aperta os dentes (inclusive dormindo)?", "YES_NO", false),
            new Question("tooth_pain", ORAL, "Sente dor em algum dente?", "YES_NO_TEXT", false),
            new Question("gum_bleeding", ORAL, "A gengiva sangra ao escovar?", "YES_NO", false),
            new Question("sensitivity", ORAL, "Tem sensibilidade ao frio ou ao doce?", "YES_NO", false),
            new Question("flossing", ORAL, "Usa fio dental diariamente?", "YES_NO", false),
            new Question("last_visit", ORAL, "Quando foi a última consulta odontológica?", "TEXT", false));

    static final List<Question> AESTHETIC = List.of(
            new Question("main_goal", FACE, "O que você deseja melhorar ou o que mais incomoda?", "TEXT", false),
            new Question(
                    "previous_procedures",
                    FACE,
                    "Já fez procedimentos estéticos na face (toxina, preenchimento, fios, bioestimulador)? Quais e"
                            + " quando?",
                    "YES_NO_TEXT",
                    true),
            new Question(
                    "permanent_filler",
                    FACE,
                    "Tem preenchimento definitivo (ex.: PMMA, silicone) em alguma região? Qual?",
                    "YES_NO_TEXT",
                    true),
            new Question(
                    "previous_complication",
                    FACE,
                    "Já teve reação ou complicação após procedimento estético?",
                    "YES_NO_TEXT",
                    true),
            new Question(
                    "skin_condition",
                    FACE,
                    "Tem inflamação, infecção, acne ativa ou lesão de pele na região a tratar?",
                    "YES_NO_TEXT",
                    true),
            new Question("herpes", FACE, "Tem herpes labial recorrente?", "YES_NO", true),
            new Question("keloid", FACE, "Tem tendência a queloide ou cicatrização alterada?", "YES_NO", true),
            new Question("sun_exposure", FACE, "Tem exposição frequente ao sol?", "YES_NO", false),
            new Question(
                    "allergies",
                    HEALTH,
                    "Tem alergia a medicamento, anestésico, ácido hialurônico, albumina, ovo ou outro componente?",
                    "YES_NO_TEXT",
                    true),
            new Question(
                    "pregnancy", HEALTH, "Está grávida, amamentando ou planejando engravidar?", "YES_NO_TEXT", true),
            new Question(
                    "neuromuscular",
                    HEALTH,
                    "Tem doença neuromuscular ou neurológica (ex.: miastenia gravis)?",
                    "YES_NO_TEXT",
                    true),
            new Question("autoimmune", HEALTH, "Tem doença autoimune ou usa imunossupressor?", "YES_NO_TEXT", true),
            new Question(
                    "anticoagulants",
                    HEALTH,
                    "Usa anticoagulante, antiagregante ou suplementos que aumentam sangramento?",
                    "YES_NO_TEXT",
                    true),
            new Question(
                    "medications",
                    HEALTH,
                    "Usa medicamento contínuo (incluindo antibiótico, anti-inflamatório ou isotretinoína)? Quais?",
                    "YES_NO_TEXT",
                    true),
            new Question(
                    "chronic_disease",
                    HEALTH,
                    "Tem diabetes, pressão alta, doença cardíaca ou outra doença crônica?",
                    "YES_NO_TEXT",
                    true),
            new Question(
                    "recent_events",
                    HEALTH,
                    "Fez cirurgia, vacina ou tratamento odontológico recentemente, ou tem algum agendado?",
                    "YES_NO_TEXT",
                    false),
            new Question("smoking", HABITS, "Fuma?", "YES_NO_TEXT", false),
            new Question("alcohol", HABITS, "Consome bebida alcoólica com frequência?", "YES_NO", false));
}
