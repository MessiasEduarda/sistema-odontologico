const KEY = "odonto.support.report";

export interface ProblemReport {
  traceId?: string | null;
  message: string;
  page: string;
}

export const REPORT_PATH = "/suporte?relatar=1";

export function saveProblemReport(report: ProblemReport) {
  try {
    window.sessionStorage.setItem(KEY, JSON.stringify(report));
  } catch {}
}

export function takeProblemReport(): ProblemReport | null {
  try {
    const raw = window.sessionStorage.getItem(KEY);
    window.sessionStorage.removeItem(KEY);
    return raw ? (JSON.parse(raw) as ProblemReport) : null;
  } catch {
    return null;
  }
}
