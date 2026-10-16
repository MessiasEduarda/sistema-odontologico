"use client";

import Link from "next/link";
import styled from "styled-components";
import { media } from "@/styles/theme";

interface Section {
  title: string;
  paragraphs: string[];
}

const UPDATED_AT = "15/10/2026";

const NOTICE =
  "Modelo de documento elaborado para este sistema. Antes de usar com clientes reais, revise com assessoria jurídica" +
  " e complete os dados da empresa responsável pela plataforma (razão social, CNPJ, endereço e canal de contato).";

const TERMS: Section[] = [
  {
    title: "1. Sobre a plataforma",
    paragraphs: [
      "O Odonto SaaS é um sistema on-line de gestão para clínicas odontológicas e de harmonização orofacial: agenda, cadastro de pacientes, prontuário, anamnese, termos de consentimento, orientações ao paciente, orçamentos, financeiro, estoque, comunicação e relatórios.",
      "Estes termos regulam o uso da plataforma pela clínica contratante (cliente) e pelas pessoas que ela cadastra como usuárias (equipe).",
    ],
  },
  {
    title: "2. Cadastro e acesso",
    paragraphs: [
      "O acesso é pessoal e intransferível. Cada usuário é responsável por manter sua senha em sigilo e por todas as ações realizadas com sua conta.",
      "A clínica define os papéis e permissões de cada pessoa da equipe e deve remover o acesso de quem deixar de trabalhar com ela.",
      "Todas as ações relevantes ficam registradas em trilha de auditoria (quem fez, o quê e quando).",
    ],
  },
  {
    title: "3. Responsabilidades da clínica",
    paragraphs: [
      "Inserir informações verdadeiras e manter os dados de pacientes atualizados.",
      "Obter dos pacientes as autorizações necessárias (termos de consentimento, uso de imagem, comunicação) e respeitar a legislação aplicável, incluindo a Lei Geral de Proteção de Dados (Lei nº 13.709/2018), o Código de Ética Odontológica e as normas do Conselho Federal e Regional de Odontologia e da ANVISA.",
      "Revisar e validar, com seu responsável técnico e assessoria jurídica, o conteúdo de fichas de anamnese, termos, orientações e documentos emitidos. Modelos oferecidos pelo sistema são apenas pontos de partida.",
      "As decisões clínicas são sempre do profissional. O sistema organiza informações e destaca alertas, mas não faz diagnóstico nem indica tratamento.",
    ],
  },
  {
    title: "4. Uso permitido",
    paragraphs: [
      "É proibido usar a plataforma para fins ilícitos, tentar acessar dados de outras clínicas, contornar mecanismos de segurança, sobrecarregar o serviço ou copiar o software.",
      "Contas usadas em desacordo com estes termos podem ser suspensas, com aviso prévio sempre que possível.",
    ],
  },
  {
    title: "5. Dados e prontuário",
    paragraphs: [
      "Os dados inseridos pela clínica pertencem à clínica. A plataforma os trata somente para prestar o serviço, conforme a Política de Privacidade.",
      "Registros clínicos (anamneses, evoluções, termos assinados, aplicações) não são apagados: correções são feitas por novo registro ou anulação com motivo, preservando o histórico, em linha com o dever de guarda do prontuário.",
      "Ao encerrar o contrato, a clínica pode solicitar a exportação dos seus dados em formato legível.",
    ],
  },
  {
    title: "6. Disponibilidade e suporte",
    paragraphs: [
      "Buscamos manter o serviço disponível de forma contínua, mas podem ocorrer interrupções para manutenção ou por fatores externos. Manutenções programadas serão comunicadas com antecedência quando possível.",
      "São feitas cópias de segurança periódicas para permitir a recuperação dos dados em caso de falha.",
    ],
  },
  {
    title: "7. Planos, pagamento e cancelamento",
    paragraphs: [
      "Os recursos e limites de cada plano são informados na contratação e na tela de Configurações da clínica.",
      "A falta de pagamento pode levar à suspensão do acesso, sem exclusão de dados. A clínica pode cancelar a qualquer momento, observadas as condições do plano contratado.",
    ],
  },
  {
    title: "8. Alterações destes termos",
    paragraphs: [
      "Estes termos podem ser atualizados. Mudanças relevantes serão informadas na plataforma com antecedência razoável.",
    ],
  },
];

const PRIVACY: Section[] = [
  {
    title: "1. Quem trata os dados",
    paragraphs: [
      "Dados de pacientes: a clínica é a controladora (decide para que os dados são usados). A plataforma atua como operadora, tratando os dados apenas para executar o serviço contratado e conforme as instruções da clínica.",
      "Dados da equipe e da clínica contratante (nome, e-mail, acessos): a plataforma é controladora, para fins de cadastro, segurança, faturamento e suporte.",
    ],
  },
  {
    title: "2. Quais dados são tratados",
    paragraphs: [
      "Pacientes: identificação e contato, dados de saúde (anamnese, prontuário, odontograma, procedimentos, fotos e exames anexados), termos assinados, agendamentos e informações financeiras dos atendimentos.",
      "Equipe: nome, e-mail, papéis, registros de acesso (data, IP e navegador) e trilha de auditoria.",
      "Pacientes no portal e nos links: endereço IP e navegador no momento da assinatura de termos e das confirmações, como prova do aceite.",
    ],
  },
  {
    title: "3. Para que usamos",
    paragraphs: [
      "Prestar o atendimento e manter o prontuário (tutela da saúde e obrigação legal); agendar e lembrar consultas; emitir documentos, orçamentos e cobranças; enviar comunicações autorizadas pelo paciente; garantir a segurança e prevenir fraudes.",
      "Os dados não são vendidos nem usados para publicidade.",
    ],
  },
  {
    title: "4. Compartilhamento",
    paragraphs: [
      "Somente com prestadores necessários ao funcionamento (hospedagem, envio de e-mail), sob contrato e com dever de confidencialidade, ou quando exigido por lei ou autoridade competente.",
    ],
  },
  {
    title: "5. Segurança",
    paragraphs: [
      "Acesso por senha forte com bloqueio após tentativas erradas, sessões que expiram, permissões por papel, isolamento dos dados de cada clínica no banco de dados, links de paciente com código secreto e validade, trilha de auditoria e registros clínicos imutáveis.",
    ],
  },
  {
    title: "6. Por quanto tempo guardamos",
    paragraphs: [
      "O prontuário é mantido pelo prazo exigido pela legislação e pelas normas profissionais. Demais dados, enquanto durar o contrato e pelos prazos legais de guarda (por exemplo, fiscais).",
    ],
  },
  {
    title: "7. Direitos dos titulares",
    paragraphs: [
      "O titular pode pedir confirmação de tratamento, acesso, correção, informação sobre compartilhamento, portabilidade e revogação de consentimentos. Pacientes devem fazer o pedido diretamente à clínica, que é a controladora; a plataforma apoia a clínica no atendimento.",
      "Dados de prontuário não podem ser eliminados antes do prazo legal de guarda.",
    ],
  },
  {
    title: "8. Cookies",
    paragraphs: [
      "Usamos apenas cookies essenciais: o de sessão (mantém você conectado) e o de proteção contra falsificação de requisições. Não usamos cookies de publicidade ou rastreamento.",
    ],
  },
  {
    title: "9. Contato",
    paragraphs: [
      "Dúvidas sobre privacidade: pelo canal de atendimento da plataforma ou, no caso de pacientes, diretamente com a clínica.",
    ],
  },
];

export function LegalPage({ kind }: { kind: "terms" | "privacy" }) {
  const sections = kind === "terms" ? TERMS : PRIVACY;
  return (
    <Page>
      <Header>
        <Brand href="/entrar">Odonto SaaS</Brand>
        <Nav>
          <Link href="/termos-de-uso" aria-current={kind === "terms" ? "page" : undefined}>
            Termos de uso
          </Link>
          <Link href="/privacidade" aria-current={kind === "privacy" ? "page" : undefined}>
            Privacidade
          </Link>
        </Nav>
      </Header>
      <Main>
        <h1>{kind === "terms" ? "Termos de uso" : "Política de privacidade"}</h1>
        <Updated>Última atualização: {UPDATED_AT}</Updated>
        <Notice role="note">{NOTICE}</Notice>
        {sections.map((s) => (
          <section key={s.title}>
            <h2>{s.title}</h2>
            {s.paragraphs.map((p) => (
              <p key={p}>{p}</p>
            ))}
          </section>
        ))}
      </Main>
    </Page>
  );
}

const Page = styled.div`
  min-height: 100vh;
  background: ${({ theme }) => theme.colors.background};
`;

const Header = styled.header`
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[3]};
  background: ${({ theme }) => theme.colors.sidebar};
  padding: ${({ theme }) => `${theme.space[4]} ${theme.space[4]}`};

  ${media.md} {
    padding: ${({ theme }) => `${theme.space[5]} ${theme.space[8]}`};
  }
`;

const Brand = styled(Link)`
  color: ${({ theme }) => theme.colors.sidebarText};
  font-family: ${({ theme }) => theme.fonts.display};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  font-size: ${({ theme }) => theme.fontSizes.xl};
  text-decoration: none;
`;

const Nav = styled.nav`
  display: flex;
  gap: ${({ theme }) => theme.space[5]};

  a {
    color: ${({ theme }) => theme.colors.sidebarTextMuted};
    text-decoration: none;
  }
  a[aria-current="page"] {
    color: ${({ theme }) => theme.colors.sidebarText};
    text-decoration: underline;
    text-underline-offset: 4px;
  }
`;

const Main = styled.main`
  max-width: 820px;
  margin: 0 auto;
  padding: ${({ theme }) => `${theme.space[8]} ${theme.space[4]}`};
  line-height: 1.65;

  h1 {
    font-family: ${({ theme }) => theme.fonts.display};
    font-size: ${({ theme }) => theme.fontSizes["2xl"]};
    color: ${({ theme }) => theme.colors.primaryText};
  }
  h2 {
    font-size: ${({ theme }) => theme.fontSizes.lg};
    margin: ${({ theme }) => `${theme.space[6]} 0 ${theme.space[2]}`};
  }
  p {
    margin-bottom: ${({ theme }) => theme.space[3]};
  }
`;

const Updated = styled.p`
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.sm};
`;

const Notice = styled.p`
  background: ${({ theme }) => theme.colors.warningSoft};
  border: 1px solid ${({ theme }) => theme.colors.warningBorder};
  color: ${({ theme }) => theme.colors.warning};
  border-radius: ${({ theme }) => theme.radii.md};
  padding: ${({ theme }) => theme.space[4]};
`;
