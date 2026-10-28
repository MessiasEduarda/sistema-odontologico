import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { TicketPage } from "@/features/support/TicketPage";

export const metadata: Metadata = { title: "Chamado" };

export default function Page({ params }: PageProps<"/suporte/[id]">) {
  return (
    <Suspense fallback={<LoadingState />}>
      <TicketRoute params={params} />
    </Suspense>
  );
}

async function TicketRoute({ params }: { params: PageProps<"/suporte/[id]">["params"] }) {
  const { id } = await params;
  return <TicketPage ticketId={id} />;
}
