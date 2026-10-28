import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { PlatformTicketPage } from "@/features/support/PlatformTicketPage";

export const metadata: Metadata = { title: "Chamado de suporte" };

export default function Page({ params }: PageProps<"/plataforma/suporte/[id]">) {
  return (
    <Suspense fallback={<LoadingState />}>
      <TicketRoute params={params} />
    </Suspense>
  );
}

async function TicketRoute({ params }: { params: PageProps<"/plataforma/suporte/[id]">["params"] }) {
  const { id } = await params;
  return <PlatformTicketPage ticketId={id} />;
}
